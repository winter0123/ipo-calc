package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DART 확인 종목코드의 신규 저장, 기존 IPO 복구 및 충돌 보류를 검증한다. */
class IpoDartStockCodePersistenceTest {
  private static final String CORP = "01025617";
  private static final String RECEIPT = "20261109000163";
  private IpoRepository repository;
  private IpoSyncPersistence persistence;

  @BeforeEach
  void setUp() {
    repository = mock(IpoRepository.class);
    persistence =
        new IpoSyncPersistence(
            repository,
            mock(BusinessDayCalculator.class),
            Clock.fixed(
                LocalDate.of(2026, 11, 20).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")),
            mock(OpenDartEquitySecuritiesClient.class));
  }

  private OpenDartEquitySecurityDetail detail() {
    return OpenDartEquitySecurityDetail.builder()
        .rceptNo(RECEIPT)
        .subscriptionStartDate(LocalDate.of(2026, 11, 10))
        .subscriptionEndDate(LocalDate.of(2026, 11, 11))
        .build();
  }

  private Ipo oldRow() {
    return Ipo.builder()
        .id(111L)
        .corpCode(CORP)
        .ipoName("하나에어로")
        .companyName("하나에어로")
        .subscriptionStartDate(LocalDate.of(2026, 11, 10))
        .subscriptionEndDate(LocalDate.of(2026, 11, 11))
        .sourceReceiptNo(RECEIPT)
        .syncSource("OPENDART")
        .status(RecordStatus.ACTIVE)
        .build();
  }

  @Test
  void backfillsExistingIpoWithoutChangingIdOrNameAndIsIdempotent() {
    Ipo row = oldRow();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(CORP, RecordStatus.ACTIVE))
        .thenReturn(List.of(row));

    assertThat(persistence.persist(CORP, "하나에어로", detail(), true, false, null, "A271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(row.getId()).isEqualTo(111L);
    assertThat(row.getIpoName()).isEqualTo("하나에어로");
    assertThat(row.getStockCode()).isEqualTo("271160");

    assertThat(persistence.persist(CORP, "하나에어로", detail(), true, false, null, "271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(row.getStockCode()).isEqualTo("271160");
    verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(row);
  }

  @Test
  void doesNotChangeManualRow() {
    Ipo row = Ipo.builder().id(111L).corpCode(CORP).status(RecordStatus.ACTIVE).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(CORP, RecordStatus.ACTIVE))
        .thenReturn(List.of(row));

    assertThat(persistence.persist(CORP, "하나에어로", detail(), true, false, null, "271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.SKIPPED_MANUAL);
    assertThat(row.getStockCode()).isNull();
    verify(repository, never()).saveAndFlush(row);
  }

  @Test
  void stockCodeConflictStillUpdatesVerifiedOfferingWithoutChangingCode() {
    Ipo row = oldRow();
    Ipo other = Ipo.builder().id(222L).stockCode("271160").status(RecordStatus.ACTIVE).build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(CORP, RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    when(repository.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(other));

    DartOfferingTerms terms = verifiedTerms();
    assertThat(
            persistence.persistVerifiedFiling(
                CORP, "하나에어로", RECEIPT, RECEIPT, detail(), terms, null, "271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(row.getStockCode()).isNull();
    assertThat(row.getFixedPrice()).isEqualTo(5_700L);
    verify(repository).saveAndFlush(row);
  }

  private DartOfferingTerms verifiedTerms() {
    return new DartOfferingTerms(
        RECEIPT,
        DartOfferingTerms.PARSER_VERSION,
        null,
        null,
        5_700L,
        LocalDate.of(2026, 11, 10),
        LocalDate.of(2026, 11, 11),
        null,
        LocalDate.of(2026, 11, 18),
        null);
  }

  @Test
  void conflictingExistingCodePreservesCodeButUpdatesVerifiedPrice() {
    Ipo row =
        Ipo.builder()
            .id(111L)
            .corpCode(CORP)
            .stockCode("999999")
            .sourceReceiptNo(RECEIPT)
            .subscriptionStartDate(LocalDate.of(2026, 11, 10))
            .subscriptionEndDate(LocalDate.of(2026, 11, 11))
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART")
            .build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(CORP, RecordStatus.ACTIVE))
        .thenReturn(List.of(row));

    assertThat(
            persistence.persistVerifiedFiling(
                CORP, "하나에어로", RECEIPT, RECEIPT, detail(), verifiedTerms(), null, "271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(row.getStockCode()).isEqualTo("999999");
    assertThat(row.getFixedPrice()).isEqualTo(5_700L);
    verify(repository).saveAndFlush(row);
  }

  @Test
  void normalSyncConflictUpdatesScheduleWithoutReplacingStockCode() {
    Ipo row = oldRow();
    Ipo other = Ipo.builder().id(222L).stockCode("271160").build();
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(CORP, RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    when(repository.findByStockCodeAndStatusAndDeletedAtIsNull("271160", RecordStatus.ACTIVE))
        .thenReturn(Optional.of(other));

    assertThat(
            persistence.persist(CORP, "하나에어로", detail(), false, false, verifiedTerms(), "271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.UPDATED);
    assertThat(row.getStockCode()).isNull();
    assertThat(row.getFixedPrice()).isEqualTo(5_700L);
    verify(repository).saveAndFlush(row);
  }

  @Test
  void createsNewIpoWithVerifiedCode() {
    when(repository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(CORP, RecordStatus.ACTIVE))
        .thenReturn(List.of());
    assertThat(persistence.persist(CORP, "하나에어로", detail(), true, false, null, "271160"))
        .isEqualTo(IpoSyncPersistence.Outcome.CREATED);
    var captor = org.mockito.ArgumentCaptor.forClass(Ipo.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getStockCode()).isEqualTo("271160");
  }
}
