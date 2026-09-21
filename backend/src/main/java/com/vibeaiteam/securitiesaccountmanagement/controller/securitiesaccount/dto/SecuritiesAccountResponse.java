package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto;

import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesAccountResponse {

    private Long id;
    private Long investorId;
    private Long securitiesFirmId;
    private String accountNumber;
    private String accountName;
    private Boolean isDefault;
    private String status;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SecuritiesAccountResponse from(SecuritiesAccount account, String maskedAccountNumber) {
        return SecuritiesAccountResponse.builder()
                .id(account.getId())
                .investorId(account.getInvestorId())
                .securitiesFirmId(account.getSecuritiesFirmId())
                .accountNumber(maskedAccountNumber)
                .accountName(account.getAccountName())
                .isDefault(account.getIsDefault())
                .status(account.getStatus())
                .deletedAt(account.getDeletedAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}