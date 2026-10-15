package com.ipocalc.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 대시보드 전체 요약 DTO. 총 투자금액, 세전·세후 순수익, 수익률, 참여 횟수를 담는다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryDto {

  private Long totalInvestmentAmount;

  private Long totalPreTaxProfit;

  private Long totalAfterTaxProfit;

  private Double totalProfitRate;

  private Integer participationCount;
}
