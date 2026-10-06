package com.vibeaiteam.subscriptionmanagement.exception;

/** 청약 대상 없음 예외(HTTP 404). */
public class SubscriptionNotFoundException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SubscriptionNotFoundException(String message) {
    super(message);
  }
}
