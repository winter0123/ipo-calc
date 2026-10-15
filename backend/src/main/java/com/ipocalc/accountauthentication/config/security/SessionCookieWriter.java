package com.ipocalc.accountauthentication.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 세션 쿠키 삭제. 발급은 Spring Session이 한다(SessionCookieConfig). 속성은
 * {@code server.servlet.session.cookie.*}와 같은 값을 쓴다.
 */
@Component
public class SessionCookieWriter {

  private static final String NAME = "JSESSIONID";

  private final boolean secure;
  private final String sameSite;

  /** 컨테이너 세션 쿠키 설정과 동일한 Secure·SameSite 값을 주입받는다. */
  public SessionCookieWriter(
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
      @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
    this.secure = secure;
    this.sameSite =
        sameSite.isEmpty()
            ? sameSite
            : sameSite.substring(0, 1).toUpperCase() + sameSite.substring(1).toLowerCase();
  }

  /** 로그아웃 시 JSESSIONID를 즉시 만료시킨다. 컨테이너는 세션 무효화만 하고 쿠키는 지우지 않는다. */
  public void expire(HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from(NAME, "")
            .path("/")
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .maxAge(0)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
