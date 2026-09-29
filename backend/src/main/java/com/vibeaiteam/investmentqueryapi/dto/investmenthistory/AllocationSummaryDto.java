package com.vibeaiteam.investmentqueryapi.dto.investmenthistory;

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
public class AllocationSummaryDto {

  private Long id;
  private Long allocatedQuantity;
  private Long allocationPrice;
  private String allocationStatus;
}
