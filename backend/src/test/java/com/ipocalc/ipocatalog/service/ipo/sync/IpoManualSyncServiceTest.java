package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import com.ipocalc.ipocatalog.exception.OpenDartKeyMissingException;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSyncService;
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
  private OpenDartApiProperties props;
  private IpoSyncStateManager stateManager;
  private Executor executor;
  private IpoManualSyncService service;

  @BeforeEach
  void setUp() {
    sync = mock(IpoSyncService.class);
    detail = mock(IpoDetailSyncService.class);
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
    service = new IpoManualSyncService(sync, detail, props, stateManager, executor);
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
            sync, detail, new OpenDartApiProperties(" ", "http://x"), stateManager, executor);

    assertThatThrownBy(() -> service.syncNow()).isInstanceOf(OpenDartKeyMissingException.class);
    verify(sync, never()).sync(any(Integer.class));
  }

  @Test
  void busyPropagates() {
    org.mockito.Mockito.doThrow(new IpoSyncBusyException()).when(sync).runExclusively(any());

    assertThatThrownBy(() -> service.syncNow()).isInstanceOf(IpoSyncBusyException.class);
  }
}
