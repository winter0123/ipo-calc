package com.ipocalc.salemanagement.controller.sale.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 매도 검증 실패 예외(HTTP 400). */
public class SaleValidationException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SaleValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
