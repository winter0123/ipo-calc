package com.ipocalc.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배정 요약 응답 DTO (관리자용). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationSummaryResponse {

  private Long id;
  private String ipoName;
  private Long allocatedQuantity;
  private String allocatedAt;
  private String allocationMethod;
  private Long allocatedAmount;
  private String status;
}
