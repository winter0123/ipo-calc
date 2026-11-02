package com.ipocalc.accountauthentication.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Origin/Referer 검증 필터.
 *
 * <p>Spring Security의 CsrfFilter 뒤에서 실행되어, 쓰기 요청의 Origin(없으면 Referer)이 신뢰 Origin과 정확히 일치하는지 확인한다.
 * Origin·Referer가 둘 다 없으면 CSRF 토큰 검증(CsrfFilter)만 신뢰한다. Origin 헤더가 문자열 {@code null}이면 거부한다.
 */
public class CsrfTokenFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final boolean csrfEnabled;
  private final String trustedOrigin;

  /** CSRF 토큰 검증 뒤 Origin 검사에 필요한 설정을 저장한다. */
  public CsrfTokenFilter(boolean csrfEnabled, String trustedOrigin) {
    this.csrfEnabled = csrfEnabled;
    this.trustedOrigin = normalizeOrigin(trustedOrigin, true);
  }

  /**
   * 쓰기 요청의 Origin 또는 Referer를 검증한다.
   *
   * @param request HTTP 요청
   * @param response HTTP 응답
   * @param filterChain 다음 필터 체인
   * @throws ServletException 서블릿 처리 오류
   * @throws IOException 입출력 오류
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!csrfEnabled || SAFE_METHODS.contains(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    String origin = request.getHeader("Origin");
    String referer = request.getHeader("Referer");

    if (origin != null) {
      if (!isTrustedOrigin(origin, true)) {
        reject(response, "Invalid Origin");
        return;
      }
    } else if (referer != null && !isTrustedOrigin(referer, false)) {
      reject(response, "Invalid Referer");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isTrustedOrigin(String value, boolean originHeader) {
    if (trustedOrigin == null || "null".equals(value)) {
      return false;
    }
    String candidate = normalizeOrigin(value, originHeader);
    return candidate != null && candidate.equals(trustedOrigin);
  }

  private static String normalizeOrigin(String value, boolean requireOriginOnly) {
    if (value == null || value.isBlank() || "null".equals(value)) {
      return null;
    }
    try {
      URI uri = new URI(value.trim());
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (scheme == null
          || host == null
          || uri.isOpaque()
          || uri.getRawUserInfo() != null
          || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          || uri.getRawFragment() != null
          || (requireOriginOnly
              && ((uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                  || uri.getRawQuery() != null))) {
        return null;
      }
      int port = uri.getPort();
      boolean defaultPort =
          port == -1
              || ("http".equalsIgnoreCase(scheme) && port == 80)
              || ("https".equalsIgnoreCase(scheme) && port == 443);
      return scheme.toLowerCase() + "://" + host.toLowerCase() + (defaultPort ? "" : ":" + port);
    } catch (Exception e) {
      return null;
    }
  }

  private static void reject(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"" + message + "\"}");
  }
}
