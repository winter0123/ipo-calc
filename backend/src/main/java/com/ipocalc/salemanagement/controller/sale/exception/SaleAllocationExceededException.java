package com.ipocalc.salemanagement.controller.sale.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 매도 수량이 배정 수량을 초과한 예외(HTTP 400). */
public class SaleAllocationExceededException extends ApiException {

  /** 메시지를 담아 예외를 생성한다. */
  public SaleAllocationExceededException(String message) {
    super(HttpStatus.BAD_REQUEST, "ALLOCATION_EXCEEDED", message);
  }
}
