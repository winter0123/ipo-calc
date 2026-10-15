package com.ipocalc.ipocatalog.service.ipo.detail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 공시에서 읽어 온 공모주 상세 값 한 벌. 동기화가 만들어 {@link IpoDetailService#save}에 넘긴다.
 *
 * @param sourceRceptNo 공시 접수번호(14자리)
 * @param sourceReportName 공시 이름
 * @param ceoName 대표이사
 * @param establishedOn 설립일
 * @param address 본점 주소
 * @param parValue 액면가(원)
 * @param capitalStock 자본금(원)
 * @param largestShareholderName 최대주주 이름
 * @param largestShareholderRatio 최대주주 공모 전 지분율(%)
 * @param institutionalCompetitionRate 수요예측 기관 경쟁률(X:1의 X)
 * @param lockUpCommitmentRatio 의무보유확약 비율(%)
 * @param financials 요약 재무(공시 순서)
 * @param floatSchedule 유통가능 물량 일정
 * @param underwriters 인수회사
 */
public record IpoDetailSnapshot(
    String sourceRceptNo,
    String sourceReportName,
    String ceoName,
    LocalDate establishedOn,
    String address,
    Long parValue,
    Long capitalStock,
    String largestShareholderName,
    BigDecimal largestShareholderRatio,
    BigDecimal institutionalCompetitionRate,
    BigDecimal lockUpCommitmentRatio,
    List<Financial> financials,
    List<FloatStep> floatSchedule,
    List<Underwriter> underwriters) {

  /**
   * 요약 재무 한 기.
   *
   * @param period 기간 이름. 예: 제11기(2025년12월말)
   * @param revenue 매출액(원)
   * @param operatingProfit 영업이익(원)
   * @param netIncome 당기순이익(원)
   */
  public record Financial(String period, Long revenue, Long operatingProfit, Long netIncome) {}

  /**
   * 유통가능 물량 한 단계.
   *
   * @param label 시점 이름. 예: 상장일 유통가능
   * @param shares 누적 유통가능 주식수
   * @param ratio 누적 유통가능 비율(%)
   */
  public record FloatStep(String label, Long shares, BigDecimal ratio) {}

  /**
   * 인수회사 한 곳.
   *
   * @param name 증권사 이름
   * @param role 역할. 예: 대표
   * @param shares 인수 주식수
   */
  public record Underwriter(String name, String role, Long shares) {}
}
