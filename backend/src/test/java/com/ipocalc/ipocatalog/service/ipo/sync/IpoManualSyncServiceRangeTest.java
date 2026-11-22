package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncRangeRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import com.ipocalc.ipocatalog.exception.IpoSyncRangeValidationException;
import com.ipocalc.ipocatalog.exception.OpenDartKeyMissingException;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSyncService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** IpoManualSyncService 범위 동기화 테스트. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpoManualSyncServiceRangeTest {

  @Mock private IpoSyncService ipoSyncService;
  @Mock private IpoDetailSyncService ipoDetailSyncService;
  @Mock private IpoHistoricalListingService historicalListingService;
  @Mock private DartRightsIssueListingService rightsIssueListingService;
  @Mock private OpenDartApiProperties openDartProperties;
  @Mock private IpoSyncStateManager syncStateManager;
  @Mock private Executor syncExecutor;
  @Mock private IpoRepository ipoRepository;

  private IpoManualSyncService service;

  @BeforeEach
  void setUp() {
    lenient().when(openDartProperties.isApiKeyMissing()).thenReturn(false);
    lenient().when(ipoSyncService.getIpoRepository()).thenReturn(ipoRepository);
    lenient()
        .doAnswer(
            invocation -> {
              Runnable r = invocation.getArgument(0);
              r.run();
              return null;
            })
        .when(syncExecutor)
        .execute(any(Runnable.class));

    service =
        new IpoManualSyncService(
            ipoSyncService,
            ipoDetailSyncService,
            historicalListingService,
            rightsIssueListingService,
            openDartProperties,
            syncStateManager,
            syncExecutor);
    // daysBack 설정
    org.springframework.test.util.ReflectionTestUtils.setField(service, "daysBack", 90);
  }

  @Test
  void rangeUsesDartOnlyMonthsAndRunsOneHistoricalBackfill() {
    var req = new IpoSyncRangeRequest();
    req.setFromMonth("2026-09");
    req.setToMonth("2026-10");
    when(ipoSyncService.syncMonthWithoutSchedule(any()))
        .thenReturn(IpoSyncResult.builder().created(0).updated(0).aborted(false).build());
    var failedRange =
        new IpoHistoricalListingService.FailedRange(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    var backfill = new IpoHistoricalListingService.Result(3, java.util.List.of(failedRange));
    var additionalBackfill = new IpoHistoricalListingService.Result(1, java.util.List.of());
    when(historicalListingService.backfill(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)))
        .thenReturn(backfill);
    LocalDate additionalStart = YearMonth.of(2026, 11).atDay(1);
    LocalDate additionalEnd = YearMonth.of(2026, 11).atEndOfMonth();
    if (additionalEnd.isAfter(LocalDate.now())) {
      additionalEnd = LocalDate.now();
    }
    when(historicalListingService.backfill(
            additionalStart, additionalEnd, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)))
        .thenReturn(additionalBackfill);

    service.syncRange(req);

    verify(ipoSyncService).syncMonthWithoutSchedule(YearMonth.of(2026, 9));
    verify(ipoSyncService).syncMonthWithoutSchedule(YearMonth.of(2026, 10));
    verify(ipoSyncService, times(2)).syncMonthWithoutSchedule(any());
    verify(historicalListingService).backfill(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));
    verify(historicalListingService)
        .backfill(
            additionalStart, additionalEnd, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31));
    ArgumentCaptor<IpoHistoricalListingService.Result> resultCaptor =
        ArgumentCaptor.forClass(IpoHistoricalListingService.Result.class);
    verify(syncStateManager).recordListingBackfill(resultCaptor.capture());
    var merged = resultCaptor.getValue();
    assertThat(merged.listingBackfilled()).isEqualTo(4);
    assertThat(merged.failedRanges()).containsExactly(failedRange);
    verify(syncStateManager).completeRangeSync();
  }

  @Test
  void rangeSyncForCurrentMonthSkipsNextMonthAdditionalBackfill() {
    // given
    var req = new IpoSyncRangeRequest();
    req.setFromMonth("2026-11");
    req.setToMonth("2026-11");
    when(ipoSyncService.syncMonthWithoutSchedule(any()))
        .thenReturn(IpoSyncResult.builder().created(0).updated(0).aborted(false).build());
    var backfill = new IpoHistoricalListingService.Result(0, java.util.List.of());
    LocalDate listingEnd = LocalDate.of(2026, 11, 22);
    when(historicalListingService.backfill(LocalDate.of(2026, 11, 1), listingEnd))
        .thenReturn(backfill);

    // when
    service.syncRange(req);

    // then
    verify(historicalListingService).backfill(LocalDate.of(2026, 11, 1), listingEnd);
    verify(historicalListingService, never()).backfill(any(), any(), any(), any());
    verify(syncStateManager).recordListingBackfill(backfill);
    verify(syncStateManager).completeRangeSync();
  }

  @Test
  void syncRange_validRange_startsBackgroundAndReturnsRunning() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2026-01");
    request.setToMonth("2026-03");

    IpoSyncStatusResponse runningStatus =
        IpoSyncStatusResponse.builder()
            .state("RUNNING")
            .fromMonth("2026-01")
            .toMonth("2026-03")
            .monthsDone(0)
            .monthsTotal(3)
            .startedAt(LocalDateTime.now())
            .finishedAt(null)
            .result(
                IpoSyncStatusResponse.SyncResult.builder()
                    .created(0)
                    .updated(0)
                    .skipped(0)
                    .failed(0)
                    .scheduleMerged(0)
                    .detailSaved(0)
                    .aborted(false)
                    .build())
            .error(null)
            .build();
    when(syncStateManager.getStatus()).thenReturn(runningStatus);

    // when
    IpoSyncStatusResponse result = service.syncRange(request);

    // then
    assertThat(result.getState()).isEqualTo("RUNNING");
    assertThat(result.getFromMonth()).isEqualTo("2026-01");
    assertThat(result.getToMonth()).isEqualTo("2026-03");
    assertThat(result.getMonthsTotal()).isEqualTo(3);
    verify(syncStateManager).startRangeSync("2026-01", "2026-03", 3);
    verify(syncExecutor).execute(any(Runnable.class));
  }

  @Test
  void syncRange_emptyRequest_runsDefaultSync() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();

    IpoSyncStatusResponse runningStatus =
        IpoSyncStatusResponse.builder()
            .state("RUNNING")
            .fromMonth(null)
            .toMonth(null)
            .monthsDone(0)
            .monthsTotal(1)
            .startedAt(LocalDateTime.now())
            .finishedAt(null)
            .result(
                IpoSyncStatusResponse.SyncResult.builder()
                    .created(0)
                    .updated(0)
                    .skipped(0)
                    .failed(0)
                    .scheduleMerged(0)
                    .detailSaved(0)
                    .aborted(false)
                    .build())
            .error(null)
            .build();
    when(syncStateManager.getStatus()).thenReturn(runningStatus);

    // when
    IpoSyncStatusResponse result = service.syncRange(request);

    // then
    assertThat(result.getState()).isEqualTo("RUNNING");
    assertThat(result.getFromMonth()).isNull();
    assertThat(result.getToMonth()).isNull();
    assertThat(result.getMonthsTotal()).isEqualTo(1);
    verify(syncStateManager).startDefaultSync();
    verify(syncExecutor).execute(any(Runnable.class));
  }

  @Test
  void syncRange_missingKey_throws() {
    // given
    when(openDartProperties.isApiKeyMissing()).thenReturn(true);
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2026-01");
    request.setToMonth("2026-03");

    // when/then
    assertThatThrownBy(() -> service.syncRange(request))
        .isInstanceOf(OpenDartKeyMissingException.class);
  }

  @Test
  void syncRange_invalidFormat_throws() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2026/01");
    request.setToMonth("2026-03");

    // when/then
    assertThatThrownBy(() -> service.syncRange(request))
        .isInstanceOf(IpoSyncRangeValidationException.class)
        .hasMessageContaining("YYYY-MM");
  }

  @Test
  void syncRange_fromAfterTo_throws() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2026-05");
    request.setToMonth("2026-03");

    // when/then
    assertThatThrownBy(() -> service.syncRange(request))
        .isInstanceOf(IpoSyncRangeValidationException.class)
        .hasMessageContaining("fromMonth는 toMonth보다 이전이거나 같아야 합니다");
  }

  @Test
  void syncRange_toAfterThisMonth_throws() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2026-01");
    request.setToMonth(YearMonth.now().plusMonths(1).toString());

    // when/then
    assertThatThrownBy(() -> service.syncRange(request))
        .isInstanceOf(IpoSyncRangeValidationException.class)
        .hasMessageContaining("이번 달");
  }

  @Test
  void syncRange_fromBefore2015_throws() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2014-12");
    request.setToMonth("2026-03");

    // when/then
    assertThatThrownBy(() -> service.syncRange(request))
        .isInstanceOf(IpoSyncRangeValidationException.class)
        .hasMessageContaining("2015-01");
  }

  @Test
  void syncRange_exceeds24Months_throws() {
    // given
    IpoSyncRangeRequest request = new IpoSyncRangeRequest();
    request.setFromMonth("2024-01");
    request.setToMonth("2026-03"); // 27개월

    // when/then
    assertThatThrownBy(() -> service.syncRange(request))
        .isInstanceOf(IpoSyncRangeValidationException.class)
        .hasMessageContaining("24개월");
  }

  @Test
  void getSyncStatus_returnsStateManagerStatus() {
    // given
    IpoSyncStatusResponse status =
        IpoSyncStatusResponse.builder()
            .state("DONE")
            .fromMonth("2026-01")
            .toMonth("2026-03")
            .monthsDone(3)
            .monthsTotal(3)
            .startedAt(LocalDateTime.now().minusMinutes(10))
            .finishedAt(LocalDateTime.now())
            .result(
                IpoSyncStatusResponse.SyncResult.builder()
                    .created(10)
                    .updated(5)
                    .skipped(2)
                    .failed(1)
                    .scheduleMerged(3)
                    .detailSaved(8)
                    .aborted(false)
                    .build())
            .error(null)
            .build();
    when(syncStateManager.getStatus()).thenReturn(status);

    // when
    IpoSyncStatusResponse result = service.getSyncStatus();

    // then
    assertThat(result.getState()).isEqualTo("DONE");
    assertThat(result.getMonthsDone()).isEqualTo(3);
    assertThat(result.getResult().getCreated()).isEqualTo(10);
  }
}
