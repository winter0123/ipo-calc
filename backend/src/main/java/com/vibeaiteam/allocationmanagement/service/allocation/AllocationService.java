package com.vibeaiteam.allocationmanagement.service.allocation;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationRegisterRequest;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationResponse;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationUpdateRequest;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationConflictException;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationForbiddenException;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationNotFoundException;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationValidationException;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.allocationmanagement.service.ownership.AllocationOwnership;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipErrors;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.vibeaiteam.subscriptionmanagement.service.ownership.SubscriptionOwnership;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배정 관리 서비스. */
@Service
@RequiredArgsConstructor
public class AllocationService {

  private static final OwnershipErrors OWNERSHIP_ERRORS =
      new OwnershipErrors(AllocationNotFoundException::new, AllocationForbiddenException::new);

  private final AllocationRepository allocationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionOwnership subscriptionOwnership;
  private final AllocationOwnership allocationOwnership;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /** 배정을 등록한다. */
  @Transactional
  public AllocationResponse register(AllocationRegisterRequest request) {
    validateRequest(request);

    // Subscription 존재(404) + 소유권(403) 검증
    Subscription subscription = findOwnedSubscription(request.getSubscriptionId());

    validateQuantity(request.getAllocatedQuantity(), subscription.getQuantity());

    checkDuplicate(request.getSubscriptionId());

    LocalDateTime now = LocalDateTime.now(clock);
    Allocation allocation =
        Allocation.builder()
            .subscriptionId(request.getSubscriptionId())
            .allocatedQuantity(request.getAllocatedQuantity())
            .allocatedAt(request.getAllocatedAt())
            .allocationMethod(request.getAllocationMethod())
            .allocationPrice(request.getAllocationPrice())
            .note(request.getNote())
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    try {
      Allocation saved = allocationRepository.save(allocation);
      return AllocationResponse.from(saved);
    } catch (DataIntegrityViolationException ex) {
      // AC-005: 부분 유니크 인덱스(uk_allocation_active_subscription)가 최종 방어선.
      // Service 중복 검사를 놓친 동시 요청이 유니크 위반을 일으키면 409로 변환한다.
      throw new AllocationConflictException("이미 등록된 배정 기록입니다.");
    }
  }

  private void validateRequest(AllocationRegisterRequest request) {
    if (request == null) {
      throw new AllocationValidationException("배정 등록 정보는 필수입니다.");
    }
    if (request.getSubscriptionId() == null
        || request.getAllocatedQuantity() == null
        || request.getAllocatedAt() == null) {
      throw new AllocationValidationException(
          "subscriptionId, allocatedQuantity, allocatedAt는 필수입니다.");
    }
  }

  // AC-003: 배정수량은 0 이상이며, 청약수량(subscription.quantity)을 초과할 수 없다 (D-A-005, 단순 값 검증).
  private void validateQuantity(Long allocatedQuantity, Long subscriptionQuantity) {
    if (allocatedQuantity < 0) {
      throw new AllocationValidationException("allocatedQuantity는 0 이상이어야 합니다.");
    }
    if (allocatedQuantity > subscriptionQuantity) {
      throw new AllocationValidationException("allocatedQuantity는 청약수량을 초과할 수 없습니다.");
    }
  }

  private void checkDuplicate(Long subscriptionId) {
    // AC-005: 동일 subscriptionId의 ACTIVE 배정 기록이 이미 있으면 409 (AC-006: CANCELLED는 무관)
    allocationRepository
        .findBySubscriptionIdAndStatusAndDeletedAtIsNull(subscriptionId, RecordStatus.ACTIVE)
        .ifPresent(
            existing -> {
              throw new AllocationConflictException("이미 등록된 배정 기록입니다.");
            });
  }

  /** 현재 계정 소유 투자자의 배정 목록을 조회한다. 타 계정 소유 ID를 필터로 주면 빈 목록이다. */
  @Transactional(readOnly = true)
  public List<AllocationResponse> list(Long subscriptionId, Long investorId) {
    return allocationRepository
        .findAllByAccountWithFilters(
            securityUtils.getCurrentAccountId(), subscriptionId, investorId)
        .stream()
        .map(AllocationResponse::from)
        .toList();
  }

  /** ID로 배정을 조회한다. 없으면 404, 타 계정 소유면 403. */
  @Transactional(readOnly = true)
  public AllocationResponse getById(Long id) {
    return AllocationResponse.from(findOwnedAllocation(id));
  }

