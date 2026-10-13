package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 공모주 상세 화면의 부가 정보(기업정보·수요예측·증권사별 청약·최근 실적). 출처가 없거나 아직 나오지 않은 값은 null이다.
 *
 * @param description 회사 한 줄 소개(ustockplus)
 * @param keywords 업종 키워드(ustockplus)
 * @param ceoName 대표이사(OpenDART)
 * @param establishedOn 설립일(OpenDART)
 * @param address 주소(OpenDART)
 * @param demandForecastStartDate 수요예측 시작일
 * @param demandForecastEndDate 수요예측 종료일
 * @param institutionalCompetitionRate 기관 경쟁률(예: 1097.62 → 1097.62:1)
 * @param lockUpCommitmentRatio 기관 의무보유확약 비율(%)
 * @param protectedShareRatio 보호예수(매도 제한) 물량 비율(%)
 * @param floatShareRatio 상장일 유통가능 물량 비율(%)
 * @param listedShares 상장 예정 주식수
 * @param offeringShares 공모 주식수
 * @param marketCapAtOfferPrice 확정 공모가 기준 시가총액(원)
 * @param recentSales 최근 연도 매출액(원)
 * @param recentNetProfit 최근 연도 순이익(원)
 * @param registrationStatementUrl DART 증권신고서 주소(https://dart.fss.or.kr/ 만 허용)
 * @param brokers 증권사별 청약 정보
 */
public record IpoInsightResponse(
    String description,
    List<String> keywords,
    String ceoName,
    LocalDate establishedOn,
    String address,
    LocalDate demandForecastStartDate,
    LocalDate demandForecastEndDate,
    BigDecimal institutionalCompetitionRate,
    BigDecimal lockUpCommitmentRatio,
    BigDecimal protectedShareRatio,
    BigDecimal floatShareRatio,
    Long listedShares,
    Long offeringShares,
    Long marketCapAtOfferPrice,
    Long recentSales,
    Long recentNetProfit,
    String registrationStatementUrl,
    List<BrokerAllocation> brokers) {

  /**
   * 증권사 한 곳의 청약 정보.
   *
   * @param name 증권사 이름
   * @param shares 그 증권사에 배정된 일반청약 주식수
   * @param equalSharesPerPerson 균등배정 1인당 예상 주식수(청약 마감 후에만)
   * @param proportionalCompetitionRate 비례 경쟁률(청약 마감 후에만)
   */
  public record BrokerAllocation(
      String name,
      Long shares,
      BigDecimal equalSharesPerPerson,
      BigDecimal proportionalCompetitionRate) {}
}
