package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto;

import java.time.Instant;
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
public class SecuritiesAccountAccountNumberResponse {

  private Long id;

  private String accountNumber;

  private Instant exposedAt;

  /** 평문 계좌번호와 노출 시각으로 응답을 만든다. */
  public static SecuritiesAccountAccountNumberResponse of(
      Long id, String plainAccountNumber, Instant exposedAt) {
    return SecuritiesAccountAccountNumberResponse.builder()
        .id(id)
        .accountNumber(plainAccountNumber)
        .exposedAt(exposedAt)
        .build();
  }
}
