package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link IpoReconciliationService}의 호출 상한·철회 매칭 규칙을 목(mock)으로 검증한다. */
class IpoReconciliationServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(
          LocalDateTime.of(2026, 11, 10, 4, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
          ZoneId.of("Asia/Seoul"));

  private OpenDartCorpDisclosureClient corpDisclosureClient;
  private OpenDartEquitySecuritiesClient equitySecuritiesClient;
  private IpoRepository ipoRepository;
  private IpoSyncPersistence persistence;
  private TransactionTemplate transactionTemplate;
  private IpoReconciliationService service;

  @BeforeEach
  void setUp() {
    corpDisclosureClient = mock(OpenDartCorpDisclosureClient.class);
    equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    ipoRepository = mock(IpoRepository.class);
    persistence = mock(IpoSyncPersistence.class);
    transactionTemplate =
        new TransactionTemplate(
            mock(org.springframework.transaction.PlatformTransactionManager.class));
    service =
        new IpoReconciliationService(
            corpDisclosureClient,
            equitySecuritiesClient,
            ipoRepository,
            persistence,
            transactionTemplate,
            FIXED_CLOCK);
  }

  private Ipo activeAutoRow(String corpCode, LocalDate start, String receiptNo) {
    return Ipo.builder()
        .id(1L)
        .corpCode(corpCode)
        .companyName("심원")
        .ipoName("심원")
        .subscriptionStartDate(start)
        .subscriptionEndDate(start.plusDays(1))
        .status(RecordStatus.ACTIVE)
        .syncSource("OPENDART")
        .sourceReceiptNo(receiptNo)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  @Test
  void eachCompanyIsQueriedExactlyOnceForDs001AndDs006() {
    Ipo first = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    Ipo second = activeAutoRow("08213333", LocalDate.of(2025, 5, 1), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(first, second));
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any())).thenReturn(List.of());
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any())).thenReturn(List.of());

    IpoReconcileResult result = service.reconcileActiveOfferings();

    verify(corpDisclosureClient, times(1))
        .searchAllByDateRange(org.mockito.ArgumentMatchers.eq("02038432"), any(), any());
    verify(corpDisclosureClient, times(1))
        .searchAllByDateRange(org.mockito.ArgumentMatchers.eq("08213333"), any(), any());
    verify(equitySecuritiesClient, times(1))
        .getEquitySecurityDetail(org.mockito.ArgumentMatchers.eq("02038432"), any(), any());
    verify(equitySecuritiesClient, times(1))
        .getEquitySecurityDetail(org.mockito.ArgumentMatchers.eq("08213333"), any(), any());
    assertThat(result.getTotalTargets()).isEqualTo(2);
    assertThat(result.getSucceeded()).isZero();
    assertThat(result.getFailed()).isEqualTo(2);
  }

  @Test
  void emptyDs006ResponseIsNotWithdrawnAndOnlyFailureRecorded() {
    Ipo row = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    OpenDartDisclosureItem withdrawal =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("20261012000149")
            .rm("철")
            .build();
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any()))
        .thenReturn(List.of(withdrawal));
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any())).thenReturn(List.of());

    IpoReconcileResult result = service.reconcileActiveOfferings();

    verify(persistence, never()).withdraw(any(), any());
    assertThat(result.getFailed()).isEqualTo(1);
    assertThat(result.getFailures())
        .anySatisfy(failure -> assertThat(failure.getReason()).contains("원공시 DS006 상세 없음"));
  }

  @Test
  void legacyRowWithMatchingSubscriptionStartDateIsWithdrawn() {
    Ipo row = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    OpenDartDisclosureItem withdrawal =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("20261012000149")
            .rm("철")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .corpCode("02038432")
            .corpName("심원")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any()))
        .thenReturn(List.of(withdrawal));
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any()))
        .thenReturn(List.of(detail));
    when(persistence.withdraw("02038432", detail)).thenReturn(true);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    verify(persistence).withdraw("02038432", detail);
    assertThat(result.getWithdrawn()).isEqualTo(1);
    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
  }

  @Test
  void legacyRowWithMismatchedSubscriptionStartDateIsHeldBack() {
    Ipo row = activeAutoRow("02038432", LocalDate.of(2026, 11, 20), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    OpenDartDisclosureItem withdrawal =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("20261012000149")
            .rm("철")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .corpCode("02038432")
            .corpName("심원")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any()))
        .thenReturn(List.of(withdrawal));
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any()))
        .thenReturn(List.of(detail));

    IpoReconcileResult result = service.reconcileActiveOfferings();

    verify(persistence, never()).withdraw(any(), any());
    assertThat(result.getFailed()).isEqualTo(1);
    assertThat(result.getFailures())
        .anySatisfy(failure -> assertThat(failure.getReason()).contains("청약 시작일 불일치"));
  }

  @Test
  void duplicateActiveRowsBlockBeforeAnyDartCall() {
    Ipo first = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    Ipo second = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(first, second));

    IpoReconcileResult result = service.reconcileActiveOfferings();

    verify(corpDisclosureClient, never()).searchAllByDateRange(any(), any(), any());
    verify(equitySecuritiesClient, never()).getEquitySecurityDetail(any(), any(), any());
    assertThat(result.getFailed()).isEqualTo(2);
    assertThat(result.getFailures())
        .allSatisfy(failure -> assertThat(failure.getReason()).contains("복수"));
  }

  @Test
  void runtimeExceptionInOneCompanyDoesNotStopOtherCompanies() {
    Ipo first = activeAutoRow("08213333", LocalDate.of(2025, 5, 1), null);
    Ipo second = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(first, second));
    when(corpDisclosureClient.searchAllByDateRange(
            org.mockito.ArgumentMatchers.eq("08213333"), any(), any()))
        .thenThrow(new RuntimeException("boom"));
    OpenDartDisclosureItem withdrawal =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("20261012000149")
            .rm("철")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .corpCode("02038432")
            .corpName("심원")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchAllByDateRange(
            org.mockito.ArgumentMatchers.eq("02038432"), any(), any()))
        .thenReturn(List.of(withdrawal));
    when(equitySecuritiesClient.getEquitySecurityDetail(
            org.mockito.ArgumentMatchers.eq("02038432"), any(), any()))
        .thenReturn(List.of(detail));
    when(persistence.withdraw("02038432", detail)).thenReturn(true);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getFailed()).isEqualTo(1);
    assertThat(result.getWithdrawn()).isEqualTo(1);
    assertThat(result.getFailures())
        .anySatisfy(failure -> assertThat(failure.getReason()).contains("RuntimeException"));
  }

  @Test
  void validNonWithdrawalFilingConfirmsSuccess() {
    Ipo row = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    OpenDartDisclosureItem correction =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("[기재정정]증권신고서(지분증권)")
            .rceptNo("20261028000200")
            .rm("")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261028000200")
            .corpCode("02038432")
            .corpName("심원")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any()))
        .thenReturn(List.of(correction));
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any()))
        .thenReturn(List.of(detail));

    IpoReconcileResult result = service.reconcileActiveOfferings();

    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
    assertThat(result.getFailures()).isEmpty();
  }

  @Test
  void emptyDs001ResponseCountsAsUnverifiedFailure_notSuccess() {
    Ipo row = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), null);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any())).thenReturn(List.of());
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any())).thenReturn(List.of());

    IpoReconcileResult result = service.reconcileActiveOfferings();

    assertThat(result.getSucceeded()).isZero();
    assertThat(result.getFailed()).isEqualTo(1);
    assertThat(result.getFailures())
        .anySatisfy(failure -> assertThat(failure.getReason()).contains("확인 불가"));
  }
}
