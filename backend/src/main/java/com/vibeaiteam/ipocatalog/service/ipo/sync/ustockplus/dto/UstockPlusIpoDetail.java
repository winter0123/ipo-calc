package com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ustockplus {@code /v2/ipo/stocks/{종목코드}} 응답 중 공모주 상세 화면에 쓰는 필드만.
 *
 * @param description 회사 한 줄 소개
 * @param keywords 업종 키워드
 * @param subscriptionAndListedInformation 증권사별 청약 정보
 * @param demandForecastResponse 수요예측 결과
 * @param ipoInformation 공모 개요·최근 실적
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UstockPlusIpoDetail(
    String description,
    List<String> keywords,
    SubscriptionInfo subscriptionAndListedInformation,
    DemandForecast demandForecastResponse,
    IpoInformation ipoInformation) {

  /**
   * 증권사별 청약 정보.
   *
   * @param securitiesFirmCompetitionRates 증권사별 배정 주식수·경쟁률
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SubscriptionInfo(List<FirmRate> securitiesFirmCompetitionRates) {}

  /**
   * 증권사 한 곳의 청약 정보.
   *
   * @param securitiesFirmName 증권사 이름
   * @param numberOfDistributedShares 그 증권사에 배정된 일반청약 주식수
   * @param equalityDistributionNumberPerPerson 균등배정 1인당 예상 주식수(청약 마감 후)
   * @param proportionalDistributionRatio 비례 경쟁률(청약 마감 후)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FirmRate(
      String securitiesFirmName,
      Long numberOfDistributedShares,
      BigDecimal equalityDistributionNumberPerPerson,
      BigDecimal proportionalDistributionRatio) {}

  /**
   * 수요예측 결과.
   *
   * @param demandForecastStartDate 수요예측 시작일
   * @param demandForecastEndDate 수요예측 종료일
   * @param instCompetitiveness 기관 경쟁률
   * @param lockUpPromiseRatio 의무보유확약 비율(%)
   * @param offerPriceBasedMarketCap 확정 공모가 기준 시가총액(원)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DemandForecast(
      LocalDate demandForecastStartDate,
      LocalDate demandForecastEndDate,
      BigDecimal instCompetitiveness,
      BigDecimal lockUpPromiseRatio,
      Long offerPriceBasedMarketCap) {}

  /**
   * 공모 개요와 최근 실적.
   *
   * @param totalSales 최근 연도 매출액(원)
   * @param netProfit 최근 연도 순이익(원)
   * @param numberOfIpoShares 공모 주식수
   * @param numberOfListedStockShare 상장 예정 주식수
   * @param lockupRatio 보호예수(매도 제한) 물량 비율(%)
   * @param floatShareRatio 상장일 유통가능 물량 비율(%)
   * @param registrationStatementUrl DART 증권신고서 주소
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IpoInformation(
      Long totalSales,
      Long netProfit,
      Long numberOfIpoShares,
      Long numberOfListedStockShare,
      BigDecimal lockupRatio,
      BigDecimal floatShareRatio,
      String registrationStatementUrl) {}
}
