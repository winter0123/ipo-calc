package com.vibeaiteam.subscriptionmanagement.exception;

/**
 * 청약 검증 실패 예외(HTTP 400).
 */
public class SubscriptionValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SubscriptionValidationException(String message) {
    super(message);
  }
}
