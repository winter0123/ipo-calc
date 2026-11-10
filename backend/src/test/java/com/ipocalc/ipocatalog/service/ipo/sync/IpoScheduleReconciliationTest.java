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
import java.util.Optional;
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
  void withdrawalMatchesOfferingAndBlocksSameReceipt() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC());
    Ipo ipo = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(ipo));
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
    assertThat(persistence.persist("corp", "name", detail, false))
        .isEqualTo(IpoSyncPersistence.Outcome.SKIPPED_WITHDRAWN);
    // 수동 레코드 철회 불가 테스트: existsByCorpCodeAndWithdrawnReceiptNo 모킹 해제
    when(repository.existsByCorpCodeAndWithdrawnReceiptNo("corp", "receipt")).thenReturn(false);
    Ipo manual = Ipo.builder().subscriptionStartDate(FIRST).build();
    when(repository.findByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(manual));
    assertThat(persistence.withdraw("corp", detail)).isFalse();
    when(repository.findByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(Optional.empty());
    var renewed =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("new-receipt")
            .subscriptionStartDate(FIRST.plusMonths(1))
            .subscriptionEndDate(FIRST.plusMonths(1).plusDays(1))
            .build();
    assertThat(persistence.persist("corp", "name", renewed, false))
        .isEqualTo(IpoSyncPersistence.Outcome.CREATED);
  }

  @Test
  void withdrawIsIdempotentWhenAlreadyWithdrawn() {
    IpoRepository repository = mock(IpoRepository.class);
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(repository, mock(BusinessDayCalculator.class), Clock.systemUTC());
    Ipo ipo = Ipo.builder().syncSource("OPENDART").subscriptionStartDate(FIRST).build();
    when(repository.findByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(ipo));
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
}
