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
    boolean bandChanged =
        ipo.applyVerifiedOfferingTerms(terms("20261030000616", 2, 9_400L, 11_000L, null), NOW);
    boolean fixedChanged =
        ipo.applyVerifiedOfferingTerms(terms("20261103000312", 2, null, null, 11_000L), NOW);
    assertThat(bandChanged).isTrue();
    assertThat(fixedChanged).isTrue();
    assertThat(ipo.getLowerBandPrice()).isEqualTo(9_400L);
    assertThat(ipo.getUpperBandPrice()).isEqualTo(11_000L);
    assertThat(ipo.getBandPriceSourceReceiptNo()).isEqualTo("20261030000616");
    assertThat(ipo.getFixedPrice()).isEqualTo(11_000L);
    assertThat(ipo.getFixedPriceSourceReceiptNo()).isEqualTo("20261103000312");
  }

  @Test
  void staleFilingNeverRevertsVerifiedBandOrFixedPrice() {
    Ipo ipo = newAutoIpo();
    ipo.applyVerifiedOfferingTerms(terms("20261030000616", 2, 9_400L, 11_000L, null), NOW);
    ipo.applyVerifiedOfferingTerms(terms("20261103000312", 2, null, null, 11_000L), NOW);
    boolean staleChanged =
        ipo.applyVerifiedOfferingTerms(terms("20261008000649", 2, 8_000L, 9_000L, 8_500L), NOW);

    assertThat(staleChanged).isFalse();
    assertThat(ipo.getLowerBandPrice()).isEqualTo(9_400L);
    assertThat(ipo.getFixedPrice()).isEqualTo(11_000L);
  }

  @Test
  void newerVerifiedSourceIsStoredEvenWhenPriceUnchanged() {
    Ipo ipo = newAutoIpo();
    ipo.applyVerifiedOfferingTerms(terms("20261008000649", 1, 9_400L, 11_000L, null), NOW);
    boolean changed =
        ipo.applyVerifiedOfferingTerms(terms("20261030000616", 2, 9_400L, 11_000L, null), NOW);
    assertThat(changed).isTrue();
    assertThat(ipo.getBandPriceSourceReceiptNo()).isEqualTo("20261030000616");
    assertThat(ipo.getBandPriceParserVersion()).isEqualTo(2);
    boolean staleParserChanged =
        ipo.applyVerifiedOfferingTerms(terms("20261030000616", 1, 8_000L, 9_000L, null), NOW);
    assertThat(staleParserChanged).isFalse();
  }

  @Test
  void untrackedExistingPriceAndManualIpoAreProtected() {
    Ipo importedWithUnknownPrice = Ipo.builder().syncSource("OPENDART").fixedPrice(9_400L).build();
    boolean unknownChanged =
        importedWithUnknownPrice.applyVerifiedOfferingTerms(
            terms("20261103000312", 2, null, null, 11_000L), NOW);
    assertThat(unknownChanged).isFalse();
    assertThat(importedWithUnknownPrice.getFixedPrice()).isEqualTo(9_400L);

    Ipo manual = Ipo.builder().fixedPrice(9_400L).build();
    boolean manualChanged =
        manual.applyVerifiedOfferingTerms(terms("20261103000312", 2, null, null, 11_000L), NOW);
    assertThat(manualChanged).isFalse();
  }

  @Test
  void manualPriceEditClearsOnlyItsProvenanceAndBlocksLaterAutomaticOverwrite() {
    Ipo ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .lowerBandPrice(9_400L)
            .upperBandPrice(11_000L)
            .bandPriceSourceReceiptNo("20261008000649")
            .bandPriceParserVersion(2)
            .fixedPrice(11_000L)
            .fixedPriceSourceReceiptNo("20261103000312")
            .fixedPriceParserVersion(2)
            .build();
    ipo.updateDetails(
        "기업", "기업", null, null, null, null, 9_400L, 11_000L, 12_000L, null, null, NOW);

    assertThat(ipo.getBandPriceSourceReceiptNo()).isEqualTo("20261008000649");
    assertThat(ipo.getFixedPriceSourceReceiptNo()).isNull();
    assertThat(ipo.getFixedPriceParserVersion()).isNull();

    ipo.applyVerifiedOfferingTerms(terms("20261110000001", 2, null, null, 13_000L), NOW);
    assertThat(ipo.getFixedPrice()).isEqualTo(12_000L);
  }

  @Test
  void verifiedSharesRejectOlderReceiptsAndManualOverrides() {
    Ipo ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .offeringShareCount(1_000_000L)
            .lastImportedOfferingShareCount(1_000_000L)
            .lastVerifiedShareReceiptNo("20261103000312")
            .build();
    ipo.applyVerifiedShareCount(1_200_000L, "20261104000123");
    assertThat(ipo.getOfferingShareCount()).isEqualTo(1_200_000L);
    ipo.applyVerifiedShareCount(900_000L, "20261030000616");
    assertThat(ipo.getOfferingShareCount()).isEqualTo(1_200_000L);

    ipo.updateDetails(
        "기업", "기업", null, null, null, null, null, null, null, null, 1_300_000L, NOW);
    assertThat(ipo.getLastImportedOfferingShareCount()).isNull();
    ipo.applyVerifiedShareCount(1_500_000L, "20261105000123");
    assertThat(ipo.getOfferingShareCount()).isEqualTo(1_300_000L);
  }

  @Test
  void receiptChronologyGuardsVerifiedScheduleWithoutBlockingSameReceipt() {
    Ipo ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .lastVerifiedScheduleReceiptNo("20261103000312")
            .build();
    assertThat(ipo.mayApplyVerifiedSchedule("20261030000616")).isFalse();
    assertThat(ipo.mayApplyVerifiedSchedule("20261103000312")).isTrue();
    assertThat(ipo.mayApplyVerifiedSchedule("20261104000123")).isTrue();
    ipo.recordVerifiedScheduleReceipt("20261104000123");
    ipo.recordVerifiedScheduleReceipt("20261030000616");
    assertThat(ipo.getLastVerifiedScheduleReceiptNo()).isEqualTo("20261104000123");
  }

  @Test
  void manuallyEditedSubscriptionDateInvalidatesAutoOverwriteProvenance() {
    Ipo ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(java.time.LocalDate.of(2026, 11, 5))
            .lastImportedSubscriptionStartDate(java.time.LocalDate.of(2026, 11, 5))
            .build();
    ipo.updateDetails(
        "기업",
        "기업",
        null,
        null,
        java.time.LocalDate.of(2026, 11, 7),
        null,
        null,
        null,
        null,
        null,
        null,
        NOW);
    assertThat(ipo.getLastImportedSubscriptionStartDate()).isNull();
    ipo.syncFromOpenDart(
        "기업",
        null,
        java.time.LocalDate.of(2026, 11, 9),
        null,
        null,
        null,
        "OPENDART",
        NOW);
    assertThat(ipo.getSubscriptionStartDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 7));
  }


  @Test
  void automaticSyncDoesNotOverwriteEditedDisplayNames() {
    Ipo ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .companyName("관리자가 바꾼 회사명")
            .ipoName("관리자가 바꾼 종목명")
            .build();

    ipo.syncFromOpenDart("DART 신규 회사명", null, null, null, null, null, "OPENDART", NOW);

    assertThat(ipo.getCompanyName()).isEqualTo("관리자가 바꾼 회사명");
    assertThat(ipo.getIpoName()).isEqualTo("관리자가 바꾼 종목명");
  }

}
