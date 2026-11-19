package com.ipocalc.ipocatalog.domain.model.ipo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 원문 검증 가격의 항목별 출처·후퇴 방지 규칙을 검증한다. */
class IpoVerifiedOfferingTermsTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

  private Ipo newAutoIpo() {
    return Ipo.builder().syncSource("OPENDART").build();
  }

  private DartOfferingTerms terms(String receipt, int version, Long low, Long high, Long fixed) {
    return new DartOfferingTerms(receipt, version, low, high, fixed, null, null, null, null, null);
  }

  @Test
  void newAutoIpoAcceptsFirstVerifiedBandAndFixedPrice() {
    Ipo ipo = newAutoIpo();
    assertThat(ipo.applyVerifiedOfferingTerms(
        terms("20261030000616", 2, 9_400L, 11_000L, null), NOW)).isTrue();
    assertThat(ipo.applyVerifiedOfferingTerms(
        terms("20261103000312", 2, null, null, 11_000L), NOW)).isTrue();
    assertThat(ipo.getLowerBandPrice()).isEqualTo(9_400L);
    assertThat(ipo.getUpperBandPrice()).isEqualTo(11_000L);
    assertThat(ipo.getBandPriceSourceReceiptNo()).isEqualTo("20261030000616");
    assertThat(ipo.getFixedPrice()).isEqualTo(11_000L);
    assertThat(ipo.getFixedPriceSourceReceiptNo()).isEqualTo("20261103000312");
  }

  @Test
  void staleFilingNeverRevertsVerifiedBandOrFixedPrice() {
    Ipo ipo = newAutoIpo();
    ipo.applyVerifiedOfferingTerms(
        terms("20261030000616", 2, 9_400L, 11_000L, null), NOW);
    ipo.applyVerifiedOfferingTerms(
        terms("20261103000312", 2, null, null, 11_000L), NOW);

    assertThat(ipo.applyVerifiedOfferingTerms(
        terms("20261008000649", 2, 8_000L, 9_000L, 8_500L), NOW)).isFalse();
    assertThat(ipo.getLowerBandPrice()).isEqualTo(9_400L);
    assertThat(ipo.getFixedPrice()).isEqualTo(11_000L);
  }

  @Test
  void newerVerifiedSourceIsStoredEvenWhenPriceUnchanged() {
    Ipo ipo = newAutoIpo();
    ipo.applyVerifiedOfferingTerms(
        terms("20261008000649", 1, 9_400L, 11_000L, null), NOW);
    assertThat(ipo.applyVerifiedOfferingTerms(
        terms("20261030000616", 2, 9_400L, 11_000L, null), NOW)).isTrue();
    assertThat(ipo.getBandPriceSourceReceiptNo()).isEqualTo("20261030000616");
    assertThat(ipo.getBandPriceParserVersion()).isEqualTo(2);
    assertThat(ipo.applyVerifiedOfferingTerms(
        terms("20261030000616", 1, 8_000L, 9_000L, null), NOW)).isFalse();
  }

  @Test
  void untrackedExistingPriceAndManualIpoAreProtected() {
    Ipo importedWithUnknownPrice =
        Ipo.builder().syncSource("OPENDART").fixedPrice(9_400L).build();
    assertThat(importedWithUnknownPrice.applyVerifiedOfferingTerms(
        terms("20261103000312", 2, null, null, 11_000L), NOW)).isFalse();
    assertThat(importedWithUnknownPrice.getFixedPrice()).isEqualTo(9_400L);

    Ipo manual = Ipo.builder().fixedPrice(9_400L).build();
    assertThat(manual.applyVerifiedOfferingTerms(
        terms("20261103000312", 2, null, null, 11_000L), NOW)).isFalse();
  }
}
