package com.vibeaiteam.investmentqueryapi.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 투자 이력 조회 소유권 위반 예외(HTTP 403). */
public class InvestmentQueryForbiddenException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestmentQueryForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
