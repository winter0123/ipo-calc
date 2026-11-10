package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link PostDeployReconciliationRunner}의 스킵·실행·무해 종료 분기를 검증한다. */
class PostDeployReconciliationRunnerTest {

  private IpoSyncRunRecorder recorder;
  private IpoSyncService syncService;
  private final Executor directExecutor = Runnable::run;

  @BeforeEach
  void setUp() {
    recorder = mock(IpoSyncRunRecorder.class);
    syncService = mock(IpoSyncService.class);
  }

  @Test
  void skipsWhenRecoverAlreadyCompleted() {
    when(recorder.isRecoverComplete()).thenReturn(true);

    new PostDeployReconciliationRunner(recorder, syncService, directExecutor).onApplicationReady();

    verify(syncService, never()).recoverAfterDeploy();
  }

  @Test
  void runsRecoverAsynchronouslyWhenNotCompleted() {
    when(recorder.isRecoverComplete()).thenReturn(false);

    new PostDeployReconciliationRunner(recorder, syncService, directExecutor).onApplicationReady();

    verify(syncService).recoverAfterDeploy();
  }

  @Test
  void busyRecoverEndsSilentlyWithoutPropagating() {
    when(recorder.isRecoverComplete()).thenReturn(false);
    when(syncService.recoverAfterDeploy()).thenThrow(new IpoSyncBusyException());

    PostDeployReconciliationRunner runner =
        new PostDeployReconciliationRunner(recorder, syncService, directExecutor);

    assertThatCode(runner::onApplicationReady).doesNotThrowAnyException();
  }

  @Test
  void unexpectedRecoverFailureEndsSilentlyWithoutPropagating() {
    when(recorder.isRecoverComplete()).thenReturn(false);
    when(syncService.recoverAfterDeploy()).thenThrow(new IllegalStateException("boom"));

    PostDeployReconciliationRunner runner =
        new PostDeployReconciliationRunner(recorder, syncService, directExecutor);

    assertThatCode(runner::onApplicationReady).doesNotThrowAnyException();
  }

  @Test
  void executorRejectionEndsSilentlyWithoutPropagating() {
    when(recorder.isRecoverComplete()).thenReturn(false);
    Executor rejecting =
        command -> {
          throw new RejectedExecutionException("queue full");
        };

    PostDeployReconciliationRunner runner =
        new PostDeployReconciliationRunner(recorder, syncService, rejecting);

    assertThatCode(runner::onApplicationReady).doesNotThrowAnyException();
    verify(syncService, never()).recoverAfterDeploy();
  }
}
