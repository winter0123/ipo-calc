package com.ipocalc.accountauthentication.controller;

import com.ipocalc.accountauthentication.controller.dto.ImpersonationStartResponse;
import com.ipocalc.accountauthentication.controller.dto.ImpersonationStatusResponse;
import com.ipocalc.accountauthentication.service.impersonation.ImpersonationService;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 임퍼소네이션 관리 API. */
@RestController
@RequestMapping("/admin/impersonation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminImpersonationController {

  private final ImpersonationService impersonationService;
  private final SecurityUtils securityUtils;

  /**
   * 임퍼소네이션 시작.
   *
   * @param accountId 대상 회원 계정 ID
   * @param request HTTP 요청
   * @return 임퍼소네이션 세션 정보
   */
  @PostMapping("/start/{accountId}")
  public ResponseEntity<ImpersonationStartResponse> start(
      @PathVariable Long accountId, HttpServletRequest request) {

    var session =
        impersonationService.startImpersonation(
            securityUtils.getCurrentAccountId(), accountId, request.getSession());

    return ResponseEntity.ok(
        ImpersonationStartResponse.builder()
            .sessionId(session.getSessionId())
            .expiresAt(session.getExpiresAt())
            .readOnly(session.getReadOnly())
            .build());
  }

  /** 임퍼소네이션 상태 조회. */
  @GetMapping("/status")
  public ResponseEntity<ImpersonationStatusResponse> status(HttpServletRequest request) {

    var status = impersonationService.getStatus(request);

    return ResponseEntity.ok(status);
  }

  /** 임퍼소네이션 종료. */
  @PostMapping("/end")
  public ResponseEntity<Void> end(HttpServletRequest request) {
    impersonationService.endCurrentImpersonation(securityUtils.getCurrentAccountId(), "MANUAL");
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(ImpersonationService.SESSION_ATTRIBUTE);
    }
    return ResponseEntity.noContent().build();
  }
}
