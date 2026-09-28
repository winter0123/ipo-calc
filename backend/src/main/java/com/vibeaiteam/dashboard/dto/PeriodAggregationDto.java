package com.vibeaiteam.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodAggregationDto {

    private String period;

    private Long totalInvestmentAmount;

    private Long totalPreTaxProfit;

    private Long totalAfterTaxProfit;

    private Double totalProfitRate;

    private Integer participationCount;
}