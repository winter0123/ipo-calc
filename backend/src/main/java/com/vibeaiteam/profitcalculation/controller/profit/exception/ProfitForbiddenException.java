package com.vibeaiteam.profitcalculation.controller.profit.exception;

/** 수익 조회 소유권 위반 예외(HTTP 403). */
public class ProfitForbiddenException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public ProfitForbiddenException(String message) {
    super(message);
  }
}
