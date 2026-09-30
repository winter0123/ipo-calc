package com.vibeaiteam.brokerintegration.service.matching;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 브로커 일자×종목 매도 집계를 우리 배정(Allocation)에 연결하는 순수 로직 매처. DB·Spring·외부 API에 의존하지 않는 순수 자바(입력 record 목록 →
 * 결과 record 목록)로 만든다.
 */
public final class BrokerTransactionMatcher {

  private BrokerTransactionMatcher() {
    // 인스턴스화 방지
  }

  /**
   * 브로커 거래 내역과 배정 목록을 매칭한다.
   *
   * @param summaries 브로커가 알려준 일자×종목 매도 집계 목록
   * @param candidates 해당 증권계좌의 배정 목록
   * @return 매칭 결과와 종목코드가 없는 후보 목록
   */
  public static MatchReport match(
      List<BrokerSaleSummary> summaries, List<MatchCandidate> candidates) {
    if (summaries == null) {
      throw new IllegalArgumentException("summaries는 null일 수 없음");
    }
    if (candidates == null) {
      throw new IllegalArgumentException("candidates는 null일 수 없음");
    }

    // 종목코드가 null인 후보들을 별도로 수집
    List<Long> candidatesWithoutStockCode = new ArrayList<>();
    Map<String, List<MatchCandidate>> candidatesByStockCode = new HashMap<>();

    for (MatchCandidate candidate : candidates) {
      if (candidate.stockCode() == null) {
        candidatesWithoutStockCode.add(candidate.allocationId());
      } else {
        candidatesByStockCode
            .computeIfAbsent(candidate.stockCode(), k -> new ArrayList<>())
            .add(candidate);
      }
    }

    List<MatchResult> results = new ArrayList<>(summaries.size());

    // 각 브로커 요약에 대해 순차적으로 처리 (입력 순서 유지)
    for (BrokerSaleSummary summary : summaries) {
      MatchResult result =
          matchSummaryToCandidates(
              summary,
              candidatesByStockCode.getOrDefault(summary.stockCode(), Collections.emptyList()));
      results.add(result);
    }

    return new MatchReport(results, candidatesWithoutStockCode);
  }

  /**
   * 단일 브로커 요약을 후보 목록과 매칭한다.
   *
   * @param summary 브로커 매도 집계
   * @param stockCodeCandidates 해당 종목코드에 대한 배정 후보 목록
   * @return 매칭 결과
   */
  private static MatchResult matchSummaryToCandidates(
      BrokerSaleSummary summary, List<MatchCandidate> stockCodeCandidates) {
    // 1. stockCode가 같은 후보를 찾는다
    if (stockCodeCandidates.isEmpty()) {
      // 0건 → NO_ALLOCATION_FOR_STOCK
      return new MatchResult(summary, null, UnmatchedReason.NO_ALLOCATION_FOR_STOCK);
    }

    if (stockCodeCandidates.size() > 1) {
      // 2건 이상 → AMBIGUOUS_ALLOCATIONS
      return new MatchResult(summary, null, UnmatchedReason.AMBIGUOUS_ALLOCATIONS);
    }

    // 후보 1건이면 처리
    MatchCandidate candidate = stockCodeCandidates.get(0);
    LocalDate tradeDate = summary.tradeDate();

    // 그 후보의 existingSales 중 saleDate == summary.tradeDate 인 것들의 합계를 구한다
    long totalQuantity = 0;
    long totalAmount = 0;
    long totalFee = 0;
    long totalTax = 0;
    boolean hasExistingSaleForDate = false;

    for (ExistingSale existingSale : candidate.existingSales()) {
      if (existingSale.saleDate().equals(tradeDate)) {
        hasExistingSaleForDate = true;
        totalQuantity += existingSale.quantity();
        totalAmount += existingSale.amount();
        totalFee += existingSale.fee();
        totalTax += existingSale.tax();
      }
    }

    if (!hasExistingSaleForDate) {
      // 그 날짜 기존 Sale 이 없다: 남은 수량(allocatedQuantity − 모든 existingSales 수량 합) ≥ summary.quantity 이면
      // 매칭 성공
      long totalExistingQuantity =
          candidate.existingSales().stream().mapToLong(ExistingSale::quantity).sum();
      long remainingQuantity = candidate.allocatedQuantity() - totalExistingQuantity;

      if (remainingQuantity >= summary.quantity()) {
        // 매칭 성공
        return new MatchResult(summary, candidate.allocationId(), null);
      } else {
        // 수량 초과
        return new MatchResult(
            summary, candidate.allocationId(), UnmatchedReason.QUANTITY_EXCEEDS_ALLOCATION);
      }
    } else {
      // 그 날짜 기존 Sale 이 있다: 4개 합계가 summary 와 모두 같으면 ALREADY_REGISTERED, 하나라도 다르면 SPLIT_SALE_MISMATCH
      if (totalQuantity == summary.quantity()
          && totalAmount == summary.amount()
          && totalFee == summary.fee()
          && totalTax == summary.tax()) {
        // 중복 등록 방지
        return new MatchResult(
            summary, candidate.allocationId(), UnmatchedReason.ALREADY_REGISTERED);
      } else {
        // 분할 매도 합산 불일치
        return new MatchResult(
            summary, candidate.allocationId(), UnmatchedReason.SPLIT_SALE_MISMATCH);
      }
    }
  }

  /** 매칭 결과를 담는 레포트. */
  public static record MatchReport(
      List<MatchResult> results, List<Long> candidatesWithoutStockCode) {
    /** 필수 값의 null 여부를 검증한다. */
    public MatchReport {
      if (results == null) {
        throw new IllegalArgumentException("results는 null일 수 없음");
      }
      if (candidatesWithoutStockCode == null) {
        throw new IllegalArgumentException("candidatesWithoutStockCode는 null일 수 없음");
      }
    }
  }
}
