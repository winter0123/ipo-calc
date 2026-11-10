package com.ipocalc.ipocatalog.repository.ipo;

import com.ipocalc.ipocatalog.domain.model.ipo.IpoSyncRun;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** IPO 자동 재점검 실행 이력 저장소. */
@Repository
public interface IpoSyncRunRepository extends JpaRepository<IpoSyncRun, Long> {

  /** 해당 실행 유형·버전·상태 행이 존재하는지 확인한다. 배포 후 복구 완료 판정에 쓴다. */
  boolean existsByRunTypeAndAppVersionAndStatus(String runType, String appVersion, String status);

  /** 해당 실행 유형·상태 행의 개수를 반환한다. */
  long countByRunTypeAndStatus(String runType, String status);

  /** 해당 실행 유형·버전·상태의 최신 행을 조회한다. */
  Optional<IpoSyncRun> findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
      String runType, String appVersion, String status);

  /**
   * 실행 이력을 삽입한다. 부분 유니크 인덱스에 걸리는 중복(POST_DEPLOY_RECOVER SUCCESS)은 조용히 무시하고 실제 삽입 건수(0 또는 1)를 반환한다.
   * 호출자가 삽입 여부를 알 수 있도록 saveAndFlush 대신 이 메서드를 쓴다.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO ipo_sync_runs (run_type, app_version, status, started_at, finished_at,"
              + " total_targets, succeeded, failed, failures, created_at)"
              + " VALUES (:runType, :appVersion, :status, :startedAt, :finishedAt,"
              + " :totalTargets, :succeeded, :failed, CAST(:failuresJson AS jsonb), :createdAt)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertRunIgnoreOnConflict(
      String runType,
      String appVersion,
      String status,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      Integer totalTargets,
      Integer succeeded,
      Integer failed,
      String failuresJson,
      LocalDateTime createdAt);
}
