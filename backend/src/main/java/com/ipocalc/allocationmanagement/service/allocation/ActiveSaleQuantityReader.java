package com.ipocalc.allocationmanagement.service.allocation;

/** 배정 수량 검증에 필요한 매도 수량 조회 경계. */
public interface ActiveSaleQuantityReader {
  /** 배정에 연결된 활성 매도 수량을 합산한다. */
  long sumActiveSaleQuantity(Long allocationId);
}
