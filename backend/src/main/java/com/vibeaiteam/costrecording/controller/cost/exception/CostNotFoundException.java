package com.vibeaiteam.costrecording.controller.cost.exception;

/**
 * 비용 대상 없음 예외(HTTP 404).
 */
public class CostNotFoundException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public CostNotFoundException(String message) {
    super(message);
  }
}
