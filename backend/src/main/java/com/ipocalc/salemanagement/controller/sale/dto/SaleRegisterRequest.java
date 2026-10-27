package com.ipocalc.salemanagement.controller.sale.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 매도 등록 요청 DTO. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleRegisterRequest {

  private Long allocationId;

  /** allocationId 없이 새 연결을 만들 때 필요한 정보. */
  private Long subscriptionId;

  private Long ipoId;

  private Long investorId;

  private Long securitiesAccountId;

  private Long allocatedQuantity;

  private Long quantity;

  private Long price;

  private Long amount;

  private LocalDateTime saleDate;

  private Long fee;

  private Long tax;

  private Long otherCost;
}
