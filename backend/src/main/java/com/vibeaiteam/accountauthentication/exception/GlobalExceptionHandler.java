package com.vibeaiteam.accountauthentication.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
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

  /**
   * DuplicateEmailException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ErrorResponse> handleDuplicateEmail(
      DuplicateEmailException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", ex.getMessage());
  }

  /**
   * InvalidCredentialsException을 HTTP 401 응답으로 변환한다.
   */
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
  }

  /**
   * InvalidCurrentPasswordException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(InvalidCurrentPasswordException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(
      InvalidCurrentPasswordException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", ex.getMessage());
  }

  /**
   * AccountLockedException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<ErrorResponse> handleAccountLocked(
      AccountLockedException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", ex.getMessage());
  }

  /**
   * AccountPendingApprovalException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(AccountPendingApprovalException.class)
  public ResponseEntity<ErrorResponse> handleAccountPendingApproval(
      AccountPendingApprovalException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING_APPROVAL", ex.getMessage());
  }

  /**
   * SignupClosedException을 HTTP 403 응답으로 변환한다.
   */
  @ExceptionHandler(SignupClosedException.class)
  public ResponseEntity<ErrorResponse> handleSignupClosed(
      SignupClosedException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.FORBIDDEN, "SIGNUP_CLOSED", ex.getMessage());
  }

  /**
   * AccountNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(AccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleAccountNotFound(
      AccountNotFoundException ex, HttpServletRequest request) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  /**
   * ValidationFailedException을 필드 오류가 담긴 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(ValidationFailedException.class)
  public ResponseEntity<ErrorResponse> handleValidationFailed(
      ValidationFailedException ex, HttpServletRequest request) {
    return ErrorResponses.validationFailed(List.of(
                    ErrorResponse.FieldError.builder()
                        .field(ex.getField())
                        .message(ex.getMessage())
                        .build()));
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

    return ErrorResponses.validationFailed(fieldErrors);
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

    return ErrorResponses.validationFailed(fieldErrors);
  }

  /**
   * 제약 조건 위반을 HTTP 400 응답으로 변환한다.
   */
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
