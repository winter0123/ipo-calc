package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto;

import java.time.Instant;
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
public class SecuritiesAccountAccountNumberResponse {

  private Long id;

  private String accountNumber;

  private Instant exposedAt;

  public static SecuritiesAccountAccountNumberResponse of(
      Long id, String plainAccountNumber, Instant exposedAt) {
    return SecuritiesAccountAccountNumberResponse.builder()
        .id(id)
        .accountNumber(plainAccountNumber)
        .exposedAt(exposedAt)
        .build();
  }
}
