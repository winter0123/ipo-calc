package com.ipocalc.profitcalculation.controller.profit.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 수익 조회 소유권 위반 예외(HTTP 403). */
public class ProfitForbiddenException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public ProfitForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
