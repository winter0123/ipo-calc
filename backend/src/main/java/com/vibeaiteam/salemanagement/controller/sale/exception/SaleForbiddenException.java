package com.vibeaiteam.salemanagement.controller.sale.exception;

/** 매도 소유권 위반 예외(HTTP 403). */
public class SaleForbiddenException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public SaleForbiddenException(String message) {
    super(message);
  }
}
