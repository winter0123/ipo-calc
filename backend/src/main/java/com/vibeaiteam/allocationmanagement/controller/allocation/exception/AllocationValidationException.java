package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 배정 검증 실패 예외(HTTP 400). */
public class AllocationValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public AllocationValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
