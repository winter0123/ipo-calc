package com.ipocalc.ipocatalog.controller.ipo.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoHistoricalListingService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 공모주 동기화 상태 응답. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoSyncStatusResponse {

  /** 동기화 상태: IDLE, RUNNING, DONE, FAILED. */
  private String state;

  /** 범위 실행의 시작 월 (YYYY-MM). 기본 실행이면 null. */
  private String fromMonth;

  /** 범위 실행의 종료 월 (YYYY-MM). 기본 실행이면 null. */
  private String toMonth;

  /** 완료된 월 수. 기본 실행이면 0 또는 1. */
  private Integer monthsDone;

  /** 전체 대상 월 수. 기본 실행이면 1. */
  private Integer monthsTotal;

  /** 동기화 시작 시각. */
  private LocalDateTime startedAt;

  /** 동기화 완료 시각. 진행 중이면 null. */
  private LocalDateTime finishedAt;

  /** 누적 결과. 진행 중에도 갱신됨. */
  private SyncResult result;

  /** 실패 시 한국어 에러 메시지 (키·URL 노출 금지). 성공 시 null. */
  private String error;

  /** 동기화 결과 상세. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SyncResult {
    private int created;
    private int updated;
    private int skipped;
    private int failed;
    private int scheduleMerged;
    /** 선택한 과거 기간에서 신규 보완한 상장일 수. */
    private int listingBackfilled;
    /** 실패한 uStockPlus 조회 범위. 성공 구간은 이미 저장된다. */
    private List<IpoHistoricalListingService.FailedRange> listingFailedRanges;
    private int detailSaved;
    private boolean aborted;
  }
}
