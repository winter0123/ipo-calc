package com.vibeaiteam.subscriptionmanagement.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 청약 충돌 예외(HTTP 409). */
public class SubscriptionConflictException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SubscriptionConflictException(String message) {
    super(HttpStatus.CONFLICT, "DUPLICATE_SUBSCRIPTION", message);
  }
}
