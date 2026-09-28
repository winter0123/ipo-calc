package com.vibeaiteam.profitcalculation.controller.profit.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

// Profit 계산 API의 404 에러를 { "error": "NOT_FOUND", "message": "..." } 형식으로 변환한다.
// 기존 모듈의 SubscriptionExceptionHandler 패턴을 따른다.
@RestControllerAdvice
public class ProfitExceptionHandler {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @ExceptionHandler(ProfitNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProfitNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String errorCode, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .error(errorCode)
                .message(message)
                .build();
        return ResponseEntity.status(status)
                .contentType(JSON_UTF8)
                .body(response);
    }
}