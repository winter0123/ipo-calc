package com.ipocalc.brokerintegration.exception;

/**
 * 브로커 API 오류를 격리하는 예외(research 4.3-R5).
 *
 * <p>브로커 스펙이 도메인으로 새지 않게 하는 경계다. 앱키·앱시크릿·토큰·계좌번호 같은 시크릿은 상태 코드나 메시지에 담기지 않는다.
 */
public class BrokerApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String statusCode;

  /**
   * 브로커 상태 코드와 메시지로 예외를 생성한다.
   *
   * @param statusCode 브로커 상태 코드 또는 실패 원인 코드
   * @param message 시크릿이 포함되지 않은 실패 메시지
   */
  public BrokerApiException(String statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  /**
   * 브로커 상태 코드, 메시지, 원인 예외로 예외를 생성한다.
   *
   * @param statusCode 브로커 상태 코드 또는 실패 원인 코드
   * @param message 시크릿이 포함되지 않은 실패 메시지
   * @param cause 원인 예외
   */
  public BrokerApiException(String statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  /**
   * 브로커 상태 코드를 반환한다.
   *
   * @return 상태 코드
   */
  public String getStatusCode() {
    return statusCode;
  }
}
