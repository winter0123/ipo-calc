package com.vibeaiteam.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.dashboard.dto.InvestorAggregationDto;
import com.vibeaiteam.dashboard.dto.PeriodAggregationDto;
import com.vibeaiteam.dashboard.dto.SecuritiesFirmAggregationDto;
import com.vibeaiteam.dashboard.dto.SummaryDto;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DashboardAggregator 순수 집계 단위 테스트 (Spring·DB 없음). */
class DashboardAggregatorTest {

  private static long nextAllocationId = 1;

  private static CalculatedSale row(
      long investorId, Long firmId, String month, long pre, long after, Long qty, Long price) {
    return rowOf(nextAllocationId++, investorId, firmId, month, pre, after, qty, price);
  }

  private static CalculatedSale rowOf(
      long allocationId,
      long investorId,
      Long firmId,
      String month,
      long pre,
      long after,
      Long qty,
      Long price) {
    return new CalculatedSale(
        null, pre, after, investorId, firmId, month, "inv" + investorId,
        firmId == null ? null : "firm" + firmId, qty, price, allocationId);
  }

  private final List<CalculatedSale> rows =
      List.of(
          row(2, 20L, "2025-03", 100, 80, 10L, 1_000L),
          row(1, 10L, "2025-11", 50, 40, 5L, 2_000L),
          row(1, null, "2026-01", 30, 20, null, 500L));

  @Test
  void summarizeOfEmptyListIsAllZero() {
    SummaryDto s = DashboardAggregator.summarize(List.of());

    assertThat(s.getTotalInvestmentAmount()).isZero();
    assertThat(s.getTotalProfitRate()).isZero();
    assertThat(s.getParticipationCount()).isZero();
  }

  @Test
  void summarizeSumsAndTreatsNullPriceOrQuantityAsZeroInvestment() {
    SummaryDto s = DashboardAggregator.summarize(rows);

    assertThat(s.getTotalInvestmentAmount()).isEqualTo(20_000L);
    assertThat(s.getTotalPreTaxProfit()).isEqualTo(180L);
    assertThat(s.getTotalAfterTaxProfit()).isEqualTo(140L);
    assertThat(s.getTotalProfitRate()).isEqualTo(140 * 100.0 / 20_000);
    assertThat(s.getParticipationCount()).isEqualTo(3);
  }

  @Test
  void summarizeReturnsZeroRateWhenNoInvestmentAmount() {
    SummaryDto s = DashboardAggregator.summarize(List.of(row(1, 10L, "2025-01", 5, 4, null, null)));

    assertThat(s.getTotalProfitRate()).isZero();
  }

  @Test
  void byInvestorGroupsAndSortsById() {
    List<InvestorAggregationDto> result = DashboardAggregator.byInvestor(rows);

    assertThat(result).extracting(InvestorAggregationDto::getInvestorId).containsExactly(1L, 2L);
    assertThat(result.get(0).getParticipationCount()).isEqualTo(2);
    assertThat(result.get(0).getTotalAfterTaxProfit()).isEqualTo(60L);
  }

  @Test
  void bySecuritiesFirmExcludesNullFirmAndSortsById() {
    List<SecuritiesFirmAggregationDto> result = DashboardAggregator.bySecuritiesFirm(rows);

    assertThat(result)
        .extracting(SecuritiesFirmAggregationDto::getSecuritiesFirmId)
        .containsExactly(10L, 20L);
    assertThat(result.get(0).getSecuritiesFirmName()).isEqualTo("firm10");
  }

  @Test
  void byPeriodGroupsByMonthOrYearAndSortsByPeriod() {
    assertThat(DashboardAggregator.byPeriod(rows, "month"))
        .extracting(PeriodAggregationDto::getPeriod)
        .containsExactly("2025-03", "2025-11", "2026-01");

    List<PeriodAggregationDto> byYear = DashboardAggregator.byPeriod(rows, "year");
    assertThat(byYear).extracting(PeriodAggregationDto::getPeriod).containsExactly("2025", "2026");
    assertThat(byYear.get(0).getParticipationCount()).isEqualTo(2);
  }

