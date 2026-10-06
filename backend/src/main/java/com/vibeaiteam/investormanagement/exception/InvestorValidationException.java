package com.vibeaiteam.investormanagement.exception;

/** 투자자 검증 실패 예외(HTTP 400). */
public class InvestorValidationException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestorValidationException(String message) {
    super(message);
  }
}
