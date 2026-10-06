package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto;

import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 증권계좌 응답 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesAccountResponse {

  private Long id;
  private Long investorId;
  private Long securitiesFirmId;
  private String securitiesFirmName;
  private String accountNumber;
  private String accountName;
  private Boolean isDefault;
  private String status;
  private LocalDateTime deletedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 엔티티를 응답 DTO로 변환한다. */
  public static SecuritiesAccountResponse from(
      SecuritiesAccount account, String maskedAccountNumber) {
    return from(account, maskedAccountNumber, null);
  }

  /** 엔티티를 응답 DTO로 변환한다. */
  public static SecuritiesAccountResponse from(
      SecuritiesAccount account, String maskedAccountNumber, String securitiesFirmName) {
    return SecuritiesAccountResponse.builder()
        .id(account.getId())
        .investorId(account.getInvestorId())
        .securitiesFirmId(account.getSecuritiesFirmId())
        .securitiesFirmName(securitiesFirmName)
        .accountNumber(maskedAccountNumber)
        .accountName(account.getAccountName())
        .isDefault(account.getIsDefault())
        .status(account.getStatus().name())
        .deletedAt(account.getDeletedAt())
        .createdAt(account.getCreatedAt())
        .updatedAt(account.getUpdatedAt())
        .build();
  }
}
