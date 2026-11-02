package com.ipocalc.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 청약 요약 응답 DTO (관리자용). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSummaryResponse {

  private Long id;
  private String ipoName;
  private String companyName;
  private Long quantity;
  private Long depositAmount;
  private String subscribedAt;
  private String status;
  private Long allocatedQuantity;
  private Long remainingQuantity;
}
