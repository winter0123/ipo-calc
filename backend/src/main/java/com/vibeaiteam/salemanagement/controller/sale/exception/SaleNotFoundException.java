package com.vibeaiteam.salemanagement.controller.sale.exception;

/** 매도 대상 없음 예외(HTTP 404). */
public class SaleNotFoundException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SaleNotFoundException(String message) {
    super(message);
  }
}
