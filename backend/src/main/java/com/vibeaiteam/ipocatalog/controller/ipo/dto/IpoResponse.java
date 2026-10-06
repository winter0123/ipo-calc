package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공모주 응답 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoResponse {

  private Long id;

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

  private String status;

  private LocalDate refundDate;

  private boolean refundDateEstimated;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  /** 엔티티를 응답 DTO로 변환한다. */
  public static IpoResponse from(Ipo ipo) {
    return IpoResponse.builder()
        .id(ipo.getId())
        .companyName(ipo.getCompanyName())
        .ipoName(ipo.getIpoName())
        .stockCode(ipo.getStockCode())
        .roundLabel(ipo.getRoundLabel())
        .lowerBandPrice(ipo.getLowerBandPrice())
        .upperBandPrice(ipo.getUpperBandPrice())
        .fixedPrice(ipo.getFixedPrice())
        .subscriptionStartDate(ipo.getSubscriptionStartDate())
        .subscriptionEndDate(ipo.getSubscriptionEndDate())
        .listingDate(ipo.getListingDate())
        .offeringShareCount(ipo.getOfferingShareCount())
        .status(ipo.getStatus().name())
        .refundDate(ipo.getRefundDate())
        .refundDateEstimated(false)
        .createdAt(ipo.getCreatedAt())
        .updatedAt(ipo.getUpdatedAt())
        .build();
  }
}
