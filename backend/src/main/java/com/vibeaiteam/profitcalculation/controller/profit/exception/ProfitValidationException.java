package com.vibeaiteam.profitcalculation.controller.profit.exception;

/** 수익 조회 요청값 오류 예외(HTTP 400). */
public class ProfitValidationException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public ProfitValidationException(String message) {
    super(message);
  }
}
