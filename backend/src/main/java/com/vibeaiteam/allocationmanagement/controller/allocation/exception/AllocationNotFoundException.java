package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 배정 대상 없음 예외(HTTP 404). */
public class AllocationNotFoundException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public AllocationNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