  @Test
  void emptyInputGivesEmptyLists() {
    assertThat(DashboardAggregator.byInvestor(List.of())).isEmpty();
    assertThat(DashboardAggregator.bySecuritiesFirm(List.of())).isEmpty();
    assertThat(DashboardAggregator.byPeriod(List.of(), "month")).isEmpty();
  }

  @Test
  void splitSalesOfOneAllocationSumToAllocationAmount() {
    List<CalculatedSale> split =
        List.of(
            rowOf(7, 1, 10L, "2025-03", 60, 50, 30L, 10_000L),
            rowOf(7, 1, 10L, "2025-03", 40, 30, 70L, 10_000L));

    SummaryDto summary = DashboardAggregator.summarize(split);

    assertThat(summary.getTotalInvestmentAmount()).isEqualTo(1_000_000L);
    assertThat(summary.getTotalAfterTaxProfit()).isEqualTo(80L);
    assertThat(summary.getTotalProfitRate()).isEqualTo(80 * 100.0 / 1_000_000);
    assertThat(summary.getParticipationCount()).isEqualTo(1);
    assertThat(DashboardAggregator.byInvestor(split).get(0).getTotalInvestmentAmount())
        .isEqualTo(1_000_000L);
    assertThat(DashboardAggregator.bySecuritiesFirm(split).get(0).getTotalInvestmentAmount())
        .isEqualTo(1_000_000L);
  }

  @Test
  void differentAllocationsStillAddUp() {
    List<CalculatedSale> two =
        List.of(
            rowOf(7, 1, 10L, "2025-03", 60, 50, 10L, 1_000L),
            rowOf(8, 1, 10L, "2025-03", 40, 30, 10L, 1_000L));

    assertThat(DashboardAggregator.summarize(two).getTotalInvestmentAmount()).isEqualTo(20_000L);
  }

  @Test
  void allocationSplitAcrossMonthsProratesInvestmentByQuantitySold() {
    List<CalculatedSale> acrossMonths =
        List.of(
            rowOf(7, 1, 10L, "2025-03", 60, 50, 30L, 10_000L),
            rowOf(7, 1, 10L, "2025-04", 40, 30, 70L, 10_000L));

    List<PeriodAggregationDto> byMonth = DashboardAggregator.byPeriod(acrossMonths, "month");
    assertThat(byMonth).extracting(PeriodAggregationDto::getTotalInvestmentAmount)
        .containsExactly(300_000L, 700_000L);

    List<PeriodAggregationDto> byYear = DashboardAggregator.byPeriod(acrossMonths, "year");
    assertThat(byYear).hasSize(1);
    assertThat(byYear.get(0).getTotalInvestmentAmount()).isEqualTo(1_000_000L);
    assertThat(DashboardAggregator.summarize(acrossMonths).getTotalInvestmentAmount())
        .isEqualTo(1_000_000L);
    assertThat(byMonth)
        .extracting(PeriodAggregationDto::getParticipationCount)
        .containsExactly(1, 1);
    assertThat(byYear.get(0).getParticipationCount()).isEqualTo(1);
    assertThat(DashboardAggregator.summarize(acrossMonths).getParticipationCount()).isEqualTo(1);
  }

  @Test
  void partiallySoldAllocationCountsOnlyTheSoldShare() {
    List<CalculatedSale> partial = List.of(rowOf(7, 1, 10L, "2025-03", 60, 50, 30L, 10_000L));

    assertThat(DashboardAggregator.summarize(partial).getTotalInvestmentAmount())
        .isEqualTo(300_000L);
  }

  @Test
  void prorationOfIndivisibleAllocationAmountSumsExactly() {
    List<CalculatedSale> thirds =
        List.of(
            rowOf(7, 1, 10L, "2025-03", 1, 1, 1L, 3_333L),
            rowOf(7, 1, 10L, "2025-04", 1, 1, 1L, 3_333L),
            rowOf(7, 1, 10L, "2025-05", 1, 1, 1L, 3_333L));

    assertThat(DashboardAggregator.byPeriod(thirds, "month"))
        .extracting(PeriodAggregationDto::getTotalInvestmentAmount)
        .containsExactly(3_333L, 3_333L, 3_333L);
    assertThat(DashboardAggregator.summarize(thirds).getTotalInvestmentAmount())
        .isEqualTo(3 * 3_333L);
  }
}
