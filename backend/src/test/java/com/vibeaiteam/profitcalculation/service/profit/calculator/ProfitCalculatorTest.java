package com.vibeaiteam.profitcalculation.service.profit.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ProfitCalculator 단위 테스트. AC-003(취득가액 안분), AC-005(fixedPrice 대체), AC-007(잔차 허용) 시나리오를 커버한다. */
class ProfitCalculatorTest {

  // ==================== calculateAcquisitionAmount 테스트 ====================

  @Test
  void calculateAcquisitionAmount_withAllocationPrice_returnsProduct() {
    // Given: allocationPrice=12000, fixedPrice=11000, saleQuantity=30
    // When
    Long result = ProfitCalculator.calculateAcquisitionAmount(12000L, 11000L, 30L);

    // Then: allocationPrice 우선 사용
    assertThat(result).isEqualTo(360000L); // 12000 × 30
  }

  @Test
  void calculateAcquisitionAmount_withNullAllocationPrice_usesFixedPrice() {
    // Given: allocationPrice=null, fixedPrice=11000, saleQuantity=30 (AC-005)
    // When
    Long result = ProfitCalculator.calculateAcquisitionAmount(null, 11000L, 30L);

    // Then: fixedPrice로 대체
    assertThat(result).isEqualTo(330000L); // 11000 × 30
  }

  @Test
  void calculateAcquisitionAmount_withBothNull_returnsZero() {
    // Given: allocationPrice=null, fixedPrice=null (엣지케이스: 단가 모두 없음)
    // When
    Long result = ProfitCalculator.calculateAcquisitionAmount(null, null, 30L);

    // Then: 0 반환
    assertThat(result).isEqualTo(0L);
  }

  @Test
  void calculateAcquisitionAmount_withNullSaleQuantity_returnsZero() {
    // Given: saleQuantity=null
    // When
    Long result = ProfitCalculator.calculateAcquisitionAmount(12000L, 11000L, null);

    // Then: 0 반환 (null-safe)
    assertThat(result).isEqualTo(0L);
  }

  @Test
  void calculateAcquisitionAmount_withZeroSaleQuantity_returnsZero() {
    // Given: saleQuantity=0
    // When
    Long result = ProfitCalculator.calculateAcquisitionAmount(12000L, 11000L, 0L);

    // Then: 0 반환
    assertThat(result).isEqualTo(0L);
  }

  // ==================== calculatePreTaxProfit 테스트 ====================

  @Test
  void calculatePreTaxProfit_normalCase_returnsCorrectValue() {
    // Given: saleAmount=750000, acquisitionAmount=360000, fee=1500, otherCost=0
    // When
    Long result = ProfitCalculator.calculatePreTaxProfit(750000L, 360000L, 1500L, 0L);

    // Then: 750000 - 360000 - 1500 - 0 = 388500
    assertThat(result).isEqualTo(388500L);
  }

  @Test
  void calculatePreTaxProfit_withNullFeeAndOtherCost_treatsAsZero() {
    // Given: fee=null, otherCost=null (선택 필드)
    // When
    Long result = ProfitCalculator.calculatePreTaxProfit(750000L, 360000L, null, null);

    // Then: null → 0으로 간주, 750000 - 360000 - 0 - 0 = 390000
    assertThat(result).isEqualTo(390000L);
  }

  @Test
  void calculatePreTaxProfit_withAllNull_returnsZero() {
    // Given: 모든 파라미터 null
    // When
    Long result = ProfitCalculator.calculatePreTaxProfit(null, null, null, null);

    // Then: 0 반환 (null-safe)
    assertThat(result).isEqualTo(0L);
  }

  @Test
  void calculatePreTaxProfit_canBeNegative() {
    // Given: 매도금액보다 비용이 큰 경우 (손실)
    // When
    Long result = ProfitCalculator.calculatePreTaxProfit(100000L, 200000L, 1000L, 500L);

    // Then: 음수 반환 가능 (손실)
    assertThat(result).isEqualTo(-101500L);
  }

  // ==================== calculateAfterTaxProfit 테스트 ====================

  @Test
  void calculateAfterTaxProfit_withTax_returnsSubtractedValue() {
    // Given: preTaxProfit=376500, tax=5000 (AC-002)
    // When
    Long result = ProfitCalculator.calculateAfterTaxProfit(376500L, 5000L);

    // Then: 376500 - 5000 = 371500
    assertThat(result).isEqualTo(371500L);
  }

  @Test
  void calculateAfterTaxProfit_withZeroTax_returnsSameAsPreTax() {
    // Given: tax=0 또는 null
    // When
    Long result = ProfitCalculator.calculateAfterTaxProfit(376500L, 0L);

    // Then: 세전과 동일
    assertThat(result).isEqualTo(376500L);
  }

  @Test
  void calculateAfterTaxProfit_withNullTax_treatsAsZero() {
    // Given: tax=null (선택 필드)
    // When
    Long result = ProfitCalculator.calculateAfterTaxProfit(376500L, null);

    // Then: null → 0으로 간주
    assertThat(result).isEqualTo(376500L);
  }

  @Test
  void calculateAfterTaxProfit_withNullPreTaxProfit_treatsAsZero() {
    // Given: preTaxProfit=null
    // When
    Long result = ProfitCalculator.calculateAfterTaxProfit(null, 5000L);

    // Then: 0 - 5000 = -5000
    assertThat(result).isEqualTo(-5000L);
  }

  // ==================== calculateAllocationRatio 테스트 ====================

  @Test
  void calculateAllocationRatio_normalCase_returnsCorrectRatio() {
    // Given: saleQuantity=30, allocatedQuantity=100
    // When
    double result = ProfitCalculator.calculateAllocationRatio(30L, 100L);

    // Then: 0.3
    assertThat(result).isEqualTo(0.3);
  }

  @Test
  void calculateAllocationRatio_withZeroAllocatedQuantity_returnsZero() {
    // Given: allocatedQuantity=0 (방어적 처리)
    // When
    double result = ProfitCalculator.calculateAllocationRatio(30L, 0L);

    // Then: 0.0 반환 (나눗셈 오류 방지)
    assertThat(result).isEqualTo(0.0);
  }

  @Test
  void calculateAllocationRatio_withNullParameters_returnsZero() {
    // Given: 파라미터가 null인 경우
    // When/Then
    assertThat(ProfitCalculator.calculateAllocationRatio(null, 100L)).isEqualTo(0.0);
    assertThat(ProfitCalculator.calculateAllocationRatio(30L, null)).isEqualTo(0.0);
    assertThat(ProfitCalculator.calculateAllocationRatio(null, null)).isEqualTo(0.0);
  }

  @Test
  void calculateAllocationRatio_withEqualQuantities_returnsOne() {
    // Given: saleQuantity=100, allocatedQuantity=100 (단일 매도)
    // When
    double result = ProfitCalculator.calculateAllocationRatio(100L, 100L);

    // Then: 1.0
    assertThat(result).isEqualTo(1.0);
  }
}
