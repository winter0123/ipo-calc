package com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * AC 인용: docs/spec/securities-account-management.md (엣지케이스 #9),
 * docs/architecture/securities-account-management.md (마스킹 규칙) - 계좌번호 뒷 4자리만 평문 노출, 나머지는 '*'로 마스킹
 * (하이픈 포함 전체 길이 기준) - 4자리 미만인 계좌번호는 전체 마스킹
 */
class AccountNumberMaskerTest {

  @Test
  void maskRevealsOnlyLastFourDigitsOfFullLength() {
    // Given: 하이픈 포함 14자리 계좌번호 "123-456-789012"
    // When: mask 호출
    String masked = AccountNumberMasker.mask("123-456-789012");

    // Then: 뒷 4자리 "9012"만 노출되고 나머지 10자리는 '*'로 마스킹된다
    assertThat(masked).isEqualTo("**********9012");
  }

  @Test
  void maskNumbersShorterThanFourDigitsFully() {
    // Given: 3자리 계좌번호 "123"
    // - spec 엣지케이스 #9: 4자리 미만이면 전체 마스킹
    assertThat(AccountNumberMasker.mask("123")).isEqualTo("***");
    assertThat(AccountNumberMasker.mask("12")).isEqualTo("**");
  }

  @Test
  void maskRevealsAllForExactlyFourDigits() {
    // Given: 4자리 계좌번호 "9012"
    // - 4자리 이상이면 뒷 4자리를 노출하므로 전체가 노출된다
    assertThat(AccountNumberMasker.mask("9012")).isEqualTo("9012");
  }

  @Test
  void maskHandlesEmptyAndNull() {
    assertThat(AccountNumberMasker.mask("")).isEmpty();
    assertThat(AccountNumberMasker.mask(null)).isNull();
  }

  @Test
  void maskPreservesOriginalLength() {
    // Given: 마스킹된 결과의 길이는 원문 길이와 동일해야 한다
    String original = "123-456-789012";
    assertThat(AccountNumberMasker.mask(original)).hasSize(original.length());
  }
}
