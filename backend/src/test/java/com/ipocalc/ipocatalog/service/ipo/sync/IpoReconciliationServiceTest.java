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
            .rceptDt("20261028")
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
    when(persistence.persist(
            org.mockito.ArgumentMatchers.eq("02038432"),
            org.mockito.ArgumentMatchers.eq("심원"),
            org.mockito.ArgumentMatchers.eq(detail),
            org.mockito.ArgumentMatchers.eq(false),
            org.mockito.ArgumentMatchers.eq(true)))
        .thenReturn(IpoSyncPersistence.Outcome.UPDATED);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getRefreshed()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
    assertThat(result.getFailures()).isEmpty();
    verify(persistence).persist("02038432", "심원", detail, false, true);
  }

  @Test
  void refreshPrefersFinalizedReportAndUpdatesFixedPricePath() {
    Ipo row =
        Ipo.builder()
            .id(1L)
            .corpCode("02038432")
            .companyName("영광와이케이엠씨")
            .ipoName("영광와이케이엠씨")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .fixedPrice(9_400L)
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART")
            .sourceReceiptNo("20261001000001")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));

    OpenDartDisclosureItem original =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("영광와이케이엠씨")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("20261001000001")
            .rceptDt("20261001")
            .rm("")
            .build();
    OpenDartDisclosureItem finalized =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("영광와이케이엠씨")
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .rceptNo("20261020000002")
            .rceptDt("20261020")
            .rm("")
            .build();
    OpenDartDisclosureItem laterCorrection =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("영광와이케이엠씨")
            .reportNm("[기재정정]증권신고서(지분증권)")
            .rceptNo("20261025000003")
            .rceptDt("20261025")
            .rm("")
            .build();

    OpenDartEquitySecurityDetail originalDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261001000001")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .offerPrice(9_400L)
            .build();
    OpenDartEquitySecurityDetail finalizedDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261020000002")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .offerPrice(11_000L)
            .build();
    OpenDartEquitySecurityDetail correctionDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261025000003")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .offerPrice(11_000L)
            .build();

    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any()))
        .thenReturn(List.of(original, finalized, laterCorrection));
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any()))
        .thenReturn(List.of(originalDetail, finalizedDetail, correctionDetail));
    when(persistence.persist(
            org.mockito.ArgumentMatchers.eq("02038432"),
            org.mockito.ArgumentMatchers.eq("영광와이케이엠씨"),
            org.mockito.ArgumentMatchers.eq(finalizedDetail),
            org.mockito.ArgumentMatchers.eq(true),
            org.mockito.ArgumentMatchers.eq(true)))
        .thenReturn(IpoSyncPersistence.Outcome.UPDATED);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    // 더 늦은 기재정정보다 발행조건확정을 선택해야 함
    verify(persistence).persist("02038432", "영광와이케이엠씨", finalizedDetail, true, true);
    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getRefreshed()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
  }

  @Test
  void selectRefreshFilingPrefersFinalizedOverLaterCorrection() {
    OpenDartDisclosureItem finalized =
        OpenDartDisclosureItem.builder()
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .rceptNo("20261020000002")
            .rceptDt("20261020")
            .rm("")
            .build();
    OpenDartDisclosureItem laterCorrection =
        OpenDartDisclosureItem.builder()
            .reportNm("[기재정정]증권신고서(지분증권)")
            .rceptNo("20261025000003")
            .rceptDt("20261025")
            .rm("")
            .build();

    OpenDartDisclosureItem selected =
        IpoReconciliationService.selectRefreshFiling(List.of(laterCorrection, finalized));

    assertThat(selected).isSameAs(finalized);
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