  /**
   * SF-4: 배정 기록의 일부 필드만 부분 수정한다(SubscriptionService.update / IpoService.update와 동일한 패턴). 제공된 필드만
   * 갱신하고, subscriptionId가 새로 제공된 경우에만 존재 여부를 재검증한다. 시점 제약은 없음(D-A-007). 배정수량은 이미 매도한
   * 수량보다 작게 바꿀 수 없다.
   */
  @Transactional
  public AllocationResponse update(Long id, AllocationUpdateRequest request) {
    Allocation allocation = findOwnedAllocation(id);

    Long updatedSubscriptionId = request.getSubscriptionId();
    Long updatedAllocatedQuantity = request.getAllocatedQuantity();
    final LocalDateTime updatedAllocatedAt = request.getAllocatedAt();
    final String updatedAllocationMethod = request.getAllocationMethod();
    final Long updatedAllocationPrice = request.getAllocationPrice();
    final String updatedNote = request.getNote();

    if (updatedAllocatedQuantity != null && updatedAllocatedQuantity < 0) {
      throw new AllocationValidationException("allocatedQuantity는 0 이상이어야 합니다.");
    }

    Long finalSubscriptionId =
        updatedSubscriptionId != null ? updatedSubscriptionId : allocation.getSubscriptionId();

    // subscriptionId가 새로 제공된 경우에만 존재 여부 재검증 (미제공 시 기존값 유지, 재검증하지 않음)
    if (updatedSubscriptionId != null) {
      Subscription subscription = findOwnedSubscription(updatedSubscriptionId);
      if (updatedAllocatedQuantity != null
          && updatedAllocatedQuantity > subscription.getQuantity()) {
        throw new AllocationValidationException("allocatedQuantity는 청약수량을 초과할 수 없습니다.");
      }
    } else if (updatedAllocatedQuantity != null) {
      // subscriptionId 변경 없으나 allocatedQuantity만 변경된 경우 기존 subscription 기준으로 검증
      Subscription subscription =
          subscriptionRepository
              .findByIdAndDeletedAtIsNull(allocation.getSubscriptionId())
              .orElseThrow(() -> new AllocationNotFoundException("청약 기록을 찾을 수 없습니다."));
      if (updatedAllocatedQuantity > subscription.getQuantity()) {
        throw new AllocationValidationException("allocatedQuantity는 청약수량을 초과할 수 없습니다.");
      }
    }

    if (updatedAllocatedQuantity != null) {
      long soldQuantity = allocationRepository.sumActiveSaleQuantity(allocation.getId());
      if (updatedAllocatedQuantity < soldQuantity) {
        throw new AllocationValidationException(
            "배정수량은 이미 매도한 수량(" + soldQuantity + "주)보다 작을 수 없습니다.");
      }
    }

    if (updatedSubscriptionId != null) {
      allocation.setSubscriptionId(updatedSubscriptionId);
    }
    if (updatedAllocatedQuantity != null) {
      allocation.setAllocatedQuantity(updatedAllocatedQuantity);
    }
    if (updatedAllocatedAt != null) {
      allocation.setAllocatedAt(updatedAllocatedAt);
    }
    if (updatedAllocationMethod != null) {
      allocation.setAllocationMethod(updatedAllocationMethod);
    }
    if (updatedAllocationPrice != null) {
      allocation.setAllocationPrice(updatedAllocationPrice);
    }
    if (updatedNote != null) {
      allocation.setNote(updatedNote);
    }

    allocation.setUpdatedAt(LocalDateTime.now(clock));
    try {
      Allocation saved = allocationRepository.save(allocation);
      return AllocationResponse.from(saved);
    } catch (DataIntegrityViolationException ex) {
      throw new AllocationConflictException("이미 등록된 배정 기록입니다.");
    }
  }

  /**
   * SF-5: 배정 기록을 논리 취소한다(SubscriptionService.cancel / IpoService.deactivate와 동일 패턴). status를
   * CANCELLED로 변경하고 deletedAt/updatedAt을 갱신하며, 물리 삭제는 수행하지 않는다(AC-016). 존재하지 않거나 이미 취소된 기록은
   * findByIdAndDeletedAtIsNull이 걸러내 404를 반환한다(AC-017, 재취소는 자연 배제).
   */
  @Transactional
  public void cancel(Long id) {
    Allocation allocation = findOwnedAllocation(id);
    if (allocationRepository.sumActiveSaleQuantity(id) > 0) {
      throw new AllocationConflictException("매도 기록이 있는 배정은 취소할 수 없습니다. 매도를 먼저 삭제해 주세요.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    allocation.setStatus(RecordStatus.CANCELLED);
    allocation.setDeletedAt(now);
    allocation.setUpdatedAt(now);
    allocationRepository.save(allocation);
  }

  // 미삭제 청약 조회 + 소유 투자자 검증 — 없으면 404, 타 계정 소유면 403
  private Subscription findOwnedSubscription(Long subscriptionId) {
    return subscriptionOwnership.requireOwnedActive(subscriptionId, OWNERSHIP_ERRORS);
  }

  // 미삭제 배정 조회 + 소유권 검증(배정 → 청약 → 투자자) — 없으면 404, 타 계정 소유면 403
  private Allocation findOwnedAllocation(Long id) {
    return allocationOwnership.requireOwnedActive(id, OWNERSHIP_ERRORS);
  }
}
