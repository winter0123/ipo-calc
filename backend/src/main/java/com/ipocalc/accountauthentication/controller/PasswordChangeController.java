package com.ipocalc.accountauthentication.controller;

import com.ipocalc.accountauthentication.application.service.PasswordChangeService;
import com.ipocalc.accountauthentication.controller.dto.PasswordChangeRequest;
import com.ipocalc.accountauthentication.controller.dto.SuccessResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 변경 API를 제공한다. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordChangeController {

  private final PasswordChangeService passwordChangeService;
  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /** 현재 로그인한 사용자의 비밀번호를 기존 비밀번호 검증과 함께 변경하고, 성공 메시지를 담은 200 OK 응답을 반환한다. */
  @PostMapping(value = "/password-change", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SuccessResponse> changePassword(
      @Valid @RequestBody PasswordChangeRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    passwordChangeService.changePassword(request, httpServletRequest, httpServletResponse);
    httpServletResponse.setContentType(JSON_UTF8.toString());
    return ResponseEntity.ok().contentType(JSON_UTF8).body(SuccessResponse.of("비밀번호가 변경되었습니다"));
  }
}
