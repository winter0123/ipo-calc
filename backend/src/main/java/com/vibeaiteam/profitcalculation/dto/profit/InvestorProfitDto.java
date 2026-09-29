package com.vibeaiteam.profitcalculation.dto.profit;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 투자자별 전체 수익 목록 응답 DTO. 투자자의 ACTIVE Sale 각각에 대한 ProfitDto 목록과
// 총 매도금액, 총 세전/세후 순수익을 포함한다. 합계는 단순 합산(추가 반올림 없음)이다 (D-P-003).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorProfitDto {

  private Long investorId;

  private List<ProfitDto> sales;

  private Long totalSaleAmount;

  private Long totalPreTaxProfit;

  private Long totalAfterTaxProfit;
}
