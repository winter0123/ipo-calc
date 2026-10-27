package com.ipocalc.allocationmanagement.controller.allocation.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 배정 등록 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationRegisterRequest {

  private Long subscriptionId;

  /** subscriptionId가 없을 때 청약·배정을 자동 생성할 공모주, 투자자, 계좌 정보. */
  private Long ipoId;

  private Long investorId;

  private Long securitiesAccountId;

  private Long allocatedQuantity;

  private LocalDateTime allocatedAt;

  private String allocationMethod;

  private Long allocationPrice;

  private String note;
}
