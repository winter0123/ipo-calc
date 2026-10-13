package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 배정 충돌 예외(HTTP 409). */
public class AllocationConflictException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public AllocationConflictException(String message) {
    super(HttpStatus.CONFLICT, "DUPLICATE_ALLOCATION", message);
  }
}
