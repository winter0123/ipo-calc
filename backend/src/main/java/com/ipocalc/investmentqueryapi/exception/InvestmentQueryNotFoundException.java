package com.ipocalc.investmentqueryapi.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 투자 이력 조회 대상 없음 예외(HTTP 404). */
public class InvestmentQueryNotFoundException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestmentQueryNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
