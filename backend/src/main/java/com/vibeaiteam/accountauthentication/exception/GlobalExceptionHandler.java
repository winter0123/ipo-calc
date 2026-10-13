package com.vibeaiteam.accountauthentication.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 업무 예외(ApiException)와 입력 검증 예외를 HTTP 에러 응답으로 변환하는 전역 처리기. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 상태·errorCode를 스스로 가진 업무 예외를 그 값 그대로의 에러 응답으로 변환한다. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
    return ErrorResponses.of(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
  }

  /** ValidationFailedException을 필드 오류가 담긴 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(ValidationFailedException.class)
  public ResponseEntity<ErrorResponse> handleValidationFailed(
      ValidationFailedException ex, HttpServletRequest request) {
    return ErrorResponses.validationFailed(
        List.of(
            ErrorResponse.FieldError.builder()
                .field(ex.getField())
                .message(ex.getMessage())
                .build()));
  }

  /** 요청 본문 Bean Validation 실패를 필드별 오류가 담긴 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .collect(Collectors.toList());

    return ErrorResponses.validationFailed(fieldErrors);
  }

  /** 바인딩 실패를 필드별 오류가 담긴 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> handleBindException(
      BindException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .collect(Collectors.toList());

    return ErrorResponses.validationFailed(fieldErrors);
  }

  /** 제약 조건 위반을 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getConstraintViolations().stream().map(this::toFieldError).collect(Collectors.toList());

    return ErrorResponses.validationFailed(fieldErrors);
  }

  private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
    return ErrorResponse.FieldError.builder()
        .field(fieldError.getField())
        .message(fieldError.getDefaultMessage())
        .build();
  }

  private ErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
    String field = violation.getPropertyPath().toString();
    String message = violation.getMessage();
    return ErrorResponse.FieldError.builder().field(field).message(message).build();
  }
}
