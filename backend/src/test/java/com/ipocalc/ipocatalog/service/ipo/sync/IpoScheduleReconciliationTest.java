package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartFilingOrigin;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTermsParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 일정 정정 및 철회 시 기존 값과 공모 이력을 보호한다. */
class IpoScheduleReconciliationTest {
  private static final LocalDate FIRST = LocalDate.of(2026, 11, 10);

  @Test
  void importedDateChangesButManualAndLegacyDatesArePreserved() {
    Ipo ipo = Ipo.builder().build();
    merge(ipo, FIRST);
    merge(ipo, FIRST.plusDays(1));
    assertThat(ipo.getListingDate()).isEqualTo(FIRST.plusDays(1));
    ipo.protectListingDate();
    merge(ipo, FIRST);
    assertThat(ipo.getListingDate()).isEqualTo(FIRST.plusDays(1));
    Ipo legacy = Ipo.builder().listingDate(FIRST).build();
    merge(legacy, FIRST.plusDays(1));
    assertThat(legacy.getListingDate()).isEqualTo(FIRST);
  }

  @Test
  void listingCorrectionClearsOpenPriceForRecollection() {
    Ipo ipo =
        Ipo.builder()
            .listingDate(FIRST)
            .lastImportedListingDate(FIRST)
            .listingOpenPrice(15_000L)
            .build();
    ipo.mergeSupplementalSchedule(
        null, null, null, FIRST.plusDays(1), "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
    assertThat(ipo.getListingDate()).isEqualTo(FIRST.plusDays(1));
    assertThat(ipo.getLastImportedListingDate()).isEqualTo(FIRST.plusDays(1));
    assertThat(ipo.getListingOpenPrice()).isNull();
  }

  @Test
  void refundMergesTrackedValueOrFillsNullButPreservesUnknownOrigin() {
    Ipo tracked = Ipo.builder().refundDate(FIRST).lastImportedRefundDate(FIRST).build();
    mergeSchedule(tracked, null, FIRST.plusDays(2), null);
    assertThat(tracked.getRefundDate()).isEqualTo(FIRST.plusDays(2));
    assertThat(tracked.getLastImportedRefundDate()).isEqualTo(FIRST.plusDays(2));

    Ipo legacy = Ipo.builder().refundDate(FIRST).build();
    mergeSchedule(legacy, null, FIRST.plusDays(3), null);
    assertThat(legacy.getRefundDate()).isEqualTo(FIRST);
    assertThat(legacy.getLastImportedRefundDate()).isNull();

    Ipo empty = Ipo.builder().build();
    mergeSchedule(empty, null, FIRST, null);
    assertThat(empty.getRefundDate()).isEqualTo(FIRST);
    assertThat(empty.getLastImportedRefundDate()).isEqualTo(FIRST);
  }

  @Test
  void persistBlocksMultipleActiveRowsForSameCorp() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo first = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    Ipo second = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(first, second));
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST)
            .build();
    assertThat(persistence.persist("corp", "name", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.BLOCKED_MULTIPLE);
    verify(repository, never()).saveAndFlush(any());
  }

  private void merge(Ipo ipo, LocalDate listing) {
    ipo.mergeSupplementalSchedule(
        null, null, null, listing, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
  }

  private void mergeSchedule(Ipo ipo, LocalDate allotment, LocalDate refund, LocalDate listing) {
    ipo.mergeSupplementalSchedule(
        null, allotment, refund, listing, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
  }

  @Test
  void withdrawalMatchesOfferingAndBlocksSameReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo ipo = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(ipo));
    var wrong =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST.plusDays(1))
            .build();
    assertThat(persistence.withdraw("corp", wrong)).isFalse();
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST)
            .build();
    assertThat(persistence.withdraw("corp", detail)).isTrue();
    assertThat(ipo.getStatus()).isEqualTo(RecordStatus.INACTIVE);
    assertThat(ipo.getDeletedAt()).isNotNull();
    when(repository.existsByCorpCodeAndWithdrawnReceiptNo("corp", "receipt")).thenReturn(true);
    assertThat(persistence.persist("corp", "name", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.SKIPPED_WITHDRAWN);
    // 수동 레코드 철회 불가 테스트: existsByCorpCodeAndWithdrawnReceiptNo 모킹 해제
    when(repository.existsByCorpCodeAndWithdrawnReceiptNo("corp", "receipt")).thenReturn(false);
    Ipo manual = Ipo.builder().subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(manual));
    assertThat(persistence.withdraw("corp", detail)).isFalse();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    var renewed =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("new-receipt")
            .subscriptionStartDate(FIRST.plusMonths(1))
            .subscriptionEndDate(FIRST.plusMonths(1).plusDays(1))
            .build();
    assertThat(persistence.persist("corp", "name", renewed, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.CREATED);
  }

  @Test
  void withdrawIsIdempotentWhenAlreadyWithdrawn() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo ipo = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(ipo));
    when(repository.existsByCorpCodeAndWithdrawnReceiptNo("corp", "receipt")).thenReturn(true);

    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST)
            .build();

    // 첫 호출: 이미 철회됨(true 반환, saveAndFlush 호출 안 함)
    assertThat(persistence.withdraw("corp", detail)).isTrue();
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void persistSkipsManualRecord() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo manual = Ipo.builder().subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(manual));
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .build();
    assertThat(persistence.persist("corp", "name", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.SKIPPED_MANUAL);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void persistSkipsIfStartDateMatchesExistingManual() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo manual =
        Ipo.builder()
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .ipoName("기존공모")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(repository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            FIRST, RecordStatus.ACTIVE))
        .thenReturn(List.of(manual));
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .build();
    assertThat(persistence.persist("corp", "기존공모", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.SKIPPED_MANUAL);
  }

  @Test
  void correctionWithoutLinkedOriginalReceiptIsHeld() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .sourceReceiptNo("old-receipt")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    var correction =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("new-receipt")
            .subscriptionStartDate(FIRST.plusDays(2))
            .subscriptionEndDate(FIRST.plusDays(3))
            .offeringShareCount(1_000_000L)
            .build();
    assertThat(persistence.persist("corp", "회사", correction, true, true, null))
        .isEqualTo(IpoSyncPersistence.Outcome.HELD_UNDETERMINED);
    assertThat(existing.getSubscriptionStartDate()).isEqualTo(FIRST);
    assertThat(existing.getSubscriptionEndDate()).isEqualTo(FIRST.plusDays(1));
    assertThat(existing.getOfferingShareCount()).isNull();
    // sourceReceiptNo는 덮어쓰지 않음
    assertThat(existing.getSourceReceiptNo()).isEqualTo("old-receipt");
  }

  @Test
  void undeterminedOfferingHoldsShareCount() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .sourceReceiptNo("receipt1")
            .offeringShareCount(500_000L)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    // 다른 접수번호, 정정 공시 아님 -> 구분 불가
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt2")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .offeringShareCount(800_000L)
            .build();
    assertThat(persistence.persist("corp", "회사", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.HELD_UNDETERMINED);
    // 주식 수만 갱신됨
    assertThat(existing.getOfferingShareCount()).isEqualTo(500_000L);
    // 가격 필드는 변경되지 않음 (기존 값 유지)
  }

  @Test
  void correctionWithoutSourceReceiptIsHeldForLegacyRecord() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient =
        mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            equitySecuritiesClient);
    // 레거시 행 (sourceReceiptNo 없음)
    Ipo legacy =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .sourceReceiptNo(null)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(legacy));
    var correction =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("new-receipt")
            .subscriptionStartDate(FIRST.plusDays(2))
            .subscriptionEndDate(FIRST.plusDays(3))
            .build();
    assertThat(persistence.persist("corp", "회사", correction, true, true, null))
        .isEqualTo(IpoSyncPersistence.Outcome.HELD_UNDETERMINED);
    // 레거시 행은 receipt 연결됨
    assertThat(legacy.getSourceReceiptNo()).isNull();
  }

  @Test
  void verifiedLatestFilingWithoutDs006UpdatesOnlyProvenTermsAndKeepsIpoId() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    Ipo existing =
        Ipo.builder()
            .id(42L)
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART")
            .sourceReceiptNo("20261008000649")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .lowerBandPrice(9_400L)
            .upperBandPrice(11_000L)
            .bandPriceSourceReceiptNo("20261008000649")
            .bandPriceParserVersion(2)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    DartOfferingTerms finalized =
        new DartOfferingTerms(
            "20261103000312",
            2,
            null,
            null,
            11_000L,
            FIRST.plusDays(2),
            FIRST.plusDays(3),
            null,
            null,
            null);

    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", "20261103000312", "20261008000649", null, finalized, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getId()).isEqualTo(42L);
    assertThat(existing.getSubscriptionStartDate()).isEqualTo(FIRST.plusDays(2));
    assertThat(existing.getSubscriptionEndDate()).isEqualTo(FIRST.plusDays(3));
    assertThat(existing.getFixedPrice()).isEqualTo(11_000L);
    assertThat(existing.getFixedPriceSourceReceiptNo()).isEqualTo("20261103000312");
    assertThat(existing.getLowerBandPrice()).isEqualTo(9_400L);
    assertThat(existing.getBandPriceSourceReceiptNo()).isEqualTo("20261008000649");
    verify(repository).saveAndFlush(existing);
  }

  @Test
  void missingDatesOrDifferentOriginalReceiptNeverCreatesOrMutatesIpo() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    DartOfferingTerms noDates =
        new DartOfferingTerms(
            "20261103000312", 2, null, null, 11_000L, null, null, null, null, null);
    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", "20261103000312", "20261008000649", null, noDates, null))
        .isEqualTo(IpoSyncPersistence.Outcome.HELD_UNDETERMINED);

    Ipo anotherRound =
        Ipo.builder().syncSource("OPENDART").sourceReceiptNo("20260929000347").build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(anotherRound));
    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", "20261103000312", "20261008000649", null, noDates, null))
        .isEqualTo(IpoSyncPersistence.Outcome.HELD_UNDETERMINED);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void trackedCorrectedDateCanWithdrawOriginalReceiptWithoutLosingHistory() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    Ipo corrected =
        Ipo.builder()
            .id(47L)
            .corpCode("corp")
            .syncSource("OPENDART")
            .sourceReceiptNo("20261008000649")
            .subscriptionStartDate(FIRST.plusDays(2))
            .lastImportedSubscriptionStartDate(FIRST.plusDays(2))
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(corrected));
    var original =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261008000649")
            .subscriptionStartDate(FIRST)
            .build();

    assertThat(persistence.withdraw("corp", original)).isTrue();
    assertThat(corrected.getId()).isEqualTo(47L);
    assertThat(corrected.getStatus()).isEqualTo(RecordStatus.INACTIVE);
    verify(repository).saveAndFlush(corrected);
  }

  @Test
  void manualDateCorrectionNeverAuthorizesWithdrawalByReceiptAlone() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    Ipo modified =
        Ipo.builder()
            .syncSource("OPENDART")
            .sourceReceiptNo("20261008000649")
            .subscriptionStartDate(FIRST.plusDays(2))
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(modified));
    var original =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261008000649")
            .subscriptionStartDate(FIRST)
            .build();

    assertThat(persistence.withdraw("corp", original)).isFalse();
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void supplementalCalendarCannotRevertVerifiedDartRefundButMayUpdateListing() {
    Ipo ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .refundDate(FIRST)
            .lastImportedRefundDate(FIRST)
            .lastVerifiedScheduleReceiptNo("20261103000312")
            .lastVerifiedRefundReceiptNo("20261103000312")
            .build();
    ipo.mergeSupplementalSchedule(
        null,
        null,
        FIRST.plusDays(1),
        FIRST.plusDays(8),
        "OPENDART+USTOCKPLUS",
        FIRST.atStartOfDay());

    assertThat(ipo.getRefundDate()).isEqualTo(FIRST);
    assertThat(ipo.getListingDate()).isEqualTo(FIRST.plusDays(8));
    assertThat(ipo.getLastVerifiedScheduleReceiptNo()).isEqualTo("20261103000312");
  }

  @Test
  void verifiedXmlShareCountCreatesNewIpoWithoutDs006() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    DartOfferingTerms original =
        new DartOfferingTerms(
            "20261008000649",
            2,
            9_400L,
            11_000L,
            null,
            FIRST,
            FIRST.plusDays(1),
            null,
            null,
            2_200_000L);

    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", "20261008000649", "20261008000649", null, original, null))
        .isEqualTo(IpoSyncPersistence.Outcome.CREATED);
    org.mockito.ArgumentCaptor<Ipo> created = org.mockito.ArgumentCaptor.forClass(Ipo.class);
    verify(repository).saveAndFlush(created.capture());
    assertThat(created.getValue().getOfferingShareCount()).isEqualTo(2_200_000L);
    assertThat(created.getValue().getLastImportedOfferingShareCount()).isEqualTo(2_200_000L);
    assertThat(created.getValue().getLastVerifiedShareReceiptNo()).isEqualTo("20261008000649");
  }

  @Test
  void ykmcRealHtmlExcerptLinksOriginalAndPersistsFixedPriceWithoutDs006() throws Exception {
    String originalReceipt = "20261008000649";
    String finalizedReceipt = "20261103000312";
    String html =
        Files.readString(
            Path.of("src/test/resources/dart/ykmc-20261103000312-offering-summary.html"));
    OpenDartDisclosureItem original =
        OpenDartDisclosureItem.builder()
            .corpCode("ykmc")
            .rceptNo(originalReceipt)
            .rceptDt("20261008")
            .reportNm("증권신고서(지분증권)")
            .build();
    OpenDartDisclosureItem finalized =
        OpenDartDisclosureItem.builder()
            .corpCode("ykmc")
            .rceptNo(finalizedReceipt)
            .rceptDt("20261103")
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .build();

    assertThat(DartFilingOrigin.originalSubmissionDate(html)).isEqualTo(LocalDate.of(2026, 10, 8));
    String verifiedOriginal =
        DartFilingOrigin.resolveOriginalReceipt(finalized, List.of(original, finalized), html);
    assertThat(verifiedOriginal).isEqualTo(originalReceipt);
    DartOfferingTerms terms = new DartOfferingTermsParser().parse(html, finalizedReceipt, true);
    assertThat(terms.fixedPrice()).isEqualTo(11_000L);

    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    Ipo existing =
        Ipo.builder()
            .id(507L)
            .corpCode("ykmc")
            .companyName("영광와이케이엠씨")
            .ipoName("영광와이케이엠씨")
            .syncSource("OPENDART")
            .sourceReceiptNo(originalReceipt)
            .lowerBandPrice(9_400L)
            .upperBandPrice(11_000L)
            .bandPriceSourceReceiptNo(originalReceipt)
            .bandPriceParserVersion(2)
            .subscriptionStartDate(LocalDate.of(2026, 11, 4))
            .subscriptionEndDate(LocalDate.of(2026, 11, 5))
            .lastImportedSubscriptionStartDate(LocalDate.of(2026, 11, 4))
            .lastImportedSubscriptionEndDate(LocalDate.of(2026, 11, 5))
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("ykmc", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    assertThat(
            persistence.persistVerifiedFiling(
                "ykmc", "영광와이케이엠씨", finalizedReceipt, verifiedOriginal, null, terms, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getId()).isEqualTo(507L);
    assertThat(existing.getSourceReceiptNo()).isEqualTo(originalReceipt);
    assertThat(existing.getFixedPrice()).isEqualTo(11_000L);
    assertThat(existing.getFixedPriceSourceReceiptNo()).isEqualTo(finalizedReceipt);
    assertThat(existing.getLowerBandPrice()).isEqualTo(9_400L);
    assertThat(existing.getBandPriceSourceReceiptNo()).isEqualTo(originalReceipt);
    verify(repository).saveAndFlush(existing);
  }

  @Test
  void dartSubscriptionOnlyCorrectionDoesNotBlockUstockPlusRefundCorrection() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    Ipo existing =
        Ipo.builder()
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART+USTOCKPLUS")
            .sourceReceiptNo("20261008000649")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .refundDate(FIRST.plusDays(5))
            .lastImportedRefundDate(FIRST.plusDays(5))
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    DartOfferingTerms subscriptionOnly =
        new DartOfferingTerms(
            "20261103000312",
            2,
            null,
            null,
            null,
            FIRST.plusDays(2),
            FIRST.plusDays(3),
            null,
            null,
            null);

    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", "20261103000312", "20261008000649", null, subscriptionOnly, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getLastVerifiedScheduleReceiptNo()).isEqualTo("20261103000312");
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isNull();
    assertThat(
            existing.mergeSupplementalSchedule(
                null, null, FIRST.plusDays(6), null, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay()))
        .isTrue();
    assertThat(existing.getRefundDate()).isEqualTo(FIRST.plusDays(6));
  }

  @Test
  void explicitDartRefundDateBlocksLaterUstockPlusRefundOverwrite() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    Ipo existing =
        Ipo.builder()
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART+USTOCKPLUS")
            .sourceReceiptNo("20261008000649")
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .refundDate(FIRST.plusDays(5))
            .lastImportedRefundDate(FIRST.plusDays(5))
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    DartOfferingTerms verifiedRefund =
        new DartOfferingTerms(
            "20261103000312",
            2,
            null,
            null,
            null,
            FIRST.plusDays(2),
            FIRST.plusDays(3),
            null,
            FIRST.plusDays(4),
            null);

    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", "20261103000312", "20261008000649", null, verifiedRefund, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getRefundDate()).isEqualTo(FIRST.plusDays(4));
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isEqualTo("20261103000312");
    assertThat(
            existing.mergeSupplementalSchedule(
                null, null, FIRST.plusDays(6), null, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay()))
        .isFalse();
    assertThat(existing.getRefundDate()).isEqualTo(FIRST.plusDays(4));
  }

  @Test
  void ordinaryDs006PersistCannotReplaceVerifiedDartRefundOnSameReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    // Exactly the same receipt must be eligible for schedule refresh, otherwise
    // an older-receipt guard could mask the provenance bug.
    String originalReceipt = "20261103000312";
    String verifiedReceipt = originalReceipt;
    LocalDate dartRefund = LocalDate.of(2026, 11, 9);
    LocalDate ds006Refund = LocalDate.of(2026, 11, 10);
    Ipo existing =
        Ipo.builder()
            .id(507L)
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART")
            .sourceReceiptNo(originalReceipt)
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .refundDate(dartRefund)
            .lastImportedRefundDate(dartRefund)
            .lastVerifiedScheduleReceiptNo(verifiedReceipt)
            .lastVerifiedRefundReceiptNo(verifiedReceipt)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo(originalReceipt)
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .pymd("2026년 11월 10일")
            .build();
    detail.parseDerivedFields();
    assertThat(detail.getRefundDate()).isEqualTo(ds006Refund);
    assertThat(existing.mayApplyVerifiedSchedule(detail.getRceptNo())).isTrue();

    // The original receipt still matches the offering, but its DS006 pymd
    // must not replace a refund verified from a later DART document.
    assertThat(persistence.persist("corp", "기업", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getRefundDate()).isEqualTo(dartRefund);
    assertThat(existing.getLastImportedRefundDate()).isEqualTo(dartRefund);
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isEqualTo(verifiedReceipt);
    assertThat(existing.getLastVerifiedScheduleReceiptNo()).isEqualTo(verifiedReceipt);
    assertThat(existing.getSourceReceiptNo()).isEqualTo(originalReceipt);
    assertThat(existing.getId()).isEqualTo(507L);

    // Idempotent resync must retain both the validated date and its provenance.
    assertThat(persistence.persist("corp", "기업", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getRefundDate()).isEqualTo(dartRefund);
    assertThat(existing.getLastImportedRefundDate()).isEqualTo(dartRefund);
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isEqualTo(verifiedReceipt);
    verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(existing);
  }

  @Test
  void ordinaryDs006PersistMayCorrectUnverifiedImportedRefund() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    String originalReceipt = "20261008000649";
    LocalDate oldRefund = LocalDate.of(2026, 11, 9);
    LocalDate newRefund = LocalDate.of(2026, 11, 10);
    Ipo existing =
        Ipo.builder()
            .id(507L)
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART")
            .sourceReceiptNo(originalReceipt)
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .refundDate(oldRefund)
            .lastImportedRefundDate(oldRefund)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo(originalReceipt)
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .pymd("2026년 11월 10일")
            .build();
    detail.parseDerivedFields();
    assertThat(detail.getRefundDate()).isEqualTo(newRefund);

    assertThat(persistence.persist("corp", "기업", detail, false, false, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getRefundDate()).isEqualTo(newRefund);
    assertThat(existing.getLastImportedRefundDate()).isEqualTo(newRefund);
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isNull();
    assertThat(existing.getId()).isEqualTo(507L);
    verify(repository).saveAndFlush(existing);
  }

  @Test
  void newerDs006RefundCannotReplaceOlderVerifiedDartRefundOrItsReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    LocalDate dartRefund = LocalDate.of(2026, 11, 9);
    LocalDate ds006Refund = LocalDate.of(2026, 11, 10);
    String originalReceipt = "20261008000649";
    String firstVerifiedReceipt = "20261030000616";
    String newerReceipt = "20261103000312";
    Ipo existing =
        Ipo.builder()
            .id(507L)
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART")
            .sourceReceiptNo(originalReceipt)
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .refundDate(dartRefund)
            .lastImportedRefundDate(dartRefund)
            .lastVerifiedScheduleReceiptNo(firstVerifiedReceipt)
            .lastVerifiedRefundReceiptNo(firstVerifiedReceipt)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    OpenDartEquitySecurityDetail newerDetail =
        OpenDartEquitySecurityDetail.builder().rceptNo(newerReceipt).pymd("2026년 11월 10일").build();
    newerDetail.parseDerivedFields();
    DartOfferingTerms newerWithoutRefund =
        new DartOfferingTerms(
            newerReceipt,
            2,
            null,
            null,
            null,
            FIRST.plusDays(2),
            FIRST.plusDays(3),
            null,
            null,
            null);

    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", newerReceipt, originalReceipt, newerDetail, newerWithoutRefund, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getRefundDate()).isEqualTo(dartRefund);
    assertThat(existing.getLastImportedRefundDate()).isEqualTo(dartRefund);
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isEqualTo(firstVerifiedReceipt);
    assertThat(existing.getLastVerifiedScheduleReceiptNo()).isEqualTo(newerReceipt);
    assertThat(existing.getId()).isEqualTo(507L);
    assertThat(
            existing.mergeSupplementalSchedule(
                null, null, ds006Refund, null, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay()))
        .isFalse();
    assertThat(existing.getRefundDate()).isEqualTo(dartRefund);
  }

  @Test
  void newerExplicitDartRefundUpdatesDateAndReceiptAtomicallyAfterDs006Fallback() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.systemUTC(),
            mock(OpenDartEquitySecuritiesClient.class));
    String originalReceipt = "20261008000649";
    String oldReceipt = "20261030000616";
    String ds006Receipt = "20261103000312";
    String latestReceipt = "20261104000123";
    LocalDate oldRefund = LocalDate.of(2026, 11, 9);
    LocalDate latestRefund = LocalDate.of(2026, 11, 12);
    Ipo existing =
        Ipo.builder()
            .id(507L)
            .corpCode("corp")
            .companyName("기업")
            .ipoName("기업")
            .syncSource("OPENDART")
            .sourceReceiptNo(originalReceipt)
            .subscriptionStartDate(FIRST)
            .subscriptionEndDate(FIRST.plusDays(1))
            .lastImportedSubscriptionStartDate(FIRST)
            .lastImportedSubscriptionEndDate(FIRST.plusDays(1))
            .refundDate(oldRefund)
            .lastImportedRefundDate(oldRefund)
            .lastVerifiedScheduleReceiptNo(oldReceipt)
            .lastVerifiedRefundReceiptNo(oldReceipt)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder().rceptNo(ds006Receipt).pymd("2026년 11월 10일").build();
    detail.parseDerivedFields();
    DartOfferingTerms missingRefund =
        new DartOfferingTerms(
            ds006Receipt,
            2,
            null,
            null,
            null,
            FIRST.plusDays(2),
            FIRST.plusDays(3),
            null,
            null,
            null);
    persistence.persistVerifiedFiling(
        "corp", "기업", ds006Receipt, originalReceipt, detail, missingRefund, null);

    DartOfferingTerms correctedRefund =
        new DartOfferingTerms(
            latestReceipt,
            2,
            null,
            null,
            null,
            FIRST.plusDays(4),
            FIRST.plusDays(5),
            null,
            latestRefund,
            null);
    assertThat(
            persistence.persistVerifiedFiling(
                "corp", "기업", latestReceipt, originalReceipt, null, correctedRefund, null))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getRefundDate()).isEqualTo(latestRefund);
    assertThat(existing.getLastImportedRefundDate()).isEqualTo(latestRefund);
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isEqualTo(latestReceipt);
    assertThat(existing.getId()).isEqualTo(507L);

    // Older filings and another run of the same newer filing must not roll back the record.
    persistence.persistVerifiedFiling(
        "corp", "기업", ds006Receipt, originalReceipt, detail, missingRefund, null);
    persistence.persistVerifiedFiling(
        "corp", "기업", latestReceipt, originalReceipt, null, correctedRefund, null);
    assertThat(existing.getRefundDate()).isEqualTo(latestRefund);
    assertThat(existing.getLastVerifiedRefundReceiptNo()).isEqualTo(latestReceipt);
    assertThat(existing.getSourceReceiptNo()).isEqualTo(originalReceipt);
  }
}
