package com.vibeaiteam.subscriptionmanagement.exception;

/** 청약 소유권 위반 예외(HTTP 403). */
public class SubscriptionForbiddenException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SubscriptionForbiddenException(String message) {
    super(message);
  }
}
