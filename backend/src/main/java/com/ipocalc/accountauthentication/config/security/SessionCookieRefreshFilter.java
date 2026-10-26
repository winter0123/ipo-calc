package com.ipocalc.accountauthentication.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 쿠키 슬라이딩. Spring Session은 세션 ID가 바뀔 때만 쿠키를 다시 쓰므로, 그냥 두면 쿠키 Max-Age(30일)는 로그인 시점부터만 세어져 계속 쓰는
 * 사용자도 30일 뒤 쿠키가 사라진다. 요청의 세션이 유효하고 ID가 그대로면 응답이 확정되기 직전에 쿠키를 다시 써서 만료를 뒤로 민다. ID가 바뀐 경우(로그인)는
 * Spring Session이 쓰고, 세션이 없어진 경우(로그아웃)는 쓰지 않는다.
 */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER + 1)
public class SessionCookieRefreshFilter extends OncePerRequestFilter {

  private final CookieSerializer cookieSerializer;

  /** 쿠키 직렬화기(SessionCookieConfig)를 주입받는다. */
  public SessionCookieRefreshFilter(CookieSerializer cookieSerializer) {
    this.cookieSerializer = cookieSerializer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(
        request,
        new OnCommittedResponseWrapper(response) {
          @Override
          protected void onResponseCommitted() {
            HttpSession session = request.getSession(false);
            if (session != null
                && request.isRequestedSessionIdValid()
                && session.getId().equals(request.getRequestedSessionId())) {
              cookieSerializer.writeCookieValue(
                  new CookieValue(request, response, session.getId()));
            }
            disableOnResponseCommitted();
          }
        });
  }
}
