package com.vibeaiteam.accountauthentication.config.ratelimit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** 공개 조회와 로그인·가입 요청에 IP별 분당 요청 한도를 적용하는 필터. */
public class RateLimitFilter extends OncePerRequestFilter {

  /** 공개 조회 그룹(GET /ipos, /market-holidays). */
  private static final String GROUP_PUBLIC_READ = "public-read";

  /** 로그인·가입 그룹(POST /auth/login, POST /accounts). */
  private static final String GROUP_AUTH = "auth";

  /** 한도를 넘긴 요청에 돌려줄 에러 코드. */
  private static final String ERROR_CODE = "RATE_LIMITED";

  /** 한도를 넘긴 요청에 돌려줄 안내 문구. */
  private static final String ERROR_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";

  /** 포워딩 헤더의 첫 값이 IPv6처럼 보이는지 확인하는 패턴(콜론만 있는 경우). */
  private static final Pattern IPV6_LIKE = Pattern.compile("[0-9a-fA-F:.]+");

  /** 신뢰 여부가 켜져 있을 때만 읽는 클라이언트 IP 헤더. */
  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private final RateLimitProperties properties;

  private final FixedWindowRateLimiter limiter;

  private final ObjectMapper objectMapper;

  /** 설정 값, 제한기, JSON 직렬화기로 필터를 만든다. */
  public RateLimitFilter(
      RateLimitProperties properties, FixedWindowRateLimiter limiter, ObjectMapper objectMapper) {
    this.properties = properties;
    this.limiter = limiter;
    this.objectMapper = objectMapper;
  }

  /** 제한 대상이 아닌 요청은 그대로 통과시키고, 한도를 넘긴 요청에는 429 응답을 쓴다. */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String group = resolveGroup(request);
    if (group == null) {
      filterChain.doFilter(request, response);
      return;
    }
    String key = clientIp(request) + '|' + group;
    FixedWindowRateLimiter.Decision decision = limiter.tryAcquire(key, limitFor(group));
    if (decision.allowed()) {
      filterChain.doFilter(request, response);
      return;
    }
    reject(response, decision);
  }

  private String resolveGroup(HttpServletRequest request) {
    String path = requestPath(request);
    if (HttpMethod.GET.matches(request.getMethod())) {
      return isPublicReadPath(path) ? GROUP_PUBLIC_READ : null;
    }
    if (HttpMethod.POST.matches(request.getMethod()) && isAuthPath(path)) {
      return GROUP_AUTH;
    }
    return null;
  }

  private static boolean isPublicReadPath(String path) {
    return path.equals("/ipos")
        || path.startsWith("/ipos/")
        || path.equals("/market-holidays")
        || path.startsWith("/market-holidays/");
  }

  private static boolean isAuthPath(String path) {
    return path.equals("/auth/login") || path.equals("/accounts");
  }

  private static String requestPath(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
      return requestUri.substring(contextPath.length());
    }
    return requestUri;
  }

  private int limitFor(String group) {
    if (GROUP_AUTH.equals(group)) {
      return properties.getAuthPerMinute();
    }
    return properties.getPublicReadPerMinute();
  }

  private String clientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (!properties.isTrustForwardedHeader()) {
      return remoteAddr;
    }
    String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwarded == null || forwarded.isBlank()) {
      return remoteAddr;
    }
    String firstValue = forwarded.split(",", 2)[0].trim();
    return isIpLiteral(firstValue) ? firstValue : remoteAddr;
  }

  private static boolean isIpLiteral(String value) {
    if (value.isEmpty() || value.length() > 45) {
      return false;
    }
    if (value.indexOf(':') >= 0) {
      return IPV6_LIKE.matcher(value).matches();
    }
    String[] octets = value.split("\\.", -1);
    if (octets.length != 4) {
      return false;
    }
    for (String octet : octets) {
      if (octet.isEmpty()
          || octet.length() > 3
          || !octet.chars().allMatch(Character::isDigit)
          || Integer.parseInt(octet) > 255) {
        return false;
      }
    }
    return true;
  }

  private void reject(HttpServletResponse response, FixedWindowRateLimiter.Decision decision)
      throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
    response.setContentType(ErrorResponses.JSON_UTF8.toString());
    response.getWriter().write(errorBody());
  }

  private String errorBody() {
    ErrorResponse body = ErrorResponse.builder().error(ERROR_CODE).message(ERROR_MESSAGE).build();
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("제한 응답 본문을 만들 수 없습니다", e);
    }
  }
}
