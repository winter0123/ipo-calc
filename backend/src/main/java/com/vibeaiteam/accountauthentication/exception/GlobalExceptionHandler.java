package com.vibeaiteam.accountauthentication.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증·계정 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  /**
   * DuplicateEmailException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateEmail(
      DuplicateEmailException ex, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.builder().error("DUPLICATE_EMAIL").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.CONFLICT).contentType(JSON_UTF8).body(response);
  }

  /**
   * InvalidCredentialsException을 HTTP 401 응답으로 변환한다.
   */
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.builder().error("INVALID_CREDENTIALS").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(JSON_UTF8).body(response);
  }

  /**
   * InvalidCurrentPasswordException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(InvalidCurrentPasswordException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(
      InvalidCurrentPasswordException ex, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.builder().error("INVALID_CURRENT_PASSWORD").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * AccountLockedException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<ErrorResponse> handleAccountLocked(
      AccountLockedException ex, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.builder().error("ACCOUNT_LOCKED").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(JSON_UTF8).body(response);
  }

  /**
   * ValidationFailedException을 필드 오류가 담긴 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(ValidationFailedException.class)
  public ResponseEntity<ErrorResponse> handleValidationFailed(
      ValidationFailedException ex, HttpServletRequest request) {
    ErrorResponse response =
        ErrorResponse.builder()
            .error("VALIDATION_FAILED")
            .message("입력값 검증에 실패했습니다")
            .fieldErrors(
                List.of(
                    ErrorResponse.FieldError.builder()
                        .field(ex.getField())
                        .message(ex.getMessage())
                        .build()))
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * 요청 본문 Bean Validation 실패를 필드별 오류가 담긴 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .collect(Collectors.toList());

    ErrorResponse response =
        ErrorResponse.builder()
            .error("VALIDATION_FAILED")
            .message("입력값 검증에 실패했습니다")
            .fieldErrors(fieldErrors)
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * 바인딩 실패를 필드별 오류가 담긴 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorResponse> handleBindException(
      BindException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .collect(Collectors.toList());

    ErrorResponse response =
        ErrorResponse.builder()
            .error("VALIDATION_FAILED")
            .message("입력값 검증에 실패했습니다")
            .fieldErrors(fieldErrors)
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  /**
   * 제약 조건 위반을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getConstraintViolations().stream().map(this::toFieldError).collect(Collectors.toList());

    ErrorResponse response =
        ErrorResponse.builder()
            .error("VALIDATION_FAILED")
            .message("입력값 검증에 실패했습니다")
            .fieldErrors(fieldErrors)
            .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
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
