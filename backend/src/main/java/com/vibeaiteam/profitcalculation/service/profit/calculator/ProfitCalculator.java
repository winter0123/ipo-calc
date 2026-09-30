package com.vibeaiteam.profitcalculation.service.profit.calculator;

/**
 * 수익 계산의 핵심 로직을 담은 stateless 유틸리티 클래스.
 * 각 메서드는 null-safe하며, null 파라미터는 0으로 간주한다.
 * 반올림은 Math.round(소수 첫째 자리에서 반올림 → Long)를 사용한다 (D-P-005).
 */
public final class ProfitCalculator {

  private ProfitCalculator() {
    // 유틸리티 클래스는 인스턴스화하지 않음
  }

  /**
   * 대응취득가액을 계산한다.
   * allocationPrice × saleQuantity
   * allocationPrice가 null이면 fixedPrice로 대체, 둘 다 null이면 0 반환.
   */
  public static Long calculateAcquisitionAmount(
      Long allocationPrice, Long fixedPrice, Long saleQuantity) {
    Long price = (allocationPrice != null) ? allocationPrice : fixedPrice;
    if (price == null || saleQuantity == null) {
      return 0L;
    }
    return price * saleQuantity;
  }

  /**
   * 대응취득비용을 계산한다.
   * ROUND(totalActiveCost × saleQuantity / allocatedQuantity)
   * allocatedQuantity가 0이면 0 반환 (방어적 처리).
   * Math.round로 소수 첫째 자리에서 반올림 후 Long 반환.
   */
  public static Long calculateMatchedCost(
      Long totalActiveCost, Long saleQuantity, Long allocatedQuantity) {
    if (totalActiveCost == null || saleQuantity == null || allocatedQuantity == null) {
      return 0L;
    }
    if (allocatedQuantity == 0) {
      return 0L;
    }
    if (totalActiveCost == 0 || saleQuantity == 0) {
      return 0L;
    }
    double ratio = (double) saleQuantity / (double) allocatedQuantity;
    return Math.round(totalActiveCost * ratio);
  }

  /**
   * 세전순수익을 계산한다.
   * saleAmount − acquisitionAmount − matchedCost − fee − otherCost
   * null 파라미터는 0으로 간주 (null-safe).
   */
  public static Long calculatePreTaxProfit(
      Long saleAmount, Long acquisitionAmount, Long matchedCost, Long fee, Long otherCost) {
    long sa = (saleAmount != null) ? saleAmount : 0L;
    long aa = (acquisitionAmount != null) ? acquisitionAmount : 0L;
    long mc = (matchedCost != null) ? matchedCost : 0L;
    long f = (fee != null) ? fee : 0L;
    long oc = (otherCost != null) ? otherCost : 0L;
    return sa - aa - mc - f - oc;
  }

  /**
   * 세후순수익을 계산한다.
   * preTaxProfit − tax
   * null 파라미터는 0으로 간주 (null-safe).
   */
  public static Long calculateAfterTaxProfit(Long preTaxProfit, Long tax) {
    long ptp = (preTaxProfit != null) ? preTaxProfit : 0L;
    long t = (tax != null) ? tax : 0L;
    return ptp - t;
  }

  /**
   * 안분 비율을 계산한다.
   * saleQuantity / allocatedQuantity
   * allocatedQuantity가 0이면 0.0 반환 (방어적 처리).
   */
  public static double calculateAllocationRatio(Long saleQuantity, Long allocatedQuantity) {
    if (saleQuantity == null || allocatedQuantity == null || allocatedQuantity == 0) {
      return 0.0;
    }
    return (double) saleQuantity / (double) allocatedQuantity;
  }
}
