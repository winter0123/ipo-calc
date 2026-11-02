package com.ipocalc.admin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매도 요약 응답 DTO (관리자용). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleSummaryResponse {

  private Long id;
  private String ipoName;
  private Long quantity;
  private Long price;
  private Long amount;
  private Long fee;
  private Long tax;
  private Long otherCost;
  private LocalDateTime soldAt;
  private String status;
}
