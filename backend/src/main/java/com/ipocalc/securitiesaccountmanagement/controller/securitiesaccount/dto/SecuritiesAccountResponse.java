package com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
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

  /** 계좌번호(마스킹/null). Jackson 전역 NON_NULL 설정 시에도 키를 포함하기 위해 ALWAYS 강제. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private String accountNumber;

  private String accountName;
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
        .status(account.getStatus().name())
        .deletedAt(account.getDeletedAt())
        .createdAt(account.getCreatedAt())
        .updatedAt(account.getUpdatedAt())
        .build();
  }
}
