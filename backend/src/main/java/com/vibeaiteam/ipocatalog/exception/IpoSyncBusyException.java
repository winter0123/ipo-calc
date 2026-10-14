package com.vibeaiteam.ipocatalog.exception;

import com.vibeaiteam.accountauthentication.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 공모주 동기화가 이미 실행 중일 때(HTTP 409). */
public class IpoSyncBusyException extends ApiException {

  /** 기본 메시지로 예외를 생성한다. */
  public IpoSyncBusyException() {
    super(HttpStatus.CONFLICT, "IPO_SYNC_RUNNING", "이미 공모주 가져오기가 진행 중입니다. 잠시 후 다시 시도하세요.");
  }
}
