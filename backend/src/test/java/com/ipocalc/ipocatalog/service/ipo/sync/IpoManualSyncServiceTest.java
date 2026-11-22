package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import com.ipocalc.ipocatalog.exception.OpenDartKeyMissingException;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSyncService;
import com.ipocalc.ipocatalog.service.ipo.krx.KrxListingAutoSyncService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiProperties;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link IpoManualSyncService} 단위 테스트(Mockito만). */
class IpoManualSyncServiceTest {

  private IpoSyncService sync;
  private IpoDetailSyncService detail;
  private IpoHistoricalListingService historical;
  private DartRightsIssueListingService rights;
  private KrxListingAutoSyncService kind;
  private OpenDartApiProperties props;
  private IpoSyncStateManager stateManager;
  private Executor executor;
  private IpoManualSyncService service;

  @BeforeEach
  void setUp() {
    sync = mock(IpoSyncService.class);
    detail = mock(IpoDetailSyncService.class);
    historical = mock(IpoHistoricalListingService.class);
    rights = mock(DartRightsIssueListingService.class);
    kind = mock(KrxListingAutoSyncService.class);
    props = new OpenDartApiProperties("dummy", "http://localhost");
    stateManager = mock(IpoSyncStateManager.class);
    executor = mock(Executor.class);
    doAnswer(
            inv -> {
              ((Runnable) inv.getArgument(0)).run();
              return null;
            })
        .when(executor)
        .execute(any(Runnable.class));
    service =
        new IpoManualSyncService(
            sync, detail, historical, rights, kind, props, stateManager, executor);
    ReflectionTestUtils.setField(service, "daysBack", 90);
    when(sync.runExclusively(any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
  }

  private static IpoSyncResult result(boolean aborted) {
    return IpoSyncResult.builder()
        .created(3)
        .updated(2)
        .skippedManual(1)
        .skippedNonIpo(4)
        .failureCount(1)
        .failures(new ArrayList<>())
        .scheduleMerged(6)
        .aborted(aborted)
        .build();
  }

  @Test
  void runsListThenDetailAndSummarizes() {
    when(sync.sync(90)).thenReturn(result(false));
    when(detail.syncRecent()).thenReturn(7);

    IpoManualSyncResult r = service.syncNow();

    assertThat(r).isEqualTo(new IpoManualSyncResult(3, 2, 5, 1, 6, 7, false));
  }

  @Test
  void abortedListSkipsDetail() {
    when(sync.sync(90)).thenReturn(result(true));

    IpoManualSyncResult r = service.syncNow();

    assertThat(r.aborted()).isTrue();
    assertThat(r.detailSaved()).isZero();
    verify(detail, never()).syncRecent();
  }

  @Test
  void missingKeyThrowsBeforeAnyCall() {
    service =
        new IpoManualSyncService(
            sync,
            detail,
            historical,
            rights,
            kind,
            new OpenDartApiProperties(" ", "http://x"),
            stateManager,
            executor);

    assertThatThrownBy(() -> service.syncNow()).isInstanceOf(OpenDartKeyMissingException.class);
    verify(sync, never()).sync(any(Integer.class));
  }

  @Test
  void defaultImportMergesSupplementalScheduleBeforeOfficialKindCheck() {
    when(sync.sync(90)).thenReturn(result(false));
    when(detail.syncRecent()).thenReturn(7);
    when(kind.isAutoApplyEnabled()).thenReturn(true);
    when(rights.backfill(any(), any()))
        .thenReturn(new DartRightsIssueListingService.Result(0, 0, 0));

    service.syncNow();

    var order = inOrder(sync, detail, rights, kind);
    order.verify(sync).sync(90);
    order.verify(detail).syncRecent();
    order.verify(rights).backfill(any(), any());
    order.verify(kind).reconcileRecent();
  }

  @Test
  void defaultImportSkipsKindWhenRightsIssueDocumentsFailed() {
    when(sync.sync(90)).thenReturn(result(false));
    when(detail.syncRecent()).thenReturn(7);
    when(kind.isAutoApplyEnabled()).thenReturn(true);
    when(rights.backfill(any(), any()))
        .thenReturn(new DartRightsIssueListingService.Result(0, 0, 1));

    service.syncNow();

    verify(rights).backfill(any(), any());
    verify(kind, never()).reconcileRecent();
  }

  @Test
  void rangeImportRunsRightsIssueAndSupplementalBackfillsBeforeOfficialKindCheck() {
    var month = java.time.YearMonth.of(2026, 11);
    when(sync.syncMonthWithoutSchedule(month)).thenReturn(result(false));
    when(historical.backfill(any(), any()))
        .thenReturn(new IpoHistoricalListingService.Result(0, java.util.List.of()));
    when(kind.isAutoApplyEnabled()).thenReturn(true);

    ReflectionTestUtils.invokeMethod(service, "runRangeSync", month, month);

    var order = inOrder(sync, historical, rights, kind);
    order.verify(sync).syncMonthWithoutSchedule(month);
    order.verify(historical).backfill(any(), any());
    order.verify(rights).backfill(any(), any());
    order.verify(kind).reconcileSubscriptionMonths(month, month, false);
  }

  @Test
  void busyPropagates() {
    org.mockito.Mockito.doThrow(new IpoSyncBusyException()).when(sync).runExclusively(any());

    assertThatThrownBy(() -> service.syncNow()).isInstanceOf(IpoSyncBusyException.class);
  }
}
