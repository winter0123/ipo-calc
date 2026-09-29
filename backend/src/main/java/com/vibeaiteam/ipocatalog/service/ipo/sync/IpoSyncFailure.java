package com.vibeaiteam.ipocatalog.service.ipo.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** IPO 동기화 처리 실패 정보. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoSyncFailure {

  /** 기업 고유번호 (corp_code) */
  private String corpCode;

  /** 기업명 */
  private String corpName;

  /** 실패 사유 */
  private String reason;
}
