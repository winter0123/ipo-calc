package com.ipocalc.accountauthentication.domain.model;

/** 소프트 삭제 레코드의 상태. DB 컬럼에는 {@link #name()} 문자열 그대로 저장된다. */
public enum RecordStatus {
  ACTIVE,
  INACTIVE,
  CANCELLED
}
