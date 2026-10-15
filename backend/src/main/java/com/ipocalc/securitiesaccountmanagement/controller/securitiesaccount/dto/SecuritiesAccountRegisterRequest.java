package com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 증권계좌 등록 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesAccountRegisterRequest {

  private Long investorId;

  private Long securitiesFirmId;

  private String accountNumber;

  private String accountName;

  private Boolean isDefault;
}
