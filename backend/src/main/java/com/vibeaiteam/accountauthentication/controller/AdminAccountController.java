package com.vibeaiteam.accountauthentication.controller;

import com.vibeaiteam.accountauthentication.application.service.AdminAccountService;
import com.vibeaiteam.accountauthentication.controller.dto.AdminAccountResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 가입 승인 관리 API(ADMIN 전용). */
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

  private final AdminAccountService adminAccountService;

  /** 계정 목록을 돌려준다. {@code approved=false}면 승인 대기만, 생략하면 전체다. */
  @GetMapping
  public ResponseEntity<List<AdminAccountResponse>> list(
      @RequestParam(required = false) Boolean approved) {
    return ResponseEntity.ok(adminAccountService.list(approved));
  }

  /** 계정을 승인한다. */
  @PostMapping("/{accountId}/approve")
  public ResponseEntity<Void> approve(@PathVariable Long accountId) {
    adminAccountService.approve(accountId);
    return ResponseEntity.noContent().build();
  }

  /** 계정의 승인을 취소하고 로그인 세션을 끝낸다. */
  @PostMapping("/{accountId}/revoke")
  public ResponseEntity<Void> revoke(@PathVariable Long accountId) {
    adminAccountService.revoke(accountId);
    return ResponseEntity.noContent().build();
  }
}
