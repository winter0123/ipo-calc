package com.ipocalc.allocationmanagement.repository.allocation;

import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import java.util.List;
import java.util.Optional;

/** 배정 도메인의 조인·집계 QueryDSL 저장소. */
public interface AllocationQueryRepository {
  /** 청약에 활성 배정 결과가 있는지 확인한다. */
  boolean hasActiveAllocation(Long subscriptionId);

  /** 청약의 활성 배정 수량을 합산한다. */
  long activeAllocatedQuantity(Long subscriptionId);

  /** 갱신 잠금을 사용해 배정을 조회한다. */
  Optional<Allocation> findByIdForUpdate(Long id);

  /** 계정과 선택 필터에 해당하는 배정 내역을 조회한다. */
  List<Allocation> findAllByAccountWithFilters(
      Long accountId, Long subscriptionId, Long investorId);
}
