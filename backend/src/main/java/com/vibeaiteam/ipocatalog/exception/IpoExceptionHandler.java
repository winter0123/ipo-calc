package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 공모주 예외를 HTTP 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class IpoExceptionHandler {

  /**
   * IpoValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(IpoValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(IpoValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * MarketHolidayValidationException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(MarketHolidayValidationException.class)
  public ResponseEntity<ErrorResponse> handleMarketHolidayValidation(
      MarketHolidayValidationException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  /**
   * IpoInvalidCsvException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(IpoInvalidCsvException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCsv(IpoInvalidCsvException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "INVALID_CSV", ex.getMessage());
  }

  /**
   * IpoFileTooLargeException을 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(IpoFileTooLargeException.class)
  public ResponseEntity<ErrorResponse> handleFileTooLarge(IpoFileTooLargeException ex) {
    return ErrorResponses.of(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", ex.getMessage());
  }

  /**
   * 업로드 크기 제한 초과를 HTTP 400 응답으로 변환한다.
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex) {
    return ErrorResponses.of(
        HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "CSV 파일은 최대 5MB까지 업로드할 수 있습니다.");
  }

  /**
   * IpoConflictException을 HTTP 409 응답으로 변환한다.
   */
  @ExceptionHandler(IpoConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(IpoConflictException ex) {
    return ErrorResponses.of(HttpStatus.CONFLICT, "DUPLICATE_IPO", ex.getMessage());
  }

  /**
   * IpoNotFoundException을 HTTP 404 응답으로 변환한다.
   */
  @ExceptionHandler(IpoNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(IpoNotFoundException ex) {
    return ErrorResponses.of(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String errorCode, String message) {
    return ErrorResponses.of(status, errorCode, message);
  }
}
