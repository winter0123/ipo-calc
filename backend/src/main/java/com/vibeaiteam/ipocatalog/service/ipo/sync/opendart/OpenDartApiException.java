package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

/**
 * OpenDART API 오류 응답 예외. 상태 코드를 함께 담는다.
 */
public class OpenDartApiException extends RuntimeException {

  private final String statusCode;

  /**
   * OpenDART 상태 코드와 메시지로 예외를 생성한다.
   */
  public OpenDartApiException(String statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public String getStatusCode() {
    return statusCode;
  }
}
