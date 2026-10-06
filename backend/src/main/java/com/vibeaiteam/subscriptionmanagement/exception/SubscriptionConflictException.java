package com.vibeaiteam.subscriptionmanagement.exception;

/** 청약 충돌 예외(HTTP 409). */
public class SubscriptionConflictException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SubscriptionConflictException(String message) {
    super(message);
  }
}
