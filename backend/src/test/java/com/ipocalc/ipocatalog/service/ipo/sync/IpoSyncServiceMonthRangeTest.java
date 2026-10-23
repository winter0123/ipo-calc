package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import com.ipocalc.ipocatalog.repository.holiday.MarketHolidayRepository;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoScheduleMergeService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

/** IpoSyncService syncByMonthRange 테스트. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpoSyncServiceMonthRangeTest {

  @Mock private OpenDartCorpDisclosureClient corpDisclosureClient;
  @Mock private OpenDartEquitySecuritiesClient equitySecuritiesClient;
  @Mock private IpoRepository ipoRepository;
  @Mock private IpoScheduleMergeService scheduleMergeService;
  @Mock private MarketHolidayRepository marketHolidayRepository;

  private IpoSyncService service;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(
        LocalDateTime.of(2026, 10, 14, 12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(),
        java.time.ZoneId.systemDefault());
    TransactionTemplate transactionTemplate = new TransactionTemplate(
        mock(org.springframework.transaction.PlatformTransactionManager.class));

    when(marketHolidayRepository.findByYear(anyInt())).thenReturn(Collections.emptyList());
    
    service =
        new IpoSyncService(
            corpDisclosureClient,
            equitySecuritiesClient,
            ipoRepository,
            new BusinessDayCalculator(marketHolidayRepository),
            transactionTemplate,
            scheduleMergeService,
            fixedClock);
  }

  @Test
  void syncByMonthRange_singleMonth_callsSearchByDateRangeAndProcesses() {
    // given
    YearMonth month = YearMonth.of(2026, 1);
    OpenDartDisclosureItem item = createDisclosureItem("corp1", "Company 1", null);
    when(corpDisclosureClient.searchByDateRange(any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(item));

    OpenDartEquitySecurityDetail detail = createDetail(
        "corp1", "Company 1", LocalDate.of(2026, 1, 15));
    when(equitySecuritiesClient.getEquitySecurityDetail(
        anyString(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(detail));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull(
        anyString(), eq(RecordStatus.ACTIVE)))
        .thenReturn(Optional.empty());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
        any(LocalDate.class), eq(RecordStatus.ACTIVE)))
        .thenReturn(Collections.emptyList());
    when(scheduleMergeService.merge(any(LocalDate.class))).thenReturn(0);

    // when
    IpoSyncResult result = service.syncByMonthRange(month, month);

    // then
    assertThat(result.isAborted()).isFalse();
    assertThat(result.getCreated()).isEqualTo(1);
    verify(corpDisclosureClient).searchByDateRange(
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    // rangeStart = fromMonth 1일(2026-01-01), rangeEnd = toMonth 말일(2026-01-31)
    // because it's before today
    verify(equitySecuritiesClient).getEquitySecurityDetail(
        eq("corp1"),
        eq(LocalDate.of(2026, 1, 1)),
        eq(LocalDate.of(2026, 1, 31)));
    verify(scheduleMergeService).merge(LocalDate.of(2026, 10, 14));
  }

  @Test
  void syncByMonthRange_multipleMonths_processesEachMonth() {
    // given
    YearMonth from = YearMonth.of(2026, 1);
    YearMonth to = YearMonth.of(2026, 3);

    // 1월
    OpenDartDisclosureItem item1 = createDisclosureItem("corp1", "Company 1", null);
    // 2월
    OpenDartDisclosureItem item2 = createDisclosureItem("corp2", "Company 2", null);
    // 3월
    OpenDartDisclosureItem item3 = createDisclosureItem("corp3", "Company 3", null);

    when(corpDisclosureClient.searchByDateRange(
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .thenReturn(List.of(item1));
    when(corpDisclosureClient.searchByDateRange(
        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
        .thenReturn(List.of(item2));
    when(corpDisclosureClient.searchByDateRange(
        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .thenReturn(List.of(item3));

    OpenDartEquitySecurityDetail detail1 = createDetail(
        "corp1", "Company 1", LocalDate.of(2026, 1, 15));
    OpenDartEquitySecurityDetail detail2 = createDetail(
        "corp2", "Company 2", LocalDate.of(2026, 2, 15));
    OpenDartEquitySecurityDetail detail3 = createDetail(
        "corp3", "Company 3", LocalDate.of(2026, 3, 15));

    when(equitySecuritiesClient.getEquitySecurityDetail(
        eq("corp1"), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(detail1));
    when(equitySecuritiesClient.getEquitySecurityDetail(
        eq("corp2"),
        any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(detail2));
    when(equitySecuritiesClient.getEquitySecurityDetail(
        eq("corp3"),
        any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(detail3));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull(
        anyString(), eq(RecordStatus.ACTIVE)))
        .thenReturn(Optional.empty());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
        any(LocalDate.class), eq(RecordStatus.ACTIVE)))
        .thenReturn(Collections.emptyList());
    when(scheduleMergeService.merge(any(LocalDate.class))).thenReturn(0);

    // when
    IpoSyncResult result = service.syncByMonthRange(from, to);

    // then
    assertThat(result.isAborted()).isFalse();
    assertThat(result.getCreated()).isEqualTo(1); // 마지막 달만 반환
    verify(corpDisclosureClient, times(3)).searchByDateRange(
        any(LocalDate.class), any(LocalDate.class));
    verify(scheduleMergeService).merge(LocalDate.of(2026, 10, 14));
  }

  @Test
  void syncByMonthRange_authError_abortsAndReturns() {
    // given
    YearMonth month = YearMonth.of(2026, 1);
    when(corpDisclosureClient.searchByDateRange(any(LocalDate.class), any(LocalDate.class)))
        .thenThrow(new OpenDartAuthenticationException("011", "인증키 오류"));

    // when
    IpoSyncResult result = service.syncByMonthRange(month, month);

    // then
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getAbortReason()).contains("인증 오류");
    assertThat(result.isRetryable()).isFalse();
  }

  @Test
  void syncByMonthRange_rateLimit_abortsAndReturns() {
    // given
    YearMonth month = YearMonth.of(2026, 1);
    when(corpDisclosureClient.searchByDateRange(any(LocalDate.class), any(LocalDate.class)))
        .thenThrow(new OpenDartApiException("020", "요청 제한 초과"));

    // when
    IpoSyncResult result = service.syncByMonthRange(month, month);

    // then
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getAbortReason()).contains("요청 제한 초과");
    assertThat(result.isRetryable()).isFalse();
  }

  @Test
  void syncByMonthRange_generalApiError_abortsRetryable() {
    // given
    YearMonth month = YearMonth.of(2026, 1);
    when(corpDisclosureClient.searchByDateRange(any(LocalDate.class), any(LocalDate.class)))
        .thenThrow(new OpenDartApiException("800", "시스템 점검 중"));

    // when
    IpoSyncResult result = service.syncByMonthRange(month, month);

    // then
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getAbortReason()).contains("API 오류");
    assertThat(result.isRetryable()).isTrue();
  }

  @Test
  void syncByMonthRange_emptyCompanies_returnsEmptyResult() {
    // given
    YearMonth month = YearMonth.of(2026, 1);
    when(corpDisclosureClient.searchByDateRange(
        any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(Collections.emptyList());

    // when
    IpoSyncResult result = service.syncByMonthRange(month, month);

    // then
    assertThat(result.isAborted()).isFalse();
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
  }

  private OpenDartDisclosureItem createDisclosureItem(
      String corpCode, String corpName, String stockCode) {
    OpenDartDisclosureItem item = mock(OpenDartDisclosureItem.class);
    when(item.getCorpCode()).thenReturn(corpCode);
    when(item.getCorpName()).thenReturn(corpName);
    when(item.getStockCode()).thenReturn(stockCode);
    when(item.getRceptDt()).thenReturn("20260115");
    when(item.isEquitySecuritiesReport()).thenReturn(true);
    return item;
  }

  private OpenDartEquitySecurityDetail createDetail(
      String corpCode, String corpName, LocalDate startDate) {
    OpenDartEquitySecurityDetail detail = mock(OpenDartEquitySecurityDetail.class);
    when(detail.getCorpCode()).thenReturn(corpCode);
    when(detail.getCorpName()).thenReturn(corpName);
    when(detail.getSubscriptionStartDate()).thenReturn(startDate);
    when(detail.getSubscriptionEndDate()).thenReturn(startDate.plusDays(2));
    when(detail.isSubscriptionEndUnreadable()).thenReturn(false);
    when(detail.getOfferPrice()).thenReturn(10000L);
    when(detail.getOfferingShareCount()).thenReturn(1000000L);
    when(detail.getAllotmentDate()).thenReturn(startDate.plusDays(5));
    when(detail.getRefundDate()).thenReturn(startDate.plusDays(3));
    when(detail.getRceptNo()).thenReturn("20260115001");
    return detail;
  }
}
