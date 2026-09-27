package com.vibeaiteam.costrecording.controller.cost.dto;

import com.vibeaiteam.costrecording.domain.model.cost.Cost;
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
public class CostResponse {

    private Long id;

    private Long subscriptionId;

    private String costType;

    private Long amount;

    private LocalDateTime incurredAt;

    private String memo;

    private String status;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static CostResponse from(Cost cost) {
        return CostResponse.builder()
                .id(cost.getId())
                .subscriptionId(cost.getSubscriptionId())
                .costType(cost.getCostType())
                .amount(cost.getAmount())
                .incurredAt(cost.getIncurredAt())
                .memo(cost.getMemo())
                .status(cost.getStatus())
                .deletedAt(cost.getDeletedAt())
                .createdAt(cost.getCreatedAt())
                .updatedAt(cost.getUpdatedAt())
                .build();
    }
}