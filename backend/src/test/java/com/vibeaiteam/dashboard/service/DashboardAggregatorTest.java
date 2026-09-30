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

  private static CalculatedSale row(
      long investorId, Long firmId, String month, long pre, long after, Long qty, Long price) {
    return new CalculatedSale(
        null, pre, after, investorId, firmId, month, "inv" + investorId,
        firmId == null ? null : "firm" + firmId, qty, price);
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
}
