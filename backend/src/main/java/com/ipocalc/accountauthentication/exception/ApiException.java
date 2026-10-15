package com.ipocalc.accountauthentication.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 상태와 errorCode를 스스로 가진 업무 예외의 공통 기반.
 *
 * <p>하위 예외는 생성자에서 상태·errorCode를 지정하고, 응답 변환은 {@link GlobalExceptionHandler} 하나가 맡는다.
 */
public abstract class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String errorCode;

  /**
   * 상태·errorCode·메시지를 지정해 예외를 생성한다.
   *
   * @param status 응답 HTTP 상태
   * @param errorCode 응답 에러 코드
   * @param message 사용자에게 보여줄 메시지
   */
  protected ApiException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  /** 응답 HTTP 상태를 돌려준다. */
  public HttpStatus getStatus() {
    return status;
  }

  /** 응답 에러 코드를 돌려준다. */
  public String getErrorCode() {
    return errorCode;
  }
}
