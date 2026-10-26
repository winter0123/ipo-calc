package com.ipocalc.subscriptionmanagement.repository.subscription;

/** 청약 검증에 필요한 배정 결과 조회 경계. */
public interface ActiveAllocationReader {
  /** 청약에 활성 배정 결과가 있는지 확인한다. */
  boolean hasActiveAllocation(Long subscriptionId);

  /** 청약의 활성 배정 수량을 합산한다. */
  long activeAllocatedQuantity(Long subscriptionId);
}
