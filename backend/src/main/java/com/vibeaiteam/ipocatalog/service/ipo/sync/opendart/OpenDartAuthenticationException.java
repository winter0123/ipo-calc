package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

public class OpenDartAuthenticationException extends OpenDartApiException {

  public OpenDartAuthenticationException(String statusCode, String message) {
    super(statusCode, message);
  }
}
