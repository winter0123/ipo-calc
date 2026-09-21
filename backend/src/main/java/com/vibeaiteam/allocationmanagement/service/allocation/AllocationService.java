package com.vibeaiteam.allocationmanagement.service.allocation;

import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationRegisterRequest;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationResponse;
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
}
