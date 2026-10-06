package com.vibeaiteam.accountauthentication.controller;

import com.vibeaiteam.accountauthentication.application.service.AuthService;
import com.vibeaiteam.accountauthentication.controller.dto.CurrentAccountResponse;
import com.vibeaiteam.accountauthentication.controller.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인/로그아웃 인증 API를 제공한다. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /** 로그인 요청의 아이디/비밀번호를 검증해 인증 세션을 생성하고 200 OK를 반환한다. 검증에 실패하면 401을 반환한다. */
  @PostMapping("/login")
  public ResponseEntity<Void> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    authService.login(request, httpServletRequest, httpServletResponse);
    return ResponseEntity.ok().build();
  }

  /** 현재 로그인한 계정의 ID·이메일·이름·역할을 반환한다. 로그인하지 않았으면 401이다. */
  @GetMapping("/me")
  public ResponseEntity<CurrentAccountResponse> me() {
    return ResponseEntity.ok(authService.currentAccount());
  }

  /** 현재 요청의 인증 세션을 종료하고 200 OK를 반환한다. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    authService.logout(httpServletRequest, httpServletResponse);
    return ResponseEntity.ok().build();
  }
}
