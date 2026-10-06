package com.vibeaiteam.allocationmanagement.service.ownership;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipErrors;
import com.vibeaiteam.subscriptionmanagement.service.ownership.SubscriptionOwnership;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 배정 → 청약 → 투자자 → 계정 소유권 검증을 한 곳에 모은다. 도메인별 예외는 {@link OwnershipErrors}로 넘긴다. */
@Component
@RequiredArgsConstructor
public class AllocationOwnership {

  /** 배정을 찾지 못했을 때의 표준 메시지. */
  public static final String ALLOCATION_NOT_FOUND = "배정 기록을 찾을 수 없습니다.";

  private final AllocationRepository allocationRepository;
  private final SubscriptionOwnership subscriptionOwnership;

  /**
   * 삭제되지 않은 배정을 조회하고 청약 경로로 현재 계정 소유인지 검증한다.
   *
   * @param id 배정 ID
   * @param errors 도메인 예외(없음 404, 타 계정 403)
   * @return 소유가 확인된 배정
   */
  public Allocation requireOwnedActive(Long id, OwnershipErrors errors) {
    Allocation allocation =
        allocationRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> errors.notFound(ALLOCATION_NOT_FOUND));
    subscriptionOwnership.requireOwnedIncludingDeleted(allocation.getSubscriptionId(), errors);
    return allocation;
  }

  /**
   * 삭제 여부와 관계없이 배정을 조회하고 청약 경로로 현재 계정 소유인지 검증한다. 이미 만든 매도의 부모를 따라 올라갈 때 쓴다.
   *
   * @param id 배정 ID
   * @param errors 도메인 예외(없음 404, 타 계정 403)
   * @return 소유가 확인된 배정
   */
  public Allocation requireOwnedIncludingDeleted(Long id, OwnershipErrors errors) {
    Allocation allocation =
        allocationRepository.findById(id).orElseThrow(() -> errors.notFound(ALLOCATION_NOT_FOUND));
    subscriptionOwnership.requireOwnedIncludingDeleted(allocation.getSubscriptionId(), errors);
    return allocation;
  }
}
