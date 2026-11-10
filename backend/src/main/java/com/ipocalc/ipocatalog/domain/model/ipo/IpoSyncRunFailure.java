package com.ipocalc.ipocatalog.domain.model.ipo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 재점검 실패 1건의 JSON 직렬화 값. ipoId는 회사 단위 실패면 null이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IpoSyncRunFailure(Long ipoId, String corpCode, String reason) {

  /** 실패 항목을 만든다. ipoId는 없을 수 있다. */
  public static IpoSyncRunFailure of(Long ipoId, String corpCode, String reason) {
    return new IpoSyncRunFailure(ipoId, corpCode, reason);
  }
}
