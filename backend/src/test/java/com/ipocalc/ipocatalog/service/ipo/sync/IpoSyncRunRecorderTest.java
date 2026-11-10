package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.ipocatalog.domain.model.ipo.IpoSyncRun;
import com.ipocalc.ipocatalog.repository.ipo.IpoSyncRunRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** {@link IpoSyncRunRecorder}의 완료 판정·부분 유니크 인덱스·영속화 검증을 실제 DB로 확인한다. */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class IpoSyncRunRecorderTest {

  @Autowired private IpoSyncRunRepository repository;
  @Autowired private Clock clock;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void failedReconcileLeavesFailedRowAndNoSuccess() {
    IpoReconcileResult reconcile = failedReconcile();

    IpoSyncRunRecorder recorder = new IpoSyncRunRecorder(repository, clock, transactionTemplate);
    boolean complete = recorder.recordRecover(LocalDateTime.now(clock).minusMinutes(1), reconcile);

    assertThat(complete).isFalse();
    assertThat(
            repository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isZero();
    Optional<IpoSyncRun> failed =
        repository.findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
            IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_FAILED);
    assertThat(failed).isPresent();
    assertThat(failed.get().getFailures())
        .anySatisfy(failure -> assertThat(failure.reason()).contains("철회점검:"));
  }

  @Test
  void successRecordIsIdempotentAndOnlyOneSuccessRowExists() {
    IpoReconcileResult reconcile = successfulReconcile();
    IpoSyncRunRecorder recorder = new IpoSyncRunRecorder(repository, clock, transactionTemplate);

    boolean first = recorder.recordRecover(LocalDateTime.now(clock).minusMinutes(2), reconcile);
    boolean second = recorder.recordRecover(LocalDateTime.now(clock).minusMinutes(1), reconcile);

    assertThat(first).isTrue();
    assertThat(second).isTrue();
    assertThat(
            repository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(1);
  }

  @Test
  void dailySuccessRecordsDailyRowAndCompletesRecoverKeyExactlyOnce() {
    IpoReconcileResult reconcile = successfulReconcile();
    IpoSyncRunRecorder recorder = new IpoSyncRunRecorder(repository, clock, transactionTemplate);

    boolean first =
        recorder.recordDaily(
            LocalDateTime.now(clock).minusMinutes(2), reconcile, List.of(), false, 0);
    boolean second =
        recorder.recordDaily(
            LocalDateTime.now(clock).minusMinutes(1), reconcile, List.of(), false, 0);

    assertThat(first).isTrue();
    assertThat(second).isTrue();
    assertThat(
            repository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_DAILY_RECONCILE, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(2);
    assertThat(
            repository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(1);
  }

  @Test
  void dailyPartialFailureDoesNotCompleteRecoverKey() {
    IpoReconcileResult reconcile = failedReconcile();
    IpoSyncRunRecorder recorder = new IpoSyncRunRecorder(repository, clock, transactionTemplate);

    boolean complete =
        recorder.recordDaily(
            LocalDateTime.now(clock).minusMinutes(1), reconcile, reconcile.getFailures(), false, 0);

    assertThat(complete).isFalse();
    assertThat(
            repository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_DAILY_RECONCILE, IpoSyncRun.STATUS_FAILED))
        .isEqualTo(1);
    assertThat(
            repository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isZero();
  }

  private IpoReconcileResult successfulReconcile() {
    IpoReconcileResult reconcile = new IpoReconcileResult();
    reconcile.setTotalTargets(3);
    reconcile.setSucceeded(3);
    reconcile.setFailed(0);
    reconcile.setWithdrawn(1);
    return reconcile;
  }

  private IpoReconcileResult failedReconcile() {
    IpoReconcileResult reconcile = new IpoReconcileResult();
    reconcile.setTotalTargets(2);
    reconcile.setSucceeded(1);
    reconcile.setFailed(1);
    reconcile
        .getFailures()
        .add(
            IpoSyncFailure.builder()
                .corpCode("02038432")
                .corpName("심원")
                .reason("철회점검: DS006 상세 없음")
                .ipoId(99L)
                .build());
    return reconcile;
  }
}
