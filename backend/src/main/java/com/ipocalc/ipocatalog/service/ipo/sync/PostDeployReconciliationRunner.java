package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 배포 후 자동복구(ApplicationReadyEvent)를 비동기로 1회 실행한다.
 *
 * <p>ipo-reconciliation-v1 복구 알고리즘의 SUCCESS가 이미 있으면 스킵한다. 없으면 ipoSyncExecutor(단일 스레드)로 재점검을 제출하고,
 * 실패하면 완료 기록을 남기지 않아 다음 실행(04:30 배치 또는 재기동)이 전수 재점검으로 재시도한다. busy·실행기 거부는 예외를 밖으로 던지지 않고 조용히 종료한다.
 *
 * <p>{@code app.ipo-sync.enabled=true}일 때만 동작한다(기본값 true). 스케줄러와 동일한 조건을 존중한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.ipo-sync.enabled", havingValue = "true", matchIfMissing = true)
public class PostDeployReconciliationRunner {

  private final IpoSyncRunRecorder ipoSyncRunRecorder;
  private final IpoSyncService ipoSyncService;

  @Qualifier("ipoSyncExecutor")
  private final Executor ipoSyncExecutor;

  /** ApplicationReadyEvent 수신 시 복구가 아직 완료되지 않았으면 비동기로 제출한다. */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (ipoSyncRunRecorder.isRecoverComplete()) {
      log.info("배포 후 재점검은 이미 완료됐습니다 (ipo-reconciliation-v1). 스킵합니다.");
      return;
    }
    try {
      ipoSyncExecutor.execute(this::recoverAfterDeploySafely);
      log.info("배포 후 재점검을 비동기로 제출했습니다 (ipo-reconciliation-v1).");
    } catch (RejectedExecutionException e) {
      // 실행기 거부: FAILED 기록을 남기지 않고 다음 04:30 배치에 복구를 위임한다
      log.warn("배포 후 재점검 실행기 거부 - 다음 04:30 배치에 위임합니다.");
    }
  }

  private void recoverAfterDeploySafely() {
    try {
      ipoSyncService.recoverAfterDeploy();
    } catch (IpoSyncBusyException e) {
      // 이미 실행 중: 완료 기록을 남기지 않아 다음 실행이 재시도한다
      log.info("배포 후 재점검 생략 (이미 동기화 실행 중). 다음 04:30 배치가 재점검합니다.");
    } catch (Exception e) {
      log.error("배포 후 재점검 중 예외 (클래스만 기록): {}", e.getClass().getSimpleName());
    }
  }
}
