package com.ipocalc.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 대시보드 행이 수익 계산과 같은 취득금액 규칙을 쓰는지 확인한다. */
class CalculatedSaleTest {

  private static final Investor INVESTOR = Investor.builder().id(1L).displayName("홍").build();

  private static Sale sale() {
    return Sale.builder()
        .id(1L)
        .allocationId(1L)
        .quantity(10L)
        .price(12_000L)
        .amount(120_000L)
        .saleDate(LocalDateTime.of(2026, 10, 1, 9, 0))
        .fee(0L)
        .tax(0L)
        .build();
  }

  private static Ipo ipo() {
    return Ipo.builder().id(1L).fixedPrice(10_000L).listingDate(LocalDate.of(2026, 9, 30)).build();
  }

  @Test
  void investmentFallsBackToFixedPriceWhenAllocationPriceMissing() {
    Allocation noPrice = Allocation.builder().id(1L).allocationPrice(null).build();

    CalculatedSale cs = CalculatedSale.of(sale(), noPrice, INVESTOR, null, null, ipo());

    assertThat(cs.acquisitionAmount()).isEqualTo(100_000L);
    var totals = DashboardAggregator.summarize(List.of(cs));
    assertThat(totals.getTotalInvestmentAmount()).isEqualTo(100_000L);
    assertThat(totals.getTotalProfitRate()).isEqualTo(20.0);
  }

  @Test
  void allocationPriceWinsOverFixedPrice() {
    Allocation priced = Allocation.builder().id(1L).allocationPrice(9_000L).build();

    assertThat(CalculatedSale.of(sale(), priced, INVESTOR, null, null, ipo()).acquisitionAmount())
        .isEqualTo(90_000L);
  }
}
