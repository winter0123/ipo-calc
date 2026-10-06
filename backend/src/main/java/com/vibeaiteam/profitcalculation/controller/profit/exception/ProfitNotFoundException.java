package com.vibeaiteam.profitcalculation.controller.profit.exception;

/** 수익 대상 없음 예외(HTTP 404). */
public class ProfitNotFoundException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public ProfitNotFoundException(String message) {
    super(message);
  }
}
