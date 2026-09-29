package com.vibeaiteam.investmentqueryapi.dto.investmenthistory;

import java.util.List;
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
public class InvestmentHistoryDto {

  private Long id;
  private String subscribedAt;
  private Long ipoId;
  private String ipoName;
  private Long ipoFixedPrice;
  private Long subscriptionQuantity;
  private String subscriptionStatus;
  private AllocationSummaryDto allocation;
  private List<SaleSummaryDto> sales;
  private Long remainingQuantity;
  private String investorName;
  private String securitiesFirmName;
}
