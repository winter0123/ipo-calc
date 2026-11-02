package com.ipocalc.accountauthentication.config.security;

import com.ipocalc.accountauthentication.util.security.ImpersonationContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 임퍼소네이션 중 읽기 전용을 강제하는 인터셉터.
 *
 * <p>GET/HEAD/OPTIONS만 허용하고, 허용 목록에 있는 조회 API만 통과시킨다. 유일한 예외는 임퍼소네이션 종료 {@code POST
 * /admin/impersonation/end}다. 계좌번호·자격증명처럼 민감한 조회와 다른 관리자 API는 차단한다.
 */
@Component
public class ReadOnlyImpersonationInterceptor implements HandlerInterceptor {

  private static final String END_PATH = "/admin/impersonation/end";
  private static final Set<String> ALLOWED_READ_PATTERNS =
      Set.of(
          "/dashboard/**",
          "/ipos/**",
          "/market-holidays/**",
          "/subscriptions/**",
          "/allocations/**",
          "/sales/**",
          "/notifications/**",
          "/securities-accounts/**",
          "/securities-firms/**",
          "/investors/**",
          "/investment-history/**",
          "/auth/me",
          "/auth/csrf",
          "/admin/impersonation/status");

  /** 허용 경로 안에 있더라도 임퍼소네이션 중에는 차단하는 민감 조회. */
  private static final Set<String> DENIED_READ_PATTERNS =
      Set.of(
          "/securities-accounts/*/account-number",
          "/securities-accounts/*/api-credential/**",
          "/securities-accounts/*/broker-sync/**",
          "/admin/accounts/**",
          "/admin/users/**",
          "/actuator/**");

  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler)
      throws IOException {
    if (!ImpersonationContextHolder.isImpersonating()) {
      return true;
    }

    String method = request.getMethod();
    String path = request.getRequestURI();

    if ("POST".equals(method) && END_PATH.equals(path)) {
      return true;
    }

    if (!Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
      return forbid(response);
    }

    if (matchesAny(DENIED_READ_PATTERNS, path)) {
      return forbid(response);
    }

    if (!matchesAny(ALLOWED_READ_PATTERNS, path)) {
      return forbid(response);
    }

    return true;
  }

  private boolean matchesAny(Set<String> patterns, String path) {
    return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private boolean forbid(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"임퍼소네이션 모드에서는 조회만 가능합니다\"}");
    return false;
  }
}
