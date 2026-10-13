package com.vibeaiteam.investormanagement.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 투자자 검증 실패 예외(HTTP 400). */
public class InvestorValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestorValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
