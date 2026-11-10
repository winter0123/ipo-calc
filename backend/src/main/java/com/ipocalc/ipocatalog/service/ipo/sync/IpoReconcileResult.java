package com.ipocalc.ipocatalog.service.ipo.sync;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 활성 자동 공모 재점검 결과. {@link IpoSyncResult}와 분리된 재점검 단계의 집계값이다. */
@Getter
@Setter
public class IpoReconcileResult {

  /** 재점검 대상(활성 자동 공모) 건수. */
  private int totalTargets;

  /** 철회 없이 유지되거나 철회를 완료한 건수. */
  private int succeeded;

  /** 실패 기록된 건수(행 기준 1건 = 1점). */
  private int failed;

  /** 철회 처리된 건수. */
  private int withdrawn;

  /** 실패 상세 목록. reason은 '철회점검: ' 접두어로 구분한다. */
  private List<IpoSyncFailure> failures = new ArrayList<>();

  /** 인증 오류 등으로 재점검 단계 자체가 중단(aborted)됐는지 여부. */
  private boolean aborted;

  /** 중단 사유(aborted=true인 경우). */
  private String abortReason;

  /** 중단 사유가 일반 API 오류라 다음 일정 실행에서 재시도 가능한지 여부. */
  private boolean retryable;
}
