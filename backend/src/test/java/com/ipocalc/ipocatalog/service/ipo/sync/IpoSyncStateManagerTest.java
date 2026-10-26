package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** IpoSyncStateManager 테스트. */
class IpoSyncStateManagerTest {

  private IpoSyncStateManager manager;

  @BeforeEach
  void setUp() {
    manager = new IpoSyncStateManager();
  }

  @Test
  void initialState_isIdle() {
    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getState()).isEqualTo("IDLE");
    assertThat(status.getFromMonth()).isNull();
    assertThat(status.getToMonth()).isNull();
    assertThat(status.getMonthsDone()).isEqualTo(0);
    assertThat(status.getMonthsTotal()).isEqualTo(1);
    assertThat(status.getStartedAt()).isNull();
    assertThat(status.getFinishedAt()).isNull();
    assertThat(status.getResult().getCreated()).isEqualTo(0);
    assertThat(status.getError()).isNull();
  }

  @Test
  void startRangeSync_setsRunningWithRange() {
    manager.startRangeSync("2026-01", "2026-03", 3);

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getState()).isEqualTo("RUNNING");
    assertThat(status.getFromMonth()).isEqualTo("2026-01");
    assertThat(status.getToMonth()).isEqualTo("2026-03");
    assertThat(status.getMonthsDone()).isEqualTo(0);
    assertThat(status.getMonthsTotal()).isEqualTo(3);
    assertThat(status.getStartedAt()).isNotNull();
    assertThat(status.getFinishedAt()).isNull();
    assertThat(status.getResult().getCreated()).isEqualTo(0);
    assertThat(status.getError()).isNull();
  }

  @Test
  void startDefaultSync_setsRunningWithoutRange() {
    manager.startDefaultSync();

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getState()).isEqualTo("RUNNING");
    assertThat(status.getFromMonth()).isNull();
    assertThat(status.getToMonth()).isNull();
    assertThat(status.getMonthsDone()).isEqualTo(0);
    assertThat(status.getMonthsTotal()).isEqualTo(1);
    assertThat(status.getStartedAt()).isNotNull();
  }

  @Test
  void incrementMonthDone_accumulatesResults() {
    manager.startRangeSync("2026-01", "2026-03", 3);

    IpoSyncResult month1 = createResult(2, 1, 0, 0, 0, false);
    manager.incrementMonthDone(month1, 3);

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getMonthsDone()).isEqualTo(1);
    assertThat(status.getResult().getCreated()).isEqualTo(2);
    assertThat(status.getResult().getUpdated()).isEqualTo(1);
    assertThat(status.getResult().getDetailSaved()).isEqualTo(3);

    IpoSyncResult month2 = createResult(1, 0, 1, 0, 0, false);
    manager.incrementMonthDone(month2, 2);

    status = manager.getStatus();

    assertThat(status.getMonthsDone()).isEqualTo(2);
    assertThat(status.getResult().getCreated()).isEqualTo(3);
    assertThat(status.getResult().getUpdated()).isEqualTo(1);
    assertThat(status.getResult().getSkipped()).isEqualTo(1);
    assertThat(status.getResult().getDetailSaved()).isEqualTo(5);
  }

  @Test
  void incrementMonthDone_setsAbortedWhenMonthAborted() {
    manager.startRangeSync("2026-01", "2026-03", 3);

    IpoSyncResult month1 = createResult(1, 0, 0, 0, 0, true);
    month1.setAbortReason("인증 오류");
    manager.incrementMonthDone(month1, 0);

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getResult().isAborted()).isTrue();
  }

  @Test
  void completeDefaultSync_setsDone() {
    manager.startDefaultSync();

    IpoManualSyncResult result = new IpoManualSyncResult(5, 3, 2, 1, 4, 7, false);
    manager.completeDefaultSync(result);

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getState()).isEqualTo("DONE");
    assertThat(status.getMonthsDone()).isEqualTo(1);
    assertThat(status.getMonthsTotal()).isEqualTo(1);
    assertThat(status.getFinishedAt()).isNotNull();
    assertThat(status.getResult().getCreated()).isEqualTo(5);
    assertThat(status.getResult().getUpdated()).isEqualTo(3);
    assertThat(status.getResult().getSkipped()).isEqualTo(2);
    assertThat(status.getResult().getFailed()).isEqualTo(1);
    assertThat(status.getResult().getScheduleMerged()).isEqualTo(4);
    assertThat(status.getResult().getDetailSaved()).isEqualTo(7);
    assertThat(status.getError()).isNull();
  }

  @Test
  void completeRangeSync_setsDoneWithFullMonths() {
    manager.startRangeSync("2026-01", "2026-03", 3);
    manager.incrementMonthDone(createResult(2, 1, 0, 0, 0, false), 3);
    manager.incrementMonthDone(createResult(1, 0, 1, 0, 0, false), 2);
    manager.incrementMonthDone(createResult(0, 1, 0, 0, 0, false), 1);

    manager.completeRangeSync();

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getState()).isEqualTo("DONE");
    assertThat(status.getMonthsDone()).isEqualTo(3);
    assertThat(status.getMonthsTotal()).isEqualTo(3);
    assertThat(status.getFinishedAt()).isNotNull();
  }

  @Test
  void markFailed_setsFailedWithError() {
    manager.startRangeSync("2026-01", "2026-03", 3);

    manager.markFailed("동기화 중 오류 발생: RuntimeException");

    IpoSyncStatusResponse status = manager.getStatus();

    assertThat(status.getState()).isEqualTo("FAILED");
    assertThat(status.getError()).isEqualTo("동기화 중 오류 발생: RuntimeException");
    assertThat(status.getFinishedAt()).isNotNull();
  }

  @Test
  void isRunning_returnsTrueWhenRunning() {
    manager.startRangeSync("2026-01", "2026-03", 3);

    assertThat(manager.isRunning()).isTrue();
  }

  @Test
  void isRunning_returnsFalseWhenNotRunning() {
    assertThat(manager.isRunning()).isFalse();

    manager.startDefaultSync();
    assertThat(manager.isRunning()).isTrue();

    manager.completeDefaultSync(new IpoManualSyncResult(0, 0, 0, 0, 0, 0, false));
    assertThat(manager.isRunning()).isFalse();
  }

  private IpoSyncResult createResult(
      int created, int updated, int skippedManual, int skippedNonIpo, int failed, boolean aborted) {
    IpoSyncResult result =
        IpoSyncResult.builder()
            .created(created)
            .updated(updated)
            .skippedManual(skippedManual)
            .skippedNonIpo(skippedNonIpo)
            .failureCount(failed)
            .failures(new java.util.ArrayList<>())
            .aborted(aborted)
            .build();
    result.setScheduleMerged(0);
    return result;
  }
}
