package com.vibeaiteam.investmentqueryapi.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 투자 이력 조회 검증 실패 예외(HTTP 400). */
public class InvestmentQueryValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestmentQueryValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
  }
}
