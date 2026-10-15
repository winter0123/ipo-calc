package com.ipocalc.salemanagement.controller.sale.dto;

import com.ipocalc.salemanagement.domain.model.sale.Sale;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 매도 응답 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleResponse {

  private Long id;

  private Long allocationId;

  private Long quantity;

  private Long price;

  private Long amount;

  private LocalDateTime saleDate;

  private Long fee;

  private Long tax;

  private Long otherCost;

  private String status;

  private LocalDateTime deletedAt;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  /** 엔티티를 응답 DTO로 변환한다. */
  public static SaleResponse from(Sale sale) {
    return SaleResponse.builder()
        .id(sale.getId())
        .allocationId(sale.getAllocationId())
        .quantity(sale.getQuantity())
        .price(sale.getPrice())
        .amount(sale.getAmount())
        .saleDate(sale.getSaleDate())
        .fee(sale.getFee())
        .tax(sale.getTax())
        .otherCost(sale.getOtherCost())
        .status(sale.getStatus().name())
        .deletedAt(sale.getDeletedAt())
        .createdAt(sale.getCreatedAt())
        .updatedAt(sale.getUpdatedAt())
        .build();
  }
}
