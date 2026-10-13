package com.vibeaiteam.brokerintegration.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 브로커 연결 기능이 비활성 상태일 때의 예외(HTTP 503).
 *
 * <p>APP_ENCRYPTION_BROKER_KEY(또는 app.encryption.broker-key)가 미설정이면 앱키·앱시크릿을 암호화해 저장할 수 없으므로 등록 요청을
 * 명확한 오류로 거부한다(AC-1814). 계좌번호 암호화 키와 달리 기동은 허용하되 연결 기능만 막는다.
 */
public class BrokerConnectionUnavailableException extends ApiException {

  private static final long serialVersionUID = 1L;

  /**
   * 메시지를 담아 예외를 생성한다.
   *
   * @param message 시크릿이 포함되지 않은 비활성 사유 메시지
   */
  public BrokerConnectionUnavailableException(String message) {
    super(HttpStatus.SERVICE_UNAVAILABLE, "BROKER_CONNECTION_UNAVAILABLE", message);
  }
}
