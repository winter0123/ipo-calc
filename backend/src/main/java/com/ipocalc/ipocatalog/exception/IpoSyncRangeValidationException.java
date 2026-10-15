package com.ipocalc.ipocatalog.exception;

import com.ipocalc.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 공모주 동기화 범위 검증 실패(HTTP 400). */
public class IpoSyncRangeValidationException extends ApiException {

  /** 에러 코드. */
  public static final String ERROR_CODE = "INVALID_SYNC_RANGE";

  /**
   * 메시지로 예외를 생성한다.
   *
   * @param message 사용자에게 보여줄 한국어 메시지
   */
  public IpoSyncRangeValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, ERROR_CODE, message);
  }
}