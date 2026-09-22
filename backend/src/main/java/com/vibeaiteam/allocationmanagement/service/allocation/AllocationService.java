package com.vibeaiteam.allocationmanagement.service.allocation;

import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationRegisterRequest;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationResponse;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationUpdateRequest;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationConflictException;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationNotFoundException;
import com.vibeaiteam.allocationmanagement.controller.allocation.exception.AllocationValidationException;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AllocationService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AllocationRepository allocationRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public AllocationResponse register(AllocationRegisterRequest request) {
        validateRequest(request);

        // Subscription 존재 검증(소유권 검증 불필요 — Allocation은 SecuritiesFirm/Ipo와 동일한 글로벌 기준정보,
        // architecture.md "계정 소유권 검증 관련 결정" 참고). 없으면 404.
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(request.getSubscriptionId())
                .orElseThrow(() -> new AllocationNotFoundException("청약 기록을 찾을 수 없습니다."));

        validateQuantity(request.getAllocatedQuantity(), subscription.getQuantity());

        checkDuplicate(request.getSubscriptionId());

        LocalDateTime now = LocalDateTime.now();
        Allocation allocation = Allocation.builder()
                .subscriptionId(request.getSubscriptionId())
                .allocatedQuantity(request.getAllocatedQuantity())
                .allocatedAt(request.getAllocatedAt())
                .allocationMethod(request.getAllocationMethod())
                .allocationPrice(request.getAllocationPrice())
                .note(request.getNote())
                .status(STATUS_ACTIVE)
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
            throw new AllocationValidationException("subscriptionId, allocatedQuantity, allocatedAt는 필수입니다.");
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
        allocationRepository.findBySubscriptionIdAndStatusAndDeletedAtIsNull(subscriptionId, STATUS_ACTIVE)
                .ifPresent(existing -> {
                    throw new AllocationConflictException("이미 등록된 배정 기록입니다.");
                });
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> list(Long subscriptionId, Long investorId) {
        List<Allocation> allocations;
        if (subscriptionId != null) {
            allocations = allocationRepository.findBySubscriptionIdAndDeletedAtIsNullOrderByCreatedAtDesc(subscriptionId);
        } else if (investorId != null) {
            allocations = allocationRepository.findByInvestorId(investorId);
        } else {
            allocations = allocationRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return allocations.stream()
                .map(AllocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AllocationResponse getById(Long id) {
        Allocation allocation = allocationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AllocationNotFoundException("배정 기록을 찾을 수 없습니다."));
        return AllocationResponse.from(allocation);
    }

    // SF-4: 배정 기록의 일부 필드만 부분 수정한다(SubscriptionService.update / IpoService.update와 동일한 패턴).
    // 제공된 필드만 갱신하고, subscriptionId가 새로 제공된 경우에만 존재 여부를 재검증한다.
    // 시점 제약은 없음(D-A-007). 매도(Sale) 연결 시 수량 축소 제한은 Epic 8 구현 시 함께 추가할 예정으로
    // 이번 SF에서는 확장 지점만 남겨 둔다(work-log에 "Epic 8 구현 시 추가 예정" 명시).
    @Transactional
    public AllocationResponse update(Long id, AllocationUpdateRequest request) {
        Allocation allocation = allocationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AllocationNotFoundException("배정 기록을 찾을 수 없습니다."));

        Long updatedSubscriptionId = request.getSubscriptionId();
        Long updatedAllocatedQuantity = request.getAllocatedQuantity();
        LocalDateTime updatedAllocatedAt = request.getAllocatedAt();
        String updatedAllocationMethod = request.getAllocationMethod();
        Long updatedAllocationPrice = request.getAllocationPrice();
        String updatedNote = request.getNote();

        if (updatedAllocatedQuantity != null && updatedAllocatedQuantity < 0) {
            throw new AllocationValidationException("allocatedQuantity는 0 이상이어야 합니다.");
        }

        Long finalSubscriptionId = updatedSubscriptionId != null ? updatedSubscriptionId : allocation.getSubscriptionId();

        // subscriptionId가 새로 제공된 경우에만 존재 여부 재검증 (미제공 시 기존값 유지, 재검증하지 않음)
        if (updatedSubscriptionId != null) {
            Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(updatedSubscriptionId)
                    .orElseThrow(() -> new AllocationNotFoundException("청약 기록을 찾을 수 없습니다."));
            if (updatedAllocatedQuantity != null && updatedAllocatedQuantity > subscription.getQuantity()) {
                throw new AllocationValidationException("allocatedQuantity는 청약수량을 초과할 수 없습니다.");
            }
        } else if (updatedAllocatedQuantity != null) {
            // subscriptionId 변경 없으나 allocatedQuantity만 변경된 경우 기존 subscription 기준으로 검증
            Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(allocation.getSubscriptionId())
                    .orElseThrow(() -> new AllocationNotFoundException("청약 기록을 찾을 수 없습니다."));
            if (updatedAllocatedQuantity > subscription.getQuantity()) {
                throw new AllocationValidationException("allocatedQuantity는 청약수량을 초과할 수 없습니다.");
            }
        }

        // 매도(Sale) 연결 시 수량 축소 제한 — Epic 8 구현 시 SaleService를 주입받아 확장 예정.
        // 현재는 allocationId 기반 Subscription의 quantity 비교 검증만 수행한다.
        // TODO(Epic 8): SaleService 주입 후 연결된 매도수량 합계 < 신규 allocatedQuantity 시 400 반환 로직 추가

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

        allocation.setUpdatedAt(LocalDateTime.now());
        try {
            Allocation saved = allocationRepository.save(allocation);
            return AllocationResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new AllocationConflictException("이미 등록된 배정 기록입니다.");
        }
    }

    // SF-5: 배정 기록을 논리 취소한다(SubscriptionService.cancel / IpoService.deactivate와 동일 패턴).
    // status를 CANCELLED로 변경하고 deletedAt/updatedAt을 갱신하며, 물리 삭제는 수행하지 않는다(AC-016).
    // 존재하지 않거나 이미 취소된 기록은 findByIdAndDeletedAtIsNull이 걸러내 404를 반환한다(AC-017, 재취소는 자연 배제).
    @Transactional
    public void cancel(Long id) {
        Allocation allocation = allocationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AllocationNotFoundException("배정 기록을 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now();
        allocation.setStatus("CANCELLED");
        allocation.setDeletedAt(now);
        allocation.setUpdatedAt(now);
        allocationRepository.save(allocation);
    }
}
