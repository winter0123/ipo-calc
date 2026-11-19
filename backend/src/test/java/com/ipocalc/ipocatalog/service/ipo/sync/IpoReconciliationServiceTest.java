package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTermsParser;
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
  private DartIpoFilingClient dartIpoFilingClient;
  private DartOfferingTermsParser offeringTermsParser;
  private IpoReconciliationService service;

  @BeforeEach
  void setUp() {
    corpDisclosureClient = mock(OpenDartCorpDisclosureClient.class);
    equitySecuritiesClient = mock(OpenDartEquitySecuritiesClient.class);
    ipoRepository = mock(IpoRepository.class);
    persistence = mock(IpoSyncPersistence.class);
    transactionTemplate = mock(TransactionTemplate.class);
    dartIpoFilingClient = mock(DartIpoFilingClient.class);
    offeringTermsParser = mock(DartOfferingTermsParser.class);
    doAnswer(
            invocation -> {
              org.springframework.transaction.support.TransactionCallback<?> callback =
                  invocation.getArgument(0);
              return callback.doInTransaction(null);
            })
        .when(transactionTemplate)
        .execute(any());
    // 원문 다운로드/파싱 기본 모킹: 빈 응답 반환 (가격 파싱 없이 진행)
    when(dartIpoFilingClient.downloadDocument(anyString())).thenReturn(java.util.Optional.empty());
    when(offeringTermsParser.parse(anyString(), anyString(), anyBoolean())).thenReturn(null);
    service =
        new IpoReconciliationService(
            corpDisclosureClient,
            equitySecuritiesClient,
            dartIpoFilingClient,
            offeringTermsParser,
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
            org.mockito.ArgumentMatchers.eq(true),
            org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(IpoSyncPersistence.Outcome.UPDATED);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getRefreshed()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
    assertThat(result.getFailures()).isEmpty();
    verify(persistence)
        .persist(eq("02038432"), eq("심원"), eq(detail), eq(false), eq(true), isNull());
  }

  @Test
  void refreshSelectsLatestFilingByDateAndUpdatesIfSameOffering() {
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

    // 최신 공시는 기재정정(2026-10-25), 발행조건확정(2026-10-20), 원공시(2026-10-01) 순
    // selectRefreshFiling은 접수일자·접수번호 기준 최신 공시를 선택하므로 기재정정이 선택됨
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
    // 최신 공시(기재정정)의 상세가 선택되어 persist 호출됨 (finalizedReport=false, correctionReport=true)
    when(persistence.persist(
            org.mockito.ArgumentMatchers.eq("02038432"),
            org.mockito.ArgumentMatchers.eq("영광와이케이엠씨"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(IpoSyncPersistence.Outcome.UPDATED);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    // 접수일자 기준 최신 공시(기재정정)가 선택됨
    verify(persistence)
        .persist(eq("02038432"), eq("영광와이케이엠씨"), any(), anyBoolean(), anyBoolean(), isNull());
    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getRefreshed()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
  }

  @Test
  void selectRefreshFilingSelectsLaterCorrectionOverFinalized() {
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

    assertThat(selected).isSameAs(laterCorrection);
  }

  @Test
  void selectRefreshFilingSelectsLaterRegularOverCorrection() {
    // 발행조건확정이 없고 정정공시가 있는 경우: 정정공시가 최신 일반 공시보다 우선 선택됨
    OpenDartDisclosureItem correction =
        OpenDartDisclosureItem.builder()
            .reportNm("[기재정정]증권신고서(지분증권)")
            .rceptNo("20261020000002")
            .rceptDt("20261020")
            .rm("")
            .build();
    OpenDartDisclosureItem laterRegular =
        OpenDartDisclosureItem.builder()
            .reportNm("증권신고서(지분증권)")
            .rceptNo("20261025000003")
            .rceptDt("20261025")
            .rm("")
            .build();

    OpenDartDisclosureItem selected =
        IpoReconciliationService.selectRefreshFiling(List.of(laterRegular, correction));

    assertThat(selected).isSameAs(laterRegular);
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

  @Test
  void newerUnrelatedOfferingDoesNotHideOriginalRoundRefresh() {
    String oldReceipt = "20261012000149";
    String newReceipt = "20261110000111";
    Ipo originalRow = activeAutoRow("02038432", LocalDate.of(2026, 11, 5), oldReceipt);
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(originalRow));
    OpenDartDisclosureItem repromotion =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("증권신고서(지분증권)")
            .rceptDt("20261110")
            .rceptNo(newReceipt)
            .build();
    OpenDartDisclosureItem original =
        OpenDartDisclosureItem.builder()
            .corpCode("02038432")
            .corpName("심원")
            .reportNm("증권신고서(지분증권)")
            .rceptDt("20261012")
            .rceptNo(oldReceipt)
            .build();
    OpenDartEquitySecurityDetail oldDetail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("02038432")
            .rceptNo(oldReceipt)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchAllByDateRange(any(), any(), any()))
        .thenReturn(List.of(repromotion, original));
    when(equitySecuritiesClient.getEquitySecurityDetail(any(), any(), any()))
        .thenReturn(List.of(oldDetail));
    when(dartIpoFilingClient.downloadDocument(oldReceipt))
        .thenReturn(java.util.Optional.of("<TABLE>verified original</TABLE>"));
    DartOfferingTerms verified =
        new DartOfferingTerms(
            oldReceipt, 2, 9_400L, 11_000L, null, null, null, null, null, null);
    when(offeringTermsParser.parse(anyString(), eq(oldReceipt), eq(false)))
        .thenReturn(verified);
    when(persistence.persistVerifiedFiling(
            eq("02038432"),
            eq("심원"),
            eq(oldReceipt),
            eq(oldReceipt),
            eq(oldDetail),
            eq(verified),
            isNull()))
        .thenReturn(IpoSyncPersistence.Outcome.UPDATED);

    IpoReconcileResult result = service.reconcileActiveOfferings();

    assertThat(result.getSucceeded()).isEqualTo(1);
    assertThat(result.getRefreshed()).isEqualTo(1);
    verify(dartIpoFilingClient).downloadDocument(newReceipt);
    verify(dartIpoFilingClient).downloadDocument(oldReceipt);
  }

}
