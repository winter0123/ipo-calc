package com.vibeaiteam.investormanagement.controller.investor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 투자자 등록 요청 DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorRegisterRequest {

  private String displayName;

  private String relationship;
}
