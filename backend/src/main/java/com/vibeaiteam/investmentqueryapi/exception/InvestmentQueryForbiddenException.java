package com.vibeaiteam.investmentqueryapi.exception;

/** 투자 이력 조회 소유권 위반 예외(HTTP 403). */
public class InvestmentQueryForbiddenException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestmentQueryForbiddenException(String message) {
    super(message);
  }
}
