package com.ipocalc.allocationmanagement.controller.allocation.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 배정 수정 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationUpdateRequest {

  private Long subscriptionId;

  private Long allocatedQuantity;

  private LocalDateTime allocatedAt;

  private String allocationMethod;

  private Long allocationPrice;

  private String note;
}
