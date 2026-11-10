package com.ipocalc.ipocatalog.domain.model.ipo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** IPO 자동 재점검 실행 결과. 04:30 배치(DAILY_RECONCILE)와 재기동 복구(POST_DEPLOY_RECOVER)를 기록한다. */
@Entity
@Table(name = "ipo_sync_runs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IpoSyncRun {

  /** 일일 04:30 배치의 재점검 단계 기록. */
  public static final String RUN_DAILY_RECONCILE = "DAILY_RECONCILE";

  /** 재기동 후 자동복구 기록. */
  public static final String RUN_POST_DEPLOY_RECOVER = "POST_DEPLOY_RECOVER";

  /** 배포 후 복구 알고리즘 식별 키(코드 버전). 배포 단위 SHA가 아니라 이 키 고정이다. */
  public static final String RECOVERY_KEY = "ipo-reconciliation-v1";

  /** 실행 성공(전수). */
  public static final String STATUS_SUCCESS = "SUCCESS";

  /** 중단 또는 부분 실패. 완료로 보지 않는다. */
  public static final String STATUS_FAILED = "FAILED";

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("실행 이력 고유 식별자")
  private Long id;

  /** 실행 유형. */
  @Column(name = "run_type", nullable = false, length = 30)
  @Comment("실행 유형(DAILY_RECONCILE/POST_DEPLOY_RECOVER)")
  private String runType;

  /** POST_DEPLOY_RECOVER의 복구 알고리즘 키(ipo-reconciliation-v1). DAILY도 관측용으로 같은 키를 기록한다. */
  @Column(name = "app_version", nullable = false, length = 100)
  @Comment("복구 알고리즘 키(ipo-reconciliation-v1) 또는 DAILY 관측용 키")
  private String appVersion;

  /** 실행 상태. */
  @Column(name = "status", nullable = false, length = 10)
  @Comment("실행 상태(SUCCESS/FAILED)")
  private String status;

  /** 실행 시작 시각. */
  @Column(name = "started_at", nullable = false)
  @Comment("실행 시작 시각")
  private LocalDateTime startedAt;

  /** 종료 시각. */
  @Column(name = "finished_at")
  @Comment("종료 시각")
  private LocalDateTime finishedAt;

  /** 재점검 대상(활성 자동 공모) 건수. */
  @Column(name = "total_targets")
  @Comment("재점검 대상 건수")
  private Integer totalTargets;

  /** 철회 없이 유지되거나 철회를 완료한 건수. */
  @Column(name = "succeeded")
  @Comment("성공 건수")
  private Integer succeeded;

  /** 실패 기록 건수. */
  @Column(name = "failed")
  @Comment("실패 건수")
  private Integer failed;

  /** 실패 상세 JSON 배열. 철회 점검 실패는 reason이 '철회점검: '로 시작한다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "failures")
  @Builder.Default
  @Comment("실패 상세 JSON 배열")
  private List<IpoSyncRunFailure> failures = new ArrayList<>();

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  @Comment("생성 시각")
  private LocalDateTime createdAt;
}
