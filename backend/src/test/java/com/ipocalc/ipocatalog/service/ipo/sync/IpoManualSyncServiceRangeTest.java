package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncRangeRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.exception.IpoSyncRangeValidationException;
import com.ipocalc.ipocatalog.exception.OpenDartKeyMissingException;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSyncService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiProperties;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private OpenDartApiProperties openDartProperties;
  @Mock private IpoSyncStateManager syncStateManager;
  @Mock private Executor syncExecutor;
  @Mock private IpoRepository ipoRepository;

  private IpoManualSyncService service;

  @BeforeEach
  void setUp() {
    lenient().when(openDartProperties.isApiKeyMissing()).thenReturn(false);
    lenient().when(ipoSyncService.getIpoRepository()).thenReturn(ipoRepository);
    lenient().doAnswer(invocation -> {
      Runnable r = invocation.getArgument(0);
      r.run();
      return null;
    }).when(syncExecutor).execute(any(Runnable.class));

    service =
        new IpoManualSyncService(
            ipoSyncService,
            ipoDetailSyncService,
            openDartProperties,
            syncStateManager,
            syncExecutor);
    // daysBack 설정
    org.springframework.test.util.ReflectionTestUtils.setField(service, "daysBack", 90);
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