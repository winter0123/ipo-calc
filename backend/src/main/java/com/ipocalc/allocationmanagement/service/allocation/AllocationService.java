package com.ipocalc.allocationmanagement.service.allocation;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.exception.HasChildRecordsException;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.accountauthentication.util.validation.InputLimits;
import com.ipocalc.allocationmanagement.controller.allocation.dto.AllocationRegisterRequest;
import com.ipocalc.allocationmanagement.controller.allocation.dto.AllocationResponse;
import com.ipocalc.allocationmanagement.controller.allocation.dto.AllocationUpdateRequest;
import com.ipocalc.allocationmanagement.controller.allocation.exception.AllocationConflictException;
import com.ipocalc.allocationmanagement.controller.allocation.exception.AllocationForbiddenException;
import com.ipocalc.allocationmanagement.controller.allocation.exception.AllocationNotFoundException;
import com.ipocalc.allocationmanagement.controller.allocation.exception.AllocationValidationException;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.allocationmanagement.service.ownership.AllocationOwnership;
import com.ipocalc.investormanagement.service.ownership.OwnershipErrors;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.BusinessDayCalculator;
import com.ipocalc.subscriptionmanagement.controller.subscription.dto.SubscriptionRegisterRequest;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.subscriptionmanagement.service.ownership.SubscriptionOwnership;
import com.ipocalc.subscriptionmanagement.service.subscription.SubscriptionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배정 관리 서비스. */
@Service
@RequiredArgsConstructor
public class AllocationService {

  private static final int TEXT_MAX_LENGTH = 255;

  private static final OwnershipErrors OWNERSHIP_ERRORS =
      new OwnershipErrors(AllocationNotFoundException::new, AllocationForbiddenException::new);

  private final AllocationRepository allocationRepository;
  private final ActiveSaleQuantityReader activeSaleQuantityReader;
  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionService subscriptionService;
  private final SubscriptionOwnership subscriptionOwnership;
  private final AllocationOwnership allocationOwnership;
  private final IpoRepository ipoRepository;
  private final BusinessDayCalculator businessDayCalculator;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /** 배정을 등록한다. */
  @Transactional
  public AllocationResponse register(AllocationRegisterRequest request) {
    validateRequest(request);

    Long subscriptionId = request.getSubscriptionId();
    if (subscriptionId == null) {
      if (request.getIpoId() == null
          || request.getInvestorId() == null
          || request.getSecuritiesAccountId() == null) {
        throw new AllocationValidationException(
            "청약 없이 배정을 등록하려면 ipoId, investorId, securitiesAccountId가 필요합니다.");
      }
      subscriptionId =
          subscriptionRepository
              .findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
                  request.getInvestorId(),
                  request.getSecuritiesAccountId(),
                  request.getIpoId(),
                  RecordStatus.ACTIVE)
              .map(Subscription::getId)
              .orElseGet(
                  () ->
                      subscriptionService
                          .register(
                              SubscriptionRegisterRequest.builder()
                                  .investorId(request.getInvestorId())
                                  .securitiesAccountId(request.getSecuritiesAccountId())
                                  .ipoId(request.getIpoId())
                                  .quantity(request.getAllocatedQuantity())
                                  .depositAmount(0L)
                                  .subscribedAt(LocalDateTime.now(clock))
                                  .memo("매도/배정 등록에서 자동 생성된 청약 연결 기록")
                                  .build())
                          .getId());
    }

    // 청약수량 대비 검증을 직렬화하려고 청약 행을 먼저 잠근다
    subscriptionRepository.findByIdForUpdate(subscriptionId);

    // Subscription 존재(404) + 소유권(403) 검증
    Subscription subscription = findOwnedSubscription(subscriptionId);

    validateQuantity(request.getAllocatedQuantity(), subscription.getQuantity());

    checkDuplicate(subscriptionId);

    LocalDateTime now = LocalDateTime.now(clock);
    Ipo ipo = findIpo(subscription);
    Long computedAllocatedAmount = computeAllocatedAmount(ipo, request.getAllocatedQuantity());
    LocalDateTime computedAllocatedAt = resolveAllocatedAt(ipo, now);

