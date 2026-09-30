package com.vibeaiteam.salemanagement.controller.sale.exception;

/**
 * 매도 검증 실패 예외(HTTP 400).
 */
public class SaleValidationException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SaleValidationException(String message) {
    super(message);
  }
}
