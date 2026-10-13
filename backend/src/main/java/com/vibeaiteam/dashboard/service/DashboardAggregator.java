package com.vibeaiteam.dashboard.service;

import com.vibeaiteam.dashboard.dto.InvestorAggregationDto;
import com.vibeaiteam.dashboard.dto.PeriodAggregationDto;
import com.vibeaiteam.dashboard.dto.SecuritiesFirmAggregationDto;
import com.vibeaiteam.dashboard.dto.SummaryDto;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** CalculatedSale 목록을 그룹핑·합산하는 순수 집계 로직. 저장소·Spring에 의존하지 않는다. */
public final class DashboardAggregator {

  private DashboardAggregator() {}

  /** 그룹 하나의 합계. */
  private record Totals(
      long investmentAmount, long preTaxProfit, long afterTaxProfit, double profitRate, int count) {

    /** 매도 건의 투자금 = 배정금액을 매도 수량 비율로 안분한 몫. 배정금액이 단가×수량이라 단가×매도수량과 같고 항상 정수다. */
    // 총 투자금은 수익 계산의 취득금액과 같은 값(배정단가 없으면 공모가)이어야 수익률이 맞는다.
    private static long investmentOf(CalculatedSale cs) {
      return cs.acquisitionAmount() == null ? 0L : cs.acquisitionAmount();
    }

    static Totals of(List<CalculatedSale> group) {
      long investmentAmount = group.stream().mapToLong(Totals::investmentOf).sum();
      long preTaxProfit = group.stream().mapToLong(CalculatedSale::preTaxProfit).sum();
      long afterTaxProfit = group.stream().mapToLong(CalculatedSale::afterTaxProfit).sum();
      double profitRate =
          (investmentAmount == 0L) ? 0.0 : (afterTaxProfit * 100.0) / investmentAmount;
      // 참여 횟수는 분할 매도를 한 번으로 세도록 서로 다른 배정 수로 센다.
      int participation = (int) group.stream().map(CalculatedSale::allocationId).distinct().count();
      return new Totals(investmentAmount, preTaxProfit, afterTaxProfit, profitRate, participation);
    }
  }

  /** 전체를 단일 그룹으로 합산한다. 목록이 비면 모든 필드 0. */
  public static SummaryDto summarize(List<CalculatedSale> sales) {
    Totals t = Totals.of(sales);
    return SummaryDto.builder()
        .totalInvestmentAmount(t.investmentAmount())
        .totalPreTaxProfit(t.preTaxProfit())
        .totalAfterTaxProfit(t.afterTaxProfit())
        .totalProfitRate(t.profitRate())
        .participationCount(t.count())
        .build();
  }

  /** investorId로 그룹핑해 investorId 오름차순으로 반환한다. */
  public static List<InvestorAggregationDto> byInvestor(List<CalculatedSale> sales) {
    return sales.stream()
        .collect(Collectors.groupingBy(CalculatedSale::investorId))
        .entrySet()
        .stream()
        .map(
            entry -> {
              List<CalculatedSale> group = entry.getValue();
              Totals t = Totals.of(group);
              return InvestorAggregationDto.builder()
                  .investorId(entry.getKey())
                  .investorName(group.get(0).investorName())
                  .totalInvestmentAmount(t.investmentAmount())
                  .totalPreTaxProfit(t.preTaxProfit())
                  .totalAfterTaxProfit(t.afterTaxProfit())
                  .totalProfitRate(t.profitRate())
                  .participationCount(t.count())
                  .build();
            })
        .sorted(Comparator.comparing(InvestorAggregationDto::getInvestorId))
        .toList();
  }

  /** securitiesFirmId(null 제외)로 그룹핑해 securitiesFirmId 오름차순으로 반환한다. */
  public static List<SecuritiesFirmAggregationDto> bySecuritiesFirm(List<CalculatedSale> sales) {
    return sales.stream()
        .filter(cs -> cs.securitiesFirmId() != null)
        .collect(Collectors.groupingBy(CalculatedSale::securitiesFirmId))
        .entrySet()
        .stream()
        .map(
            entry -> {
              List<CalculatedSale> group = entry.getValue();
              Totals t = Totals.of(group);
              return SecuritiesFirmAggregationDto.builder()
                  .securitiesFirmId(entry.getKey())
                  .securitiesFirmName(group.get(0).securitiesFirmName())
                  .totalInvestmentAmount(t.investmentAmount())
                  .totalPreTaxProfit(t.preTaxProfit())
                  .totalAfterTaxProfit(t.afterTaxProfit())
                  .totalProfitRate(t.profitRate())
                  .participationCount(t.count())
                  .build();
            })
        .sorted(Comparator.comparing(SecuritiesFirmAggregationDto::getSecuritiesFirmId))
        .toList();
  }

  /**
   * 공모주 상장월("YYYY-MM") 또는 상장연도("YYYY") 기준(상장일이 없으면 매도일)으로 그룹핑해 period 오름차순으로 반환한다.
   *
   * @param unit "year"이면 연도, 그 외에는 월 단위
   */
  public static List<PeriodAggregationDto> byPeriod(List<CalculatedSale> sales, String unit) {
    return byPeriod(sales, unit, null, null);
  }

  /**
   * 상장일 기준 연/월로 좁힌 뒤 기간별로 집계한다. year가 null이면 전체. month는 unit이 "month"이고 year가 있을 때만 적용돼 해당 월 한 건만
   * 남기고, 그 외에는 무시한다.
   */
  public static List<PeriodAggregationDto> byPeriod(
      List<CalculatedSale> sales, String unit, Integer year, Integer month) {
    List<CalculatedSale> scoped = sales;
    if (year != null) {
      String prefix =
          month != null && !"year".equals(unit)
              ? String.format("%04d-%02d", year, month)
              : String.format("%04d", year);
      scoped = sales.stream().filter(cs -> cs.periodKey().startsWith(prefix)).toList();
    }
    return scoped.stream()
        .collect(
            Collectors.groupingBy(
                cs -> "year".equals(unit) ? cs.periodKey().substring(0, 4) : cs.periodKey()))
        .entrySet()
        .stream()
        .map(
            entry -> {
              Totals t = Totals.of(entry.getValue());
              return PeriodAggregationDto.builder()
                  .period(entry.getKey())
                  .totalInvestmentAmount(t.investmentAmount())
                  .totalPreTaxProfit(t.preTaxProfit())
                  .totalAfterTaxProfit(t.afterTaxProfit())
                  .totalProfitRate(t.profitRate())
                  .participationCount(t.count())
                  .build();
            })
        .sorted(Comparator.comparing(PeriodAggregationDto::getPeriod))
        .toList();
  }
}
