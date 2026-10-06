package com.vibeaiteam.ipocatalog.exception;

/** CSV 파일 크기 초과 예외(HTTP 400). */
public class IpoFileTooLargeException extends RuntimeException {

  /** 메시지를 담아 예외를 생성한다. */
  public IpoFileTooLargeException(String message) {
    super(message);
  }
}
