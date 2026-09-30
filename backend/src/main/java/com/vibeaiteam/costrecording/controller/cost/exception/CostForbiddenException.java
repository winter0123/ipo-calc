package com.vibeaiteam.costrecording.controller.cost.exception;

/**
 * 비용 소유권 위반 예외(HTTP 403).
 */
public class CostForbiddenException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public CostForbiddenException(String message) {
    super(message);
  }
}
