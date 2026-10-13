package com.vibeaiteam.ipocatalog.service.ipo.detail.dart;

import java.math.BigDecimal;
import java.util.List;

/**
 * DART 공시 원문(투자설명서·증권신고서)에서 뽑은 공모주 상세 정보. 못 찾은 항목은 null 또는 빈 리스트다.
 *
 * @param financials 요약재무(공시 순서, 금액은 원 단위)
 * @param floatSchedule 상장일 이후 누적 유통가능(락업 해제) 일정
 * @param competitionRate 수요예측 기관 경쟁률(X:1)
 * @param lockUpCommitmentRatio 의무보유확약 비율(%)
 * @param largestShareholderName 최대주주 이름
 * @param largestShareholderRatio 최대주주 지분율(%)
 * @param capitalStock 자본금(원, 요약재무 첫 기간)
 */
public record ParsedFiling(
    List<Financial> financials,
    List<FloatStep> floatSchedule,
    BigDecimal competitionRate,
    BigDecimal lockUpCommitmentRatio,
    String largestShareholderName,
    BigDecimal largestShareholderRatio,
    Long capitalStock) {

  /**
   * 한 기간의 요약 손익. 금액은 원 단위이고 없으면 null이다.
   *
   * @param period 기간 머리글 원문(예: 제11기(2025년12월말))
   * @param revenue 매출액
   * @param operatingProfit 영업이익
   * @param netIncome 당기순이익
   */
  public record Financial(String period, Long revenue, Long operatingProfit, Long netIncome) {}

  /**
   * 유통가능 일정 한 단계.
   *
   * @param label 원문 구분(예: 상장일 유통가능)
   * @param shares 누적 유통가능 주식수
   * @param ratio 누적 비율(%)
   */
  public record FloatStep(String label, Long shares, BigDecimal ratio) {}
}
