package com.ipocalc.profitcalculation.service.profit.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 수익 계산의 핵심 로직을 담은 stateless 유틸리티 클래스. 각 메서드는 null-safe하며, null 파라미터는 0으로 간주한다. 반올림은 원 단위 HALF_UP을
 * 사용한다 (D-P-005). 금액 상한이 2^53을 넘어 double로는 원 단위 정밀도를 보장할 수 없으므로 BigDecimal 정수 연산을 쓴다.
 */
public final class ProfitCalculator {

  private ProfitCalculator() {
    // 유틸리티 클래스는 인스턴스화하지 않음
  }

  /**
   * 대응취득가액을 계산한다. 배정 총액(allocatedAmount)을 배정수량 대비 매도수량 비율로 안분하고 원 단위로 반올림한다. 배정 총액이 null이면
   * 확정공모가(fixedPrice) × 매도수량으로 대체하며, 둘 다 null이면 0을 반환한다.
   */
  public static Long calculateAcquisitionAmount(
      Long allocatedAmount, Long allocatedQuantity, Long fixedPrice, Long saleQuantity) {
    if (saleQuantity == null) {
      return 0L;
    }
    if (allocatedAmount != null && allocatedQuantity != null) {
      if (allocatedQuantity <= 0) {
        return 0L;
      }
      return BigDecimal.valueOf(allocatedAmount)
          .multiply(BigDecimal.valueOf(saleQuantity))
          .divide(BigDecimal.valueOf(allocatedQuantity), 0, RoundingMode.HALF_UP)
          .longValueExact();
    }
    if (fixedPrice == null) {
      return 0L;
    }
    return fixedPrice * saleQuantity;
  }

  /**
   * 세전순수익을 계산한다. saleAmount − acquisitionAmount − fee − otherCost null 파라미터는 0으로 간주 (null-safe).
   */
  public static Long calculatePreTaxProfit(
      Long saleAmount, Long acquisitionAmount, Long fee, Long otherCost) {
    long sa = (saleAmount != null) ? saleAmount : 0L;
    long aa = (acquisitionAmount != null) ? acquisitionAmount : 0L;
    long f = (fee != null) ? fee : 0L;
    long oc = (otherCost != null) ? otherCost : 0L;
    return sa - aa - f - oc;
  }

  /** 세후순수익을 계산한다. preTaxProfit − tax null 파라미터는 0으로 간주 (null-safe). */
  public static Long calculateAfterTaxProfit(Long preTaxProfit, Long tax) {
    long ptp = (preTaxProfit != null) ? preTaxProfit : 0L;
    long t = (tax != null) ? tax : 0L;
    return ptp - t;
  }

  /**
   * 실현손익을 계산한다. saleAmount − acquisitionAmount − tax − otherCost 공모주수수료(fee)는 차감하지 않으며, null 파라미터는
   * 0으로 간주 (null-safe).
   */
  public static Long calculateRealizedProfit(
      Long saleAmount, Long acquisitionAmount, Long tax, Long otherCost) {
    long sa = (saleAmount != null) ? saleAmount : 0L;
    long aa = (acquisitionAmount != null) ? acquisitionAmount : 0L;
    long t = (tax != null) ? tax : 0L;
    long oc = (otherCost != null) ? otherCost : 0L;
    return sa - aa - t - oc;
  }

  /** 안분 비율을 계산한다. saleQuantity / allocatedQuantity allocatedQuantity가 0이면 0.0 반환 (방어적 처리). */
  public static double calculateAllocationRatio(Long saleQuantity, Long allocatedQuantity) {
    if (saleQuantity == null || allocatedQuantity == null || allocatedQuantity == 0) {
      return 0.0;
    }
    return (double) saleQuantity / (double) allocatedQuantity;
  }
}
