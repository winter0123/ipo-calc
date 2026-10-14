package com.vibeaiteam.ipocatalog.service.ipo.sync;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 공모주 동기화 상태 관리자 (싱글톤, 메모리 저장).
 *
 * <p>백그라운드 동기화의 진행 상태를 보관한다. 재시작 시 IDLE로 초기화된다. 스레드 안전하게 동작한다.
 */
@Component
@Slf4j
public class IpoSyncStateManager {

  /** 동기화 상태 열거. */
  public enum State {
    IDLE,
    RUNNING,
    DONE,
    FAILED
  }

  /** 현재 상태를 담는 원자적 참조. */
  private final AtomicReference<IpoSyncStatusResponse> currentStatus = new AtomicReference<>();

  /** 생성 시 IDLE 상태로 초기화. */
  public IpoSyncStateManager() {
    resetToIdle();
  }

  /** IDLE 상태로 초기화 (재시작 시 호출). */
  public void resetToIdle() {
    IpoSyncStatusResponse idle =
        IpoSyncStatusResponse.builder()
            .state(State.IDLE.name())
            .fromMonth(null)
            .toMonth(null)
            .monthsDone(0)
            .monthsTotal(1)
            .startedAt(null)
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
    currentStatus.set(idle);
    log.debug("IPO 동기화 상태 초기화: IDLE");
  }

  /** 현재 상태 스냅샷을 반환 (불변 복사). */
  public IpoSyncStatusResponse getStatus() {
    return currentStatus.get();
  }

  /** 범위 실행을 RUNNING으로 시작. */
  public void startRangeSync(String fromMonth, String toMonth, int monthsTotal) {
    IpoSyncStatusResponse running =
        IpoSyncStatusResponse.builder()
            .state(State.RUNNING.name())
            .fromMonth(fromMonth)
            .toMonth(toMonth)
            .monthsDone(0)
            .monthsTotal(monthsTotal)
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
    currentStatus.set(running);
    log.info("IPO 범위 동기화 시작: fromMonth={}, toMonth={}, "
          + "monthsTotal={}", fromMonth, toMonth, monthsTotal);
  }

  /** 기본 실행을 RUNNING으로 시작 (fromMonth/toMonth=null, monthsTotal=1). */
  public void startDefaultSync() {
    IpoSyncStatusResponse running =
        IpoSyncStatusResponse.builder()
            .state(State.RUNNING.name())
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
    currentStatus.set(running);
    log.info("IPO 기본 동기화 시작");
  }

  /** 한 달 처리 완료 시 호출: monthsDone 증가, 결과 누적. */
  public void incrementMonthDone(IpoSyncResult monthResult, int detailSaved) {
    currentStatus.updateAndGet(
        current -> {
          if (current == null || !State.RUNNING.name().equals(current.getState())) {
            return current;
          }
          IpoSyncStatusResponse.SyncResult accum = current.getResult();
          return IpoSyncStatusResponse.builder()
              .state(current.getState())
              .fromMonth(current.getFromMonth())
              .toMonth(current.getToMonth())
              .monthsDone(current.getMonthsDone() + 1)
              .monthsTotal(current.getMonthsTotal())
              .startedAt(current.getStartedAt())
              .finishedAt(null)
              .result(
                  IpoSyncStatusResponse.SyncResult.builder()
                      .created(accum.getCreated() + monthResult.getCreated())
                      .updated(accum.getUpdated() + monthResult.getUpdated())
                      .skipped(accum.getSkipped() + monthResult.getSkippedManual()
                          + monthResult.getSkippedNonIpo())
                      .failed(accum.getFailed() + monthResult.getFailureCount())
                      .scheduleMerged(accum.getScheduleMerged() + monthResult.getScheduleMerged())
                      .detailSaved(accum.getDetailSaved() + detailSaved)
                      .aborted(monthResult.isAborted())
                      .build())
              .error(current.getError())
              .build();
        });
  }

  /** 기본 실행 완료 시 호출. */
  public void completeDefaultSync(IpoManualSyncResult result) {
    currentStatus.updateAndGet(
        current -> {
          if (current == null || !State.RUNNING.name().equals(current.getState())) {
            return current;
          }
          return IpoSyncStatusResponse.builder()
              .state(State.DONE.name())
              .fromMonth(null)
              .toMonth(null)
              .monthsDone(1)
              .monthsTotal(1)
              .startedAt(current.getStartedAt())
              .finishedAt(LocalDateTime.now())
              .result(
                  IpoSyncStatusResponse.SyncResult.builder()
                      .created(result.created())
                      .updated(result.updated())
                      .skipped(result.skipped())
                      .failed(result.failed())
                      .scheduleMerged(result.scheduleMerged())
                      .detailSaved(result.detailSaved())
                      .aborted(result.aborted())
                      .build())
              .error(null)
              .build();
        });
    log.info("IPO 기본 동기화 완료: created={}, updated={}", result.created(), result.updated());
  }

  /** 범위 실행 완료 시 호출. */
  public void completeRangeSync() {
    currentStatus.updateAndGet(
        current -> {
          if (current == null || !State.RUNNING.name().equals(current.getState())) {
            return current;
          }
          return IpoSyncStatusResponse.builder()
              .state(State.DONE.name())
              .fromMonth(current.getFromMonth())
              .toMonth(current.getToMonth())
              .monthsDone(current.getMonthsTotal())
              .monthsTotal(current.getMonthsTotal())
              .startedAt(current.getStartedAt())
              .finishedAt(LocalDateTime.now())
              .result(current.getResult())
              .error(null)
              .build();
        });
    log.info("IPO 범위 동기화 완료");
  }

  /** 실패 처리 (에러 메시지만 저장, 키·URL 노출 금지). */
  public void markFailed(String errorMessage) {
    currentStatus.updateAndGet(
        current -> {
          if (current == null) {
            return current;
          }
          return IpoSyncStatusResponse.builder()
              .state(State.FAILED.name())
              .fromMonth(current.getFromMonth())
              .toMonth(current.getToMonth())
              .monthsDone(current.getMonthsDone())
              .monthsTotal(current.getMonthsTotal())
              .startedAt(current.getStartedAt())
              .finishedAt(LocalDateTime.now())
              .result(current.getResult())
              .error(errorMessage)
              .build();
        });
    log.error("IPO 동기화 실패: {}", errorMessage);
  }

  /** 이미 실행 중인지 확인. */
  public boolean isRunning() {
    IpoSyncStatusResponse status = currentStatus.get();
    return status != null && State.RUNNING.name().equals(status.getState());
  }
}