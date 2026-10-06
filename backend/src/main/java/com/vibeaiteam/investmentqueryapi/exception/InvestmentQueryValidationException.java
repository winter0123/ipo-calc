package com.vibeaiteam.investmentqueryapi.exception;

/** 투자 이력 조회 검증 실패 예외(HTTP 400). */
public class InvestmentQueryValidationException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestmentQueryValidationException(String message) {
    super(message);
  }
}
