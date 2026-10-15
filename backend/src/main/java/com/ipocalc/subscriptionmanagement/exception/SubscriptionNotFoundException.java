package com.ipocalc.subscriptionmanagement.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 청약 대상 없음 예외(HTTP 404). */
public class SubscriptionNotFoundException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SubscriptionNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
