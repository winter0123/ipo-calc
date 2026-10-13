package com.vibeaiteam.accountauthentication.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 전역 처리기가 ApiException의 상태·errorCode·메시지를 그대로 응답에 옮기는지 확인한다. */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  /** 테스트용 예외. */
  private static class TeapotException extends ApiException {
    private static final long serialVersionUID = 1L;

    TeapotException(String message) {
      super(HttpStatus.I_AM_A_TEAPOT, "TEAPOT", message);
    }
  }

  @Test
  void apiExceptionIsMappedToItsStatusErrorCodeAndMessage() {
    ResponseEntity<ErrorResponse> response = handler.handleApiException(new TeapotException("차"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
    assertThat(response.getHeaders().getContentType()).isEqualTo(ErrorResponses.JSON_UTF8);
    assertThat(response.getBody().getError()).isEqualTo("TEAPOT");
    assertThat(response.getBody().getMessage()).isEqualTo("차");
  }
}
