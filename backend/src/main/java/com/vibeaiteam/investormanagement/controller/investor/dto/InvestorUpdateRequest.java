package com.vibeaiteam.investormanagement.controller.investor.dto;

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
public class InvestorUpdateRequest {

  private String displayName;

  private String relationship;
}
