package com.vibeaiteam.investmentqueryapi.dto.investmenthistory;

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
public class SaleSummaryDto {

    private Long id;
    private Long quantity;
    private Long amount;
    private Long fee;
    private Long tax;
    private Long otherCost;
    private String status;
    private String soldAt;
}