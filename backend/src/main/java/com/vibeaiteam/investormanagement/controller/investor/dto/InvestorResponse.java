package com.vibeaiteam.investormanagement.controller.investor.dto;

import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
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
public class InvestorResponse {

  private Long id;
  private String displayName;
  private Long accountId;
  private String status;
  private LocalDateTime deletedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static InvestorResponse from(Investor investor) {
    return InvestorResponse.builder()
        .id(investor.getId())
        .displayName(investor.getDisplayName())
        .accountId(investor.getAccountId())
        .status(investor.getStatus())
        .deletedAt(investor.getDeletedAt())
        .createdAt(investor.getCreatedAt())
        .updatedAt(investor.getUpdatedAt())
        .build();
  }
}
