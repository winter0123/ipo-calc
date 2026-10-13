package com.vibeaiteam.accountauthentication.util.validation;

import java.util.Locale;

/** 종목코드 정규화. 공모주 저장과 증권사 응답 매칭이 같은 규칙을 쓰도록 한 곳에 둔다. */
public final class StockCodes {

  private StockCodes() {}

  /**
   * 종목코드를 정규화한다: 앞뒤 공백 제거, 대문자, 7자리 'A' 접두사 제거, 숫자만이면 6자리로 0 채움. 상장 전 영숫자 코드(예: 0013Y0)는 그대로 둔다.
   *
   * @param raw 원본 종목코드
   * @return 정규화한 코드, 비었으면 null
   */
  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String code = raw.trim().toUpperCase(Locale.ROOT);
    if (code.length() == 7 && code.charAt(0) == 'A') {
      code = code.substring(1);
    }
    if (code.isEmpty()) {
      return null;
    }
    if (code.length() < 6 && code.chars().allMatch(c -> c >= '0' && c <= '9')) {
      code = "0".repeat(6 - code.length()) + code;
    }
    return code;
  }
}