    Allocation allocation =
        Allocation.builder()
            .subscriptionId(subscriptionId)
            .allocatedQuantity(request.getAllocatedQuantity())
            .allocatedAt(computedAllocatedAt)
            .allocationMethod(request.getAllocationMethod())
            .allocatedAmount(computedAllocatedAmount)
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
    if (request.getAllocatedQuantity() == null) {
      throw new AllocationValidationException("allocatedQuantity는 필수입니다.");
    }
    validateText(request.getAllocationMethod(), request.getNote());
  }

  // 텍스트 길이(DB 컬럼 255자 — 넘으면 500이 되므로 400으로 막는다)
  private void validateText(String allocationMethod, String note) {
    if (allocationMethod != null && allocationMethod.length() > TEXT_MAX_LENGTH) {
      throw new AllocationValidationException(
          "allocationMethod는 " + TEXT_MAX_LENGTH + "자 이하여야 합니다.");
    }
    if (note != null && note.length() > TEXT_MAX_LENGTH) {
      throw new AllocationValidationException("note는 " + TEXT_MAX_LENGTH + "자 이하여야 합니다.");
    }
  }

  // 배정 총액(확정공모가 × 배정수량)을 계산한다. 공모가를 모르면 null로 둔다.
  private Long computeAllocatedAmount(Ipo ipo, Long allocatedQuantity) {
    if (ipo == null || ipo.getFixedPrice() == null || allocatedQuantity == null) {
      return null;
    }
    try {
      return validateComputedAmount(Math.multiplyExact(ipo.getFixedPrice(), allocatedQuantity));
    } catch (ArithmeticException ex) {
      throw new AllocationValidationException("배정 총액이 계산 가능한 범위를 넘습니다.");
    }
  }

  // 계산된 총액도 금액 상한을 넘지 않아야 한다(입력 검증 대신 서버 계산값을 막는다).
  private Long validateComputedAmount(Long amount) {
    if (amount < 0 || amount > InputLimits.MAX_AMOUNT) {
      throw new AllocationValidationException(
          "배정 총액은 0 이상 " + InputLimits.MAX_AMOUNT + " 이하여야 합니다.");
    }
    return amount;
  }

  // 배정일: IPO 확정 배정일 우선, 없으면 청약 종료일 +2 KRX 영업일, 그것도 없으면 현재 시각.
  private LocalDateTime resolveAllocatedAt(Ipo ipo, LocalDateTime now) {
    if (ipo != null && ipo.getAllotmentDate() != null) {
      return ipo.getAllotmentDate().atTime(LocalTime.of(9, 0));
    }
    LocalDate subscriptionEndDate = ipo != null ? ipo.getSubscriptionEndDate() : null;
    if (subscriptionEndDate != null) {
      LocalDate estimated = businessDayCalculator.plusBusinessDaysOrNull(subscriptionEndDate, 2);
      if (estimated != null) {
        return estimated.atTime(LocalTime.of(9, 0));
      }
    }
    return now;
  }

  private Ipo findIpo(Subscription subscription) {
    if (subscription.getIpoId() == null) {
      return null;
    }
    return ipoRepository.findByIdAndDeletedAtIsNull(subscription.getIpoId()).orElse(null);
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
   * 갱신하고, subscriptionId가 새로 제공된 경우에만 존재 여부를 재검증한다. 시점 제약은 없음(D-A-007). 배정수량은 이미 매도한 수량보다 작게 바꿀 수
   * 없다.
   */
  @Transactional
  public AllocationResponse update(Long id, AllocationUpdateRequest request) {
    // 매도 등록과 같은 배정 행 잠금으로 직렬화한다
    allocationRepository.findByIdForUpdate(id);
    Allocation allocation = findOwnedAllocation(id);

    Long updatedSubscriptionId = request.getSubscriptionId();
    Long updatedAllocatedQuantity = request.getAllocatedQuantity();
    final String updatedAllocationMethod = request.getAllocationMethod();
    final String updatedNote = request.getNote();

    validateText(updatedAllocationMethod, updatedNote);

    // 같은 청약을 다시 지정하면 이동이 아니다
    boolean moving =
        updatedSubscriptionId != null
            && !updatedSubscriptionId.equals(allocation.getSubscriptionId());
    long soldQuantity = activeSaleQuantityReader.sumActiveSaleQuantity(allocation.getId());
    Subscription effectiveSubscription = null;

    if (moving) {
      subscriptionRepository.findByIdForUpdate(updatedSubscriptionId);
      Subscription targetSubscription = findOwnedSubscription(updatedSubscriptionId);
      effectiveSubscription = targetSubscription;
      Subscription current =
          subscriptionOwnership.requireOwnedIncludingDeleted(
              allocation.getSubscriptionId(), OWNERSHIP_ERRORS);
      if (!current.getInvestorId().equals(targetSubscription.getInvestorId())) {
        throw new AllocationValidationException("다른 투자자의 청약으로는 배정을 옮길 수 없습니다.");
      }
      if (soldQuantity > 0) {
        throw new AllocationValidationException("매도 기록이 있는 배정은 다른 청약으로 옮길 수 없습니다.");
      }
      checkDuplicate(updatedSubscriptionId);
      long finalQuantity =
          updatedAllocatedQuantity != null
              ? updatedAllocatedQuantity
              : allocation.getAllocatedQuantity();
      validateQuantity(finalQuantity, targetSubscription.getQuantity());
    } else if (updatedAllocatedQuantity != null) {
      subscriptionRepository.findByIdForUpdate(allocation.getSubscriptionId());
      Subscription subscription =
          subscriptionOwnership.requireOwnedActive(
              allocation.getSubscriptionId(), OWNERSHIP_ERRORS);
      validateQuantity(updatedAllocatedQuantity, subscription.getQuantity());
      effectiveSubscription = subscription;
    }

    if (updatedAllocatedQuantity != null && updatedAllocatedQuantity < soldQuantity) {
      throw new AllocationValidationException(
          "배정수량은 이미 매도한 수량(" + soldQuantity + "주)보다 작을 수 없습니다.");
    }

    LocalDateTime now = LocalDateTime.now(clock);
    Long newAllocatedAmount = null;
    LocalDateTime newAllocatedAt = null;
    // 수량이나 연결 청약이 바뀌면 총액을 다시 계산하고, 이동이면 배정일도 다시 정한다.
    if (moving || updatedAllocatedQuantity != null) {
      Ipo ipo = findIpo(effectiveSubscription);
      long finalQuantity =
          updatedAllocatedQuantity != null
              ? updatedAllocatedQuantity
              : allocation.getAllocatedQuantity();
      newAllocatedAmount = computeAllocatedAmount(ipo, finalQuantity);
      if (moving) {
        newAllocatedAt = resolveAllocatedAt(ipo, now);
      }
    }
    allocation.updateDetails(
        updatedSubscriptionId,
        updatedAllocatedQuantity,
        newAllocatedAt,
        updatedAllocationMethod,
        newAllocatedAmount,
        updatedNote,
        now);
    try {
      Allocation saved = allocationRepository.saveAndFlush(allocation);
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
    if (activeSaleQuantityReader.sumActiveSaleQuantity(id) > 0) {
      throw new HasChildRecordsException("매도 기록이 있는 배정은 취소할 수 없습니다. 매도를 먼저 삭제해 주세요.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    allocation.cancel(now);
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
