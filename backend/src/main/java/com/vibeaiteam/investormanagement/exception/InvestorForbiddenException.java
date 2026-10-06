package com.vibeaiteam.investormanagement.exception;

/** 투자자 소유권 위반 예외(HTTP 403). */
public class InvestorForbiddenException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestorForbiddenException(String message) {
    super(message);
  }
}
