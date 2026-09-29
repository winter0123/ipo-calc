package com.vibeaiteam.costrecording.controller.cost.dto;

import java.time.LocalDateTime;
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
public class CostRegisterRequest {

  private Long subscriptionId;

  private String costType;

  private Long amount;

  private LocalDateTime incurredAt;

  private String memo;
}
