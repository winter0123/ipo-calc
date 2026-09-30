package com.vibeaiteam.costrecording.controller.cost.exception;

/**
 * 비용 검증 실패 예외(HTTP 400).
 */
public class CostValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public CostValidationException(String message) {
    super(message);
  }
}
