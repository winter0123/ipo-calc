package com.ipocalc.accountauthentication.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 테스트 전용. 세션은 Spring Session(DB)이 쿠키로만 식별하는데, 기존 MockMvc 테스트는
 * {@code .session(MockHttpSession)}을 넘긴다. 이
 * 필터가 MockHttpSession에 JSESSIONID 쿠키 값을 보관했다가 다음 요청에 쿠키로 실어 주어 두 방식을 이어 준다(MockMvc 필터 체인에만
 * 들어간다).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MockSessionCookieBridge extends OncePerRequestFilter {

  /** 브리지가 MockHttpSession에 보관하는 쿠키 값의 속성 이름. */
  public static final String ATTR = "bridge.JSESSIONID";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpSession mock = request.getSession(false);
    HttpServletRequest forwarded = request;
    if (mock != null && mock.getAttribute(ATTR) instanceof String value) {
      List<Cookie> cookies = new ArrayList<>();
      if (request.getCookies() != null) {
        for (Cookie c : request.getCookies()) {
          if (!"JSESSIONID".equals(c.getName())) {
            cookies.add(c);
          }
        }
      }
      cookies.add(new Cookie("JSESSIONID", value));
      Cookie[] all = cookies.toArray(new Cookie[0]);
      forwarded =
          new HttpServletRequestWrapper(request) {
            @Override
            public Cookie[] getCookies() {
              return all;
            }
          };
    }
    try {
      chain.doFilter(forwarded, response);
    } finally {
      if (mock != null) {
        for (String header : response.getHeaders(HttpHeaders.SET_COOKIE)) {
          if (header.startsWith("JSESSIONID=")) {
            String value = header.substring("JSESSIONID=".length()).split(";", 2)[0];
            if (value.isEmpty()) {
              mock.removeAttribute(ATTR);
            } else {
              mock.setAttribute(ATTR, value);
            }
          }
        }
      }
    }
  }
}
