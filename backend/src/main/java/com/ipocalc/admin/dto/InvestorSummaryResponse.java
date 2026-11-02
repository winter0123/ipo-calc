package com.ipocalc.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 투자자 요약 응답 DTO (관리자용). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorSummaryResponse {

  private Long id;
  private String displayName;
  private String relationship;
  private String status;
}
