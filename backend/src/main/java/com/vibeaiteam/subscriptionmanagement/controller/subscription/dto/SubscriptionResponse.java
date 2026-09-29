package com.vibeaiteam.subscriptionmanagement.controller.subscription.dto;

import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
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
public class SubscriptionResponse {

  private Long id;

  private Long investorId;

  private Long securitiesAccountId;

  private Long ipoId;

  private Long quantity;

  private Long depositAmount;

  private LocalDateTime subscribedAt;

  private String memo;

  private String status;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  public static SubscriptionResponse from(Subscription subscription) {
    return SubscriptionResponse.builder()
        .id(subscription.getId())
        .investorId(subscription.getInvestorId())
        .securitiesAccountId(subscription.getSecuritiesAccountId())
        .ipoId(subscription.getIpoId())
        .quantity(subscription.getQuantity())
        .depositAmount(subscription.getDepositAmount())
        .subscribedAt(subscription.getSubscribedAt())
        .memo(subscription.getMemo())
        .status(subscription.getStatus())
        .createdAt(subscription.getCreatedAt())
        .updatedAt(subscription.getUpdatedAt())
        .build();
  }
}
