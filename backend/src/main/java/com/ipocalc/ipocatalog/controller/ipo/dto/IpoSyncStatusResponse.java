package com.ipocalc.ipocatalog.controller.ipo.dto;

import com.ipocalc.ipocatalog.service.ipo.sync.IpoHistoricalListingService;
import java.time.LocalDateTime;
import java.util.List;
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

    /** 발행실적보고서에 근거한 유상 신주 상장예정일 보완 건수. */
    private int rightsListingBackfilled;

    /** 발행실적보고서에서 회차/날짜가 명확하지 않아 보류한 수. */
    private int rightsListingReviewRequired;

    /** 유상 신주 보고서 조회·저장 실패 수. */
    private int rightsListingFailures;

    /** 실패한 uStockPlus 조회 범위. 성공 구간은 이미 저장된다. */
    private List<IpoHistoricalListingService.FailedRange> listingFailedRanges;

    /** DB 저장에 실패한 종목과 해당 날짜 구간. */
    private List<IpoHistoricalListingService.FailedStock> listingFailedStocks;

    /** 과거 자동 보완값과 새 후보가 달라 수동 확인이 필요한 항목. */
    private List<IpoHistoricalListingService.ListingConflict> listingConflicts;

    private int detailSaved;
    private boolean aborted;
  }
}
