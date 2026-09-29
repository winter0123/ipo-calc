package com.vibeaiteam.investormanagement.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InvestorExceptionHandler {

  private static final MediaType JSON_UTF8 =
      new MediaType("application", "json", StandardCharsets.UTF_8);

  @ExceptionHandler(InvestorValidationException.class)
  public ResponseEntity<ErrorResponse> handleInvestorValidation(InvestorValidationException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("VALIDATION_ERROR").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(JSON_UTF8).body(response);
  }

  @ExceptionHandler(InvestorNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleInvestorNotFound(InvestorNotFoundException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("NOT_FOUND").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(JSON_UTF8).body(response);
  }

  @ExceptionHandler(InvestorForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleInvestorForbidden(InvestorForbiddenException ex) {
    ErrorResponse response =
        ErrorResponse.builder().error("FORBIDDEN").message(ex.getMessage()).build();
    return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(JSON_UTF8).body(response);
  }
}
