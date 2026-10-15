package com.ipocalc.brokerintegration.service.matching;

import com.ipocalc.accountauthentication.util.validation.StockCodes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
      String code = StockCodes.normalize(candidate.stockCode());
      if (code == null) {
        candidatesWithoutStockCode.add(candidate.allocationId());
      } else {
        candidatesByStockCode.computeIfAbsent(code, k -> new ArrayList<>()).add(candidate);
      }
    }

    // 같은 배정의 남은 수량은 날짜순으로 차감한다(뒤쪽 매도가 앞쪽 매도를 고려하도록). 결과는 입력 순서를 유지한다.
    MatchResult[] results = new MatchResult[summaries.size()];
    Map<Long, Long> consumed = new HashMap<>();
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < results.length; i++) {
      order.add(i);
    }
    order.sort(Comparator.comparing(i -> summaries.get(i).tradeDate()));
    for (int i : order) {
      BrokerSaleSummary summary = summaries.get(i);
      results[i] =
          matchSummaryToCandidates(
              summary,
              candidatesByStockCode.getOrDefault(
                  StockCodes.normalize(summary.stockCode()), Collections.emptyList()),
              consumed);
    }

    return new MatchReport(List.of(results), candidatesWithoutStockCode);
  }

  /**
   * 단일 브로커 요약을 후보 목록과 매칭한다.
   *
   * @param summary 브로커 매도 집계
   * @param stockCodeCandidates 해당 종목코드에 대한 배정 후보 목록
   * @param consumed 이번 조회에서 앞선 날짜 매도가 이미 차지한 배정별 수량(성공 시 누적한다)
   * @return 매칭 결과
   */
  private static MatchResult matchSummaryToCandidates(
      BrokerSaleSummary summary,
      List<MatchCandidate> stockCodeCandidates,
      Map<Long, Long> consumed) {
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
    List<ExistingSale> onDate = existingSalesOn(candidate.existingSales(), tradeDate);
    boolean hasExistingSaleForDate = !onDate.isEmpty();
    for (ExistingSale existingSale : onDate) {
      totalQuantity += existingSale.quantity();
      totalAmount += existingSale.amount();
    }

    if (!hasExistingSaleForDate) {
      // 그 날짜 기존 Sale 이 없다: 남은 수량(allocatedQuantity − 모든 existingSales 수량 합) ≥ summary.quantity 이면
      // 매칭 성공
      long totalExistingQuantity =
          candidate.existingSales().stream().mapToLong(ExistingSale::quantity).sum();
      long remainingQuantity =
          candidate.allocatedQuantity()
              - totalExistingQuantity
              - consumed.getOrDefault(candidate.allocationId(), 0L);

      if (remainingQuantity >= summary.quantity()) {
        // 매칭 성공
        consumed.merge(candidate.allocationId(), summary.quantity(), Long::sum);
        return new MatchResult(summary, candidate.allocationId(), null);
      } else {
        // 수량 초과
        return new MatchResult(
            summary, candidate.allocationId(), UnmatchedReason.QUANTITY_EXCEEDS_ALLOCATION);
      }
    } else {
      // 그 날짜 기존 Sale 이 있다: 수량·금액 합계가 summary 와 같으면 ALREADY_REGISTERED, 다르면 SPLIT_SALE_MISMATCH.
      // 수수료·세금은 비교하지 않는다. 우리 쪽 fee는 공모주수수료, tax는 세금 및 기타 수수료(증권사 수수료+세금)로
      // 입력 경로(직접 등록·증권사 가져오기)마다 증권사 값과 일대일로 대응하지 않기 때문이다(2026-10-02).
      if (totalQuantity == summary.quantity() && totalAmount == summary.amount()) {
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

  /**
   * 해당 일자의 활성 매도만 골라낸다. 미리보기(ALREADY_REGISTERED·SPLIT_SALE_MISMATCH 판정)와 확정(중복 거부)이 같은 규칙을 쓰도록
   * 공유한다.
   *
   * @param sales 배정의 활성 매도
   * @param date 매도일
   * @return 그 날짜의 매도 목록
   */
  public static List<ExistingSale> existingSalesOn(List<ExistingSale> sales, LocalDate date) {
    return sales.stream().filter(s -> s.saleDate().equals(date)).toList();
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
