package com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption;

/**
 * 계좌번호 마스킹 유틸리티.
 *
 * <p>- 규칙: 계좌번호 뒷 4자리만 평문 노출, 나머지는 '*'로 마스킹 (하이픈 포함 전체 길이 기준) 예: "123-456-789012"(14자) ->
 * "**********9012" - 4자리 미만인 계좌번호는 전체 마스킹. - spec 엣지케이스 #9 / architecture.md 마스킹 규칙 참고.
 */
public final class AccountNumberMasker {

  private static final int VISIBLE_LENGTH = 4;

  private AccountNumberMasker() {}

  /**
   * 계좌번호 끝 4자리만 남기고 마스킹한다. null이면 null을 반환한다.
   */
  public static String mask(String plainAccountNumber) {
    if (plainAccountNumber == null) {
      return null;
    }
    int length = plainAccountNumber.length();
    if (length < VISIBLE_LENGTH) {
      return "*".repeat(length);
    }
    String lastFour = plainAccountNumber.substring(length - VISIBLE_LENGTH);
    return "*".repeat(length - VISIBLE_LENGTH) + lastFour;
  }
}
