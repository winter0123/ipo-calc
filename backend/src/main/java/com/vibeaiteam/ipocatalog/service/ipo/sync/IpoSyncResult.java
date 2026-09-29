package com.vibeaiteam.ipocatalog.service.ipo.sync;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** IPO 동기화 처리 결과. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoSyncResult {

  /** 신규 생성 건수 */
  private int created;

  /** 갱신 건수 */
  private int updated;

  /** 수동 등록 레코드로 건너뛴 건수 (syncSource == null) */
  private int skippedManual;

  /** 비-IPO(유상증자 등)로 건너뛴 건수 (stock_code 있음) */
  private int skippedNonIpo;

  /** 실패 건수 */
  private int failureCount;

  /** 실패 상세 목록 */
  private List<IpoSyncFailure> failures;

  /** 동기화 중단 여부 (인증 오류 또는 rate limit 020) */
  private boolean aborted;

  /** 중단 사유 (aborted=true인 경우) */
  private String abortReason;
}
