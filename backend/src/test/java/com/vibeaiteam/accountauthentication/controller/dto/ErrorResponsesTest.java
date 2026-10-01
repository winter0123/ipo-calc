package com.vibeaiteam.accountauthentication.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ErrorResponsesTest {

  @Test
  void ofBuildsStatusJsonUtf8AndErrorBody() {
    ResponseEntity<ErrorResponse> response =
        ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없습니다");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("application/json;charset=UTF-8");
    assertThat(response.getBody().getError()).isEqualTo("NOT_FOUND");
    assertThat(response.getBody().getMessage()).isEqualTo("찾을 수 없습니다");
    assertThat(response.getBody().getFieldErrors()).isNull();
  }

  @Test
  void validationFailedCarriesFieldErrorsWith400() {
    ErrorResponse.FieldError fieldError =
        ErrorResponse.FieldError.builder().field("email").message("형식이 올바르지 않습니다").build();

    ResponseEntity<ErrorResponse> response = ErrorResponses.validationFailed(List.of(fieldError));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getError()).isEqualTo("VALIDATION_FAILED");
    assertThat(response.getBody().getMessage()).isEqualTo("입력값 검증에 실패했습니다");
    assertThat(response.getBody().getFieldErrors()).containsExactly(fieldError);
  }
}
