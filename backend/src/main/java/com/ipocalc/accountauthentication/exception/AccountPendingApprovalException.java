package com.ipocalc.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/** 관리자 승인 전 계정이 로그인하려 할 때 던진다. */
public class AccountPendingApprovalException extends ApiException {

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 사용자에게 보여줄 메시지
   */
  public AccountPendingApprovalException(String message) {
    super(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING_APPROVAL", message);
  }
}
