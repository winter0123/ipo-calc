package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 배정 소유권 위반 예외(HTTP 403). */
public class AllocationForbiddenException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public AllocationForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
