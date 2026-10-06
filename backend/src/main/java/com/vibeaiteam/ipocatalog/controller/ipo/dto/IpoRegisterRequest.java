package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공모주 등록 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoRegisterRequest {

  private String companyName;

  private String ipoName;

  private String stockCode;

  private String roundLabel;

  private Long lowerBandPrice;

  private Long upperBandPrice;

  private Long fixedPrice;

  private LocalDate subscriptionStartDate;

  private LocalDate subscriptionEndDate;

  private LocalDate listingDate;

  private Long offeringShareCount;
}
