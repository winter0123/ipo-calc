package com.vibeaiteam.allocationmanagement.controller.allocation.dto;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배정 응답 DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationResponse {

  private Long id;

  private Long subscriptionId;

  private Long allocatedQuantity;

  private LocalDateTime allocatedAt;

  private String allocationMethod;

  private Long allocationPrice;

  private String note;

  private String status;

  private LocalDateTime deletedAt;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  /**
   * 엔티티를 응답 DTO로 변환한다.
   */
  public static AllocationResponse from(Allocation allocation) {
    return AllocationResponse.builder()
        .id(allocation.getId())
        .subscriptionId(allocation.getSubscriptionId())
        .allocatedQuantity(allocation.getAllocatedQuantity())
        .allocatedAt(allocation.getAllocatedAt())
        .allocationMethod(allocation.getAllocationMethod())
        .allocationPrice(allocation.getAllocationPrice())
        .note(allocation.getNote())
        .status(allocation.getStatus())
        .deletedAt(allocation.getDeletedAt())
        .createdAt(allocation.getCreatedAt())
        .updatedAt(allocation.getUpdatedAt())
        .build();
  }
}
