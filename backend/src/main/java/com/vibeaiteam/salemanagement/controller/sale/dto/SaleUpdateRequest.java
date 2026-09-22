package com.vibeaiteam.salemanagement.controller.sale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleUpdateRequest {

    private Long quantity;

    private Long price;

    private Long amount;

    private LocalDateTime saleDate;

    private Long fee;

    private Long tax;

    private Long otherCost;
}