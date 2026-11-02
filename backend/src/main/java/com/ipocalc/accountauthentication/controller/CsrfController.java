package com.ipocalc.accountauthentication.controller;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CSRF 토큰 부트스트랩 엔드포인트. 클라이언트는 인증된 상태에서 GET /auth/csrf를 호출해 원본 CSRF 토큰을 받아 헤더에 실어 보낸다. */
@RestController
@RequestMapping("/auth")
public class CsrfController {

  /** 현재 요청의 CSRF 토큰을 JSON으로 반환한다. 토큰은 CookieCsrfTokenRepository에 의해 쿠키에도 저장된다. */
  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "token", token.getToken(),
        "headerName", token.getHeaderName(),
        "parameterName", token.getParameterName());
  }
}
