package com.vibeaiteam.investormanagement.exception;

/**
 * 투자자 대상 없음 예외(HTTP 404).
 */
public class InvestorNotFoundException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public InvestorNotFoundException(String message) {
    super(message);
  }
}
