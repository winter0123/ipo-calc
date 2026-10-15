package com.ipocalc.subscriptionmanagement.controller.subscription.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 청약 등록 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRegisterRequest {

  private Long investorId;

  private Long securitiesAccountId;

  private Long ipoId;

  private Long quantity;

  private Long depositAmount;

  private LocalDateTime subscribedAt;

  private String memo;
}
