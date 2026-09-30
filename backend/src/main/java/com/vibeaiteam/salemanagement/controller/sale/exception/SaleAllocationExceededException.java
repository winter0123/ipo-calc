package com.vibeaiteam.salemanagement.controller.sale.exception;

/**
 * 매도 수량이 배정 수량을 초과한 예외(HTTP 400).
 */
public class SaleAllocationExceededException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public SaleAllocationExceededException(String message) {
    super(message);
  }
}
