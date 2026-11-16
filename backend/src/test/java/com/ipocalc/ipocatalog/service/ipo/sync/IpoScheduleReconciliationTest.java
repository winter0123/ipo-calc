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
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC());
    Ipo first = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    Ipo second = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(first, second));
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(FIRST)
            .build();
    assertThat(persistence.persist("corp", "name", detail, false, false))
        .isEqualTo(IpoSyncPersistence.Outcome.BLOCKED_MULTIPLE);
    verify(repository, never()).saveAndFlush(any());
  }

  private void mergeSchedule(Ipo ipo, LocalDate allotment, LocalDate refund, LocalDate listing) {
    ipo.mergeSupplementalSchedule(
        null, allotment, refund, listing, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
  }

  @Test
  void withdrawalMatchesOfferingAndBlocksSameReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC());
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
    assertThat(persistence.persist("corp", "name", detail, false, false))
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
    assertThat(persistence.persist("corp", "name", renewed, false, false))
        .isEqualTo(IpoSyncPersistence.Outcome.CREATED);
  }

  @Test
  void withdrawIsIdempotentWhenAlreadyWithdrawn() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC());
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

  private void merge(Ipo ipo, LocalDate listing) {
    ipo.mergeSupplementalSchedule(
        null, null, null, listing, "OPENDART+USTOCKPLUS", FIRST.atStartOfDay());
  }

  private static IpoSyncPersistence newPersistence(IpoRepository repository) {
    return new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC());
  }

  @Test
  void persistHoldsPriceSharesCompanyNameAndReceiptWhenOfferingCannotBeDistinguished() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence = newPersistence(repository);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .companyName("기존회사")
            .fixedPrice(1_000_000L)
            .offeringShareCount(100L)
            .subscriptionStartDate(FIRST)
            .sourceReceiptNo("R1")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("R2")
            .corpName("새회사")
            .offeringShareCount(9_999L)
            .offerPrice(77_000L)
            .subscriptionStartDate(FIRST.plusDays(30))
            .build();

    assertThat(persistence.persist("corp", "새회사", detail, false, false))
        .isEqualTo(IpoSyncPersistence.Outcome.HELD_UNDETERMINED);
    verify(repository, never()).saveAndFlush(any());
    assertThat(existing.getCompanyName()).isEqualTo("기존회사");
    assertThat(existing.getFixedPrice()).isEqualTo(1_000_000L);
    assertThat(existing.getOfferingShareCount()).isEqualTo(100L);
    assertThat(existing.getSourceReceiptNo()).isEqualTo("R1");
  }

  @Test
  void persistUpdatesSameOfferingWithCorrectionEvidenceAndKeepsTrackedReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence = newPersistence(repository);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .companyName("기존회사")
            .subscriptionStartDate(FIRST)
            .sourceReceiptNo("R1")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    var correction =
        OpenDartEquitySecurityDetail.builder().rceptNo("R2").subscriptionStartDate(FIRST).build();

    assertThat(persistence.persist("corp", "기존회사", correction, false, true))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getSourceReceiptNo()).isEqualTo("R1");
    verify(repository).saveAndFlush(any());
  }

  @Test
  void persistUpdatesWhenReceiptMatches() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence = newPersistence(repository);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(FIRST)
            .sourceReceiptNo("R1")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("R1")
            .subscriptionStartDate(FIRST.plusDays(7))
            .build();

    assertThat(persistence.persist("corp", "name", detail, false, false))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
  }

  @Test
  void persistUpdatesLegacyRowBySameStartDateAndConnectsReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence = newPersistence(repository);
    Ipo existing = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    var detail =
        OpenDartEquitySecurityDetail.builder().rceptNo("R9").subscriptionStartDate(FIRST).build();

    assertThat(persistence.persist("corp", "name", detail, false, false))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getSourceReceiptNo()).isEqualTo("R9");
  }

  @Test
  void persistUpdatesCorrectionReportWhenSubscriptionDatesDiffer_singleActive() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence = newPersistence(repository);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .companyName("기존회사")
            .subscriptionStartDate(FIRST)
            .sourceReceiptNo("R1")
            .lastImportedSubscriptionStartDate(FIRST)
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    LocalDate newStart = FIRST.plusDays(30);
    var correction =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("R2")
            .subscriptionStartDate(newStart)
            .build();

    assertThat(persistence.persist("corp", "name", correction, false, true))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(existing.getSubscriptionStartDate()).isEqualTo(newStart);
    assertThat(existing.getSourceReceiptNo()).isEqualTo("R1");
    verify(repository).saveAndFlush(any());
  }

  @Test
  void repromotionCreatesNewRowAfterWithdrawal() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence = newPersistence(repository);
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(FIRST)
            .sourceReceiptNo("R1")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));
    var withdrawDetail =
        OpenDartEquitySecurityDetail.builder().rceptNo("R1").subscriptionStartDate(FIRST).build();
    assertThat(persistence.withdraw("corp", withdrawDetail)).isTrue();
    assertThat(existing.getStatus()).isEqualTo(RecordStatus.INACTIVE);

    // 재추진: 기존 건이 철회된 뒤에는 다른 receipt로 새 건이 생성되어야 한다
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    var repromotion =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("R2")
            .subscriptionStartDate(FIRST.plusMonths(1))
            .subscriptionEndDate(FIRST.plusMonths(1).plusDays(1))
            .build();
    assertThat(persistence.persist("corp", "name", repromotion, false, false))
        .isEqualTo(IpoSyncPersistence.Outcome.CREATED);
  }
}
