package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.ipocatalog.domain.model.ipo.IpoSyncRun;
import com.ipocalc.ipocatalog.domain.model.ipo.IpoSyncRunFailure;
import com.ipocalc.ipocatalog.repository.ipo.IpoSyncRunRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * IPO 자동 재점검 실행 결과를 ipo_sync_runs에 기록한다.
 *
 * <p>성공은 "복구 완료" 판정이며, 완료 기록은 POST_DEPLOY_RECOVER SUCCESS 행이다. INSERT는 `ON CONFLICT DO NOTHING`으로
 * 원자적으로 처리해 부분 유니크 충돌이 예외를 만들지 않으며, 삽입 여부와 DB 상태를 함께 확인해 영속화 실패를 성공으로 표시하지 않는다. 부분 실패는 FAILED이며 다음
 * 실행(04:30 배치 또는 재기동)이 전수 재점검한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IpoSyncRunRecorder {

  private final IpoSyncRunRepository ipoSyncRunRepository;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 해당 복구 알고리즘 키의 복구가 이미 완료(SUCCESS)됐는지 확인한다. */
  public boolean isRecoverComplete() {
    return ipoSyncRunRepository.existsByRunTypeAndAppVersionAndStatus(
        IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_SUCCESS);
  }

  /**
   * 일일 재점검 결과를 기록한다. 전수 성공이면 DAILY_RECONCILE SUCCESS를 남기고, 복구 키가 아직 완료되지 않았으면 POST_DEPLOY_RECOVER
   * SUCCESS도 함께 남긴다.
   *
   * @param startedAt 실행 시작 시각
   * @param reconcile 재점검 집계
   * @param failures 재점검 실패 목록
   * @param collectAborted 신규 수집 단계가 중단됐는지 여부(전체 실행은 실패로 기록)
   * @param collectFailureCount 신규 수집 단계의 건별 실패 건수
   * @return DAILY SUCCESS 기록 및 복구 키 완료가 보장됐는지 여부
   */
  public boolean recordDaily(
      LocalDateTime startedAt,
      IpoReconcileResult reconcile,
      List<IpoSyncFailure> failures,
      boolean collectAborted,
      int collectFailureCount) {
    boolean complete =
        !reconcile.isAborted()
            && reconcile.getFailed() == 0
            && !collectAborted
            && collectFailureCount == 0;
    String status = complete ? IpoSyncRun.STATUS_SUCCESS : IpoSyncRun.STATUS_FAILED;
    List<IpoSyncRunFailure> failureEntries = complete ? List.of() : toEntries(failures);
    insert(
        IpoSyncRun.RUN_DAILY_RECONCILE,
        status,
        startedAt,
        reconcile.getTotalTargets(),
        reconcile.getSucceeded(),
        reconcile.getFailed() + collectFailureCount,
        failureEntries);
    if (!complete) {
      return false;
    }
    return ensureRecoverComplete(startedAt, reconcile);
  }

  /**
   * 배포 후 복구(POST_DEPLOY_RECOVER) 결과를 기록한다. 부분 실패는 FAILED이며, 완료 기록은 만들어지지 않는다. 영속화 실패는 예외를 밖으로 던져
   * 호출자가 성공으로 오인하지 않게 한다.
   *
   * @param startedAt 실행 시작 시각
   * @param reconcile 재점검 집계
   * @return 삽입 이후 실제 복구 완료 상태(SUCCESS 행 존재)인지 여부
   */
  public boolean recordRecover(LocalDateTime startedAt, IpoReconcileResult reconcile) {
    boolean candidate = !reconcile.isAborted() && reconcile.getFailed() == 0;
    String status = candidate ? IpoSyncRun.STATUS_SUCCESS : IpoSyncRun.STATUS_FAILED;
    List<IpoSyncRunFailure> failureEntries =
        candidate ? List.of() : toEntries(reconcile.getFailures());
    insert(
        IpoSyncRun.RUN_POST_DEPLOY_RECOVER,
        status,
        startedAt,
        reconcile.getTotalTargets(),
        reconcile.getSucceeded(),
        reconcile.getFailed(),
        failureEntries);
    return candidate && isRecoverComplete();
  }

  /** 일일 전수 성공 시 복구 키를 완료 처리한다. 실제 완료(SUCCESS 행 존재)를 확인해 반환한다. */
  private boolean ensureRecoverComplete(LocalDateTime startedAt, IpoReconcileResult reconcile) {
    if (isRecoverComplete()) {
      return true;
    }
    insert(
        IpoSyncRun.RUN_POST_DEPLOY_RECOVER,
        IpoSyncRun.STATUS_SUCCESS,
        startedAt,
        reconcile.getTotalTargets(),
        reconcile.getSucceeded(),
        0,
        List.of());
    return isRecoverComplete();
  }

  /** 실패 상세를 JSONB 문자열로 직렬화해 ipo_sync_runs에 삽입한다. 충돌은 무시되고 삽입 건수를 반환한다. */
  private int insert(
      String runType,
      String status,
      LocalDateTime startedAt,
      int totalTargets,
      int succeeded,
      int failed,
      List<IpoSyncRunFailure> failures) {
    LocalDateTime now = LocalDateTime.now(clock);
    String json;
    try {
      json = objectMapper.writeValueAsString(failures);
    } catch (Exception e) {
      // 직렬화 실패는 데이터 문제이므로 성공으로 삼지 않고 실패로 전파한다
      throw new IllegalStateException("failures JSON 직렬화 실패", e);
    }
    // @Modifying 쿼리는 트랜잭션이 필요하다. 수동 생성(테스트)에서도 동작하도록 자체 템플릿을 쓴다
    Integer inserted =
        transactionTemplate.execute(
            txStatus ->
                ipoSyncRunRepository.insertRunIgnoreOnConflict(
                    runType,
                    IpoSyncRun.RECOVERY_KEY,
                    status,
                    startedAt,
                    now,
                    totalTargets,
                    succeeded,
                    failed,
                    json,
                    now));
    return inserted == null ? 0 : inserted;
  }

  private static List<IpoSyncRunFailure> toEntries(List<IpoSyncFailure> failures) {
    if (failures == null) {
      return List.of();
    }
    return failures.stream()
        .map(f -> IpoSyncRunFailure.of(f.getIpoId(), f.getCorpCode(), f.getReason()))
        .toList();
  }
}
