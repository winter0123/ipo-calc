package com.vibeaiteam.profitcalculation.dto.profit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 단일 매도건 수익 계산 응답 DTO. Profit은 파생값이므로 ID 필드를 포함하지 않는다 (D-P-001).
// 모든 금액 필드는 원 단위 Long이다 (D-P-005). null 허용 필드(fee/tax/otherCost)는 0으로 기본값 처리.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitDto {

    private Long saleId;

    private LocalDateTime saleDate;

    private Long allocationId;

    private Long quantity;

    private Long saleAmount;

    private Long matchedAcquisitionAmount;

    private Long matchedCostAmount;

    private Long saleFee;

    private Long saleTax;

    private Long saleOtherCost;

    private Long preTaxProfit;

    private Long afterTaxProfit;
}