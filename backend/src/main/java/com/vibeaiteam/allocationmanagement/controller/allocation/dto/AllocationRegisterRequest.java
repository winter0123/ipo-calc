package com.vibeaiteam.allocationmanagement.controller.allocation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationRegisterRequest {

    private Long subscriptionId;

    private Long allocatedQuantity;

    private LocalDateTime allocatedAt;

    private String allocationMethod;

    private Long allocationPrice;

    private String note;
}
