package com.vibeaiteam.salemanagement.controller.sale.dto;

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

  private Long quantity;

  private Long price;

  private Long amount;

  private LocalDateTime saleDate;

  private Long fee;

  private Long tax;

  private Long otherCost;
}
