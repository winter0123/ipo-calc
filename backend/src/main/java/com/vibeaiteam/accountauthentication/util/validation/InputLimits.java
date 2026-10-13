package com.vibeaiteam.accountauthentication.util.validation;

/** 입력값 상한. 합산·곱셈 오버플로와 DB 컬럼 길이 초과(500)를 막으려고 DTO 값에 거는 공통 한도다. */
public final class InputLimits {

  /** 수량(주) 상한: 10억. */
  public static final long MAX_QUANTITY = 1_000_000_000L;

  /** 금액(원) 상한: 1경 미만. */
  public static final long MAX_AMOUNT = 9_999_999_999_999_999L;

  /** 자유 텍스트(메모 등) 최대 길이. */
  public static final int MAX_MEMO_LENGTH = 1000;

  private InputLimits() {}
}
