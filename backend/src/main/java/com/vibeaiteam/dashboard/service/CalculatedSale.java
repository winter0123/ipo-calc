package com.vibeaiteam.dashboard.service;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.profitcalculation.service.profit.calculator.ProfitCalculator;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import java.time.format.DateTimeFormatter;

/** 계산용 내부 레코드: Sale + 세전/세후순수익 + 그룹핑 키 + 추가 정보. */
public record CalculatedSale(
    Sale sale,
    Long preTaxProfit,
    Long afterTaxProfit,
    Long investorId,
    Long securitiesFirmId,
    String periodKey, // "YYYY-MM": 공모주 상장일 기준(상장일이 없으면 매도일)
    String investorName,
    String securitiesFirmName,
    Long soldQuantity,
    Long allocationPrice,
    Long allocationId) {

  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

  /**
   * 매도 1건과 연관 엔티티로 세전/세후순수익을 계산해 CalculatedSale을 만든다. 저장소·Spring에 의존하지 않는다.
   *
   * @param ipo 없으면 null (allocationPrice가 null일 때 fixedPrice 대체용)
   * @param securitiesFirmId 증권계좌가 없으면 null
   * @param securitiesFirmName securitiesFirmId가 null이면 null
   */
  public static CalculatedSale of(
      Sale sale,
      Allocation allocation,
      Investor investor,
      Long securitiesFirmId,
      String securitiesFirmName,
      Ipo ipo) {
    Long acquisitionAmount =
        ProfitCalculator.calculateAcquisitionAmount(
            allocation.getAllocationPrice(),
            ipo != null ? ipo.getFixedPrice() : null,
            sale.getQuantity());

    Long preTaxProfit =
        ProfitCalculator.calculatePreTaxProfit(
            sale.getAmount(), acquisitionAmount, sale.getFee(), sale.getOtherCost());

    Long afterTaxProfit = ProfitCalculator.calculateAfterTaxProfit(preTaxProfit, sale.getTax());

    return new CalculatedSale(
        sale,
        preTaxProfit,
        afterTaxProfit,
        investor.getId(),
        securitiesFirmId,
        // 사용자 결정(2026-10-13): 10월 상장 공모주를 11월에 팔아도 수익은 10월로 잡는다.
        ipo != null && ipo.getListingDate() != null
            ? ipo.getListingDate().format(MONTH_FORMATTER)
            : sale.getSaleDate().format(MONTH_FORMATTER),
        investor.getDisplayName(),
        securitiesFirmName,
        sale.getQuantity(),
        allocation.getAllocationPrice(),
        allocation.getId());
  }
}
