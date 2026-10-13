package com.vibeaiteam.investormanagement.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 투자자 대상 없음 예외(HTTP 404). */
public class InvestorNotFoundException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestorNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
