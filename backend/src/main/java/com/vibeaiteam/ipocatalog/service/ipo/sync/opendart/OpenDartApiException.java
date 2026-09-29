package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

public class OpenDartApiException extends RuntimeException {

  private final String statusCode;

  public OpenDartApiException(String statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public String getStatusCode() {
    return statusCode;
  }
}
