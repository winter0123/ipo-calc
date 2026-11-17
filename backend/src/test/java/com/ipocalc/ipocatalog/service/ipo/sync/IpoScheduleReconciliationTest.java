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
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
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
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
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
    ipo.mergeSupplementalSchedule(null, null, null, listing, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
  }

  private void mergeSchedule(Ipo ipo, LocalDate allotment, LocalDate refund, LocalDate listing) {
    ipo.mergeSupplementalSchedule(
        null, allotment, refund, listing, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
  }

  @Test
  void withdrawalMatchesOfferingAndBlocksSameReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
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
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
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
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
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
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
    Ipo manual = Ipo.builder()
        .subscriptionStartDate(FIRST)
        .subscriptionEndDate(FIRST.plusDays(1))
        .ipoName("기존공모")
        .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(repository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(FIRST, RecordStatus.ACTIVE))
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
  void correctionUpdatesExistingAutoRecord() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
    Ipo existing = Ipo.builder()
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
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getSubscriptionStartDate()).isEqualTo(FIRST.plusDays(2));
    assertThat(existing.getSubscriptionEndDate()).isEqualTo(FIRST.plusDays(3));
    assertThat(existing.getOfferingShareCount()).isEqualTo(1_000_000L);
    // sourceReceiptNo는 덮어쓰지 않음
    assertThat(existing.getSourceReceiptNo()).isEqualTo("old-receipt");
  }

  @Test
  void undeterminedOfferingUpdatesShareCountOnly() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
    Ipo existing = Ipo.builder()
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
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    // 주식 수만 갱신됨
    assertThat(existing.getOfferingShareCount()).isEqualTo(800_000L);
    // 가격 필드는 변경되지 않음 (기존 값 유지)
  }

  @Test
  void correctionWithoutSourceReceiptUpdatesLegacyRecord() {
    IpoRepository repository = mock(IpoRepository.class);
    OpenDartEquitySecuritiesClient equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC(), equitySecuritiesClient);
    // 레거시 행 (sourceReceiptNo 없음)
    Ipo legacy = Ipo.builder()
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
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    // 레거시 행은 receipt 연결됨
    assertThat(legacy.getSourceReceiptNo()).isEqualTo("new-receipt");
  }
}