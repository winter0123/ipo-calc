package com.ipocalc.profitcalculation.controller.profit.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 수익 조회 요청값 오류 예외(HTTP 400). */
public class ProfitValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public ProfitValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
