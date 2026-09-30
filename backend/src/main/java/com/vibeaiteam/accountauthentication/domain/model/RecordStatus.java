package com.vibeaiteam.accountauthentication.domain.model;

/** 소프트 삭제 레코드의 상태. DB 컬럼에는 {@link #name()} 문자열 그대로 저장된다. */
public enum RecordStatus {
  ACTIVE,
  INACTIVE,
  CANCELLED;

  /** {@code @Query} 문자열에 이어 붙이는 ACTIVE 완전 수식 enum 리터럴(HQL). */
  public static final String ACTIVE_HQL =
      "com.vibeaiteam.accountauthentication.domain.model.RecordStatus.ACTIVE";
}
