package com.vibeaiteam.investormanagement.controller.investor.dto;

import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 투자자 응답 DTO. */
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

  /** 엔티티를 응답 DTO로 변환한다. */
  public static InvestorResponse from(Investor investor) {
    return InvestorResponse.builder()
        .id(investor.getId())
        .displayName(investor.getDisplayName())
        .accountId(investor.getAccountId())
        .status(investor.getStatus().name())
        .deletedAt(investor.getDeletedAt())
        .createdAt(investor.getCreatedAt())
        .updatedAt(investor.getUpdatedAt())
        .build();
  }
}
