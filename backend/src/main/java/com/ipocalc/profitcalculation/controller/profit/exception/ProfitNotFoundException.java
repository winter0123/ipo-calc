package com.ipocalc.profitcalculation.controller.profit.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 수익 대상 없음 예외(HTTP 404). */
public class ProfitNotFoundException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public ProfitNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
