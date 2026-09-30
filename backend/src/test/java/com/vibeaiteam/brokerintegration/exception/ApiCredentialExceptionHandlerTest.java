package com.vibeaiteam.brokerintegration.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * ApiCredentialExceptionHandler가 각 예외를 올바른 HTTP 상태와 에러 코드로 변환하는지 검증한다.
 */
class ApiCredentialExceptionHandlerTest {

  private final ApiCredentialExceptionHandler handler = new ApiCredentialExceptionHandler();

  @Test
  void validationExceptionMapsTo400ValidationError() {
    ResponseEntity<ErrorResponse> response =
        handler.handleApiCredentialValidation(
            new ApiCredentialValidationException("apiProvider는 필수 입력값입니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getError()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().getMessage()).isEqualTo("apiProvider는 필수 입력값입니다.");
  }

  @Test
  void notFoundExceptionMapsTo404() {
    ResponseEntity<ErrorResponse> response =
        handler.handleApiCredentialNotFound(new ApiCredentialNotFoundException("증권계좌를 찾을 수 없습니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getError()).isEqualTo("NOT_FOUND");
  }

  @Test
  void forbiddenExceptionMapsTo403() {
    ResponseEntity<ErrorResponse> response =
        handler.handleApiCredentialForbidden(new ApiCredentialForbiddenException("계정 소유권이 없습니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().getError()).isEqualTo("FORBIDDEN");
  }

  @Test
  void conflictExceptionMapsTo409() {
    ResponseEntity<ErrorResponse> response =
        handler.handleApiCredentialConflict(
            new ApiCredentialConflictException("이미 등록된 브로커 API 인증정보가 있습니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getError()).isEqualTo("CONFLICT");
  }

  @Test
  void brokerConnectionUnavailableExceptionMapsTo503WithClearMessage() {
    // AC-1814: 키 미설정 시 등록 요청이 명확한 오류(503)로 실패해야 한다
    ResponseEntity<ErrorResponse> response =
        handler.handleBrokerConnectionUnavailable(
            new BrokerConnectionUnavailableException(
                "브로커 연결 미활성: APP_ENCRYPTION_BROKER_KEY(또는 app.encryption.broker-key)가 "
                    + "설정되지 않아 인증정보를 등록할 수 없습니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().getError()).isEqualTo("BROKER_CONNECTION_UNAVAILABLE");
    assertThat(response.getBody().getMessage()).contains("브로커 연결 미활성");
  }
}
