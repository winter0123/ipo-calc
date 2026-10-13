package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 공모주 예외를 HTTP 에러 응답으로 변환한다. */
@RestControllerAdvice
public class IpoExceptionHandler {

  /** 업로드 크기 제한 초과를 HTTP 400 응답으로 변환한다. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException ex) {
    return ErrorResponses.of(
        HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "CSV 파일은 최대 5MB까지 업로드할 수 있습니다.");
  }
}
