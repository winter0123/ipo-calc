package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 관리자 공모주 동기화 범위 지정 요청. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoSyncRangeRequest {

  /** 시작 월 (YYYY-MM 형식). 선택: 없으면 기본 동작(최근 N일). */
  private String fromMonth;

  /** 종료 월 (YYYY-MM 형식). 선택: 없으면 기본 동작(최근 N일). */
  private String toMonth;

  /** 범위 지정이 있는지 확인. 둘 다 null/빈 문자열이면 기본 동작. */
  public boolean hasRange() {
    return (fromMonth != null && !fromMonth.isBlank()) || (toMonth != null && !toMonth.isBlank());
  }
}