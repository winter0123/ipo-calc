package com.vibeaiteam.costrecording.controller.cost.dto;

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
public class CostUpdateRequest {

    private String costType;

    private Long amount;

    private LocalDateTime incurredAt;

    private String memo;
}