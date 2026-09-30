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
      long investmentAmount,
      long preTaxProfit,
      long afterTaxProfit,
      double profitRate,
      int count) {

    private static long allocationAmount(CalculatedSale cs) {
      Long price = cs.allocationPrice();
      Long qty = cs.allocatedQuantity();
      return (price == null || qty == null) ? 0L : price * qty;
    }

    /** 총투자금은 그룹 안에서 같은 배정(allocationId)의 배정금액을 한 번만 더한다(D-D-001). */
    static Totals of(List<CalculatedSale> group) {
      long investmentAmount =
          group.stream()
              .collect(
                  Collectors.toMap(
                      CalculatedSale::allocationId, Totals::allocationAmount, (a, b) -> a))
              .values()
              .stream()
              .mapToLong(Long::longValue)
              .sum();
      long preTaxProfit = group.stream().mapToLong(CalculatedSale::preTaxProfit).sum();
      long afterTaxProfit = group.stream().mapToLong(CalculatedSale::afterTaxProfit).sum();
      double profitRate =
          (investmentAmount == 0L) ? 0.0 : (afterTaxProfit * 100.0) / investmentAmount;
      return new Totals(investmentAmount, preTaxProfit, afterTaxProfit, profitRate, group.size());
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
   * 매도월("YYYY-MM") 또는 매도연도("YYYY") 기준으로 그룹핑해 period 오름차순으로 반환한다.
   *
   * @param unit "year"이면 연도, 그 외에는 월 단위
   */
  public static List<PeriodAggregationDto> byPeriod(List<CalculatedSale> sales, String unit) {
    return sales.stream()
        .collect(
            Collectors.groupingBy(
                cs -> "year".equals(unit) ? cs.saleDateKey().substring(0, 4) : cs.saleDateKey()))
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
