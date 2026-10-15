package com.ipocalc.investormanagement.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 투자자 소유권 위반 예외(HTTP 403). */
public class InvestorForbiddenException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public InvestorForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
