package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto;

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
public class SecuritiesAccountRegisterRequest {

    private Long investorId;

    private Long securitiesFirmId;

    private String accountNumber;

    private String accountName;

    private Boolean isDefault;
}