package com.ipocalc.allocationmanagement.service.allocation;

import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.subscriptionmanagement.repository.subscription.ActiveAllocationReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 배정 저장소 조회를 청약 도메인의 조회 포트에 연결한다. */
@Component
@RequiredArgsConstructor
public class ActiveAllocationReaderAdapter implements ActiveAllocationReader {
  private final AllocationRepository allocationRepository;

  @Override
  public boolean hasActiveAllocation(Long subscriptionId) {
    return allocationRepository.hasActiveAllocation(subscriptionId);
  }

  @Override
  public long activeAllocatedQuantity(Long subscriptionId) {
    return allocationRepository.activeAllocatedQuantity(subscriptionId);
  }
}
