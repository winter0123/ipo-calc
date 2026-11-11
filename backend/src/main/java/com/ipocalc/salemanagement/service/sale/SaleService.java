package com.ipocalc.salemanagement.service.sale;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.accountauthentication.util.validation.InputLimits;
import com.ipocalc.allocationmanagement.controller.allocation.dto.AllocationRegisterRequest;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.allocationmanagement.service.allocation.AllocationService;
import com.ipocalc.allocationmanagement.service.ownership.AllocationOwnership;
import com.ipocalc.investormanagement.service.ownership.OwnershipErrors;
import com.ipocalc.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.ipocalc.salemanagement.controller.sale.dto.SaleResponse;
import com.ipocalc.salemanagement.controller.sale.dto.SaleUpdateRequest;
import com.ipocalc.salemanagement.controller.sale.exception.SaleAllocationExceededException;
import com.ipocalc.salemanagement.controller.sale.exception.SaleForbiddenException;
import com.ipocalc.salemanagement.controller.sale.exception.SaleNotFoundException;
import com.ipocalc.salemanagement.controller.sale.exception.SaleValidationException;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.subscriptionmanagement.service.ownership.SubscriptionOwnership;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매도 관리 서비스. */
@Service
@RequiredArgsConstructor
public class SaleService {

  private static final OwnershipErrors OWNERSHIP_ERRORS =
      new OwnershipErrors(SaleNotFoundException::new, SaleForbiddenException::new);

  private final SaleRepository saleRepository;
  private final AllocationRepository allocationRepository;
  private final AllocationService allocationService;
  private final SubscriptionRepository subscriptionRepository;
  private final AllocationOwnership allocationOwnership;
  private final SubscriptionOwnership subscriptionOwnership;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /** 매도를 등록한다. */
  @Transactional
  public SaleResponse register(SaleRegisterRequest request) {
    validateRequest(request);

    Long allocationId = request.getAllocationId();
    if (allocationId == null) {
      allocationId = resolveAllocationId(request);
    } else {
      validateExplicitAllocationMatch(request, allocationId);
    }

    // 동시 매도가 한도를 넘지 않도록 배정 행을 먼저 잠근다(이후 조회는 잠금 뒤의 최신 값)
    allocationRepository.findByIdForUpdate(allocationId);

    // (1) Allocation 조회 — 존재하지 않으면 404, 타 계정 소유면 403
    Allocation allocation = allocationOwnership.requireOwnedActive(allocationId, OWNERSHIP_ERRORS);

    // (2) 기존 ACTIVE Sale들의 quantity 합계 조회
    Long existingQuantitySum = saleRepository.sumActiveQuantityByAllocationId(allocation.getId());
    if (existingQuantitySum == null) {
      existingQuantitySum = 0L;
    }

    // (3) 누적 매도수량 검증: (기존 합계 + 신규 quantity) ≤ allocatedQuantity
    Long newQuantity = request.getQuantity();
    if (newQuantity > allocation.getAllocatedQuantity() - existingQuantitySum) {
      throw new SaleAllocationExceededException(
          "누적 매도수량이 배정수량을 초과합니다. 기존 합계: "
              + existingQuantitySum
              + ", 신규 수량: "
              + newQuantity
              + ", 배정수량: "
              + allocation.getAllocatedQuantity());
    }

    LocalDateTime now = LocalDateTime.now(clock);
    Sale sale =
        Sale.builder()
            .allocationId(allocationId)
            .quantity(newQuantity)
            .price(request.getPrice())
            .amount(request.getAmount())
            .saleDate(request.getSaleDate())
            .fee(request.getFee())
            .tax(request.getTax())
            .otherCost(request.getOtherCost())
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Sale saved = saleRepository.save(sale);
    return SaleResponse.from(saved);
  }

  /** 매도 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<SaleResponse> list(Long allocationId, Long investorId, String investorName) {
    List<Sale> sales =
        saleRepository.findActiveWithFilters(
            securityUtils.getCurrentAccountId(), allocationId, investorId, investorName);
    return sales.stream().map(SaleResponse::from).toList();
  }

  /** ID로 매도를 조회한다. */
  @Transactional(readOnly = true)
  public SaleResponse getById(Long id) {
    return SaleResponse.from(findOwnedSale(id));
  }

  /**
   * SF-4: 매도 기록을 부분 수정한다. 시점/필드 제약 없이 전체 허용(D-SL-003). 제공된 필드만 갱신하며, 누적 매도수량 재검증을 수행한다. (1) 대상 Sale
   * 조회 (CANCELLED면 404) (2) 참조하는 Allocation의 allocatedQuantity 확인 (3) 기존 ACTIVE Sale들의 quantity 합계
   * 조회 (자기 자신은 제외) (4) (기존 합계 - 자기자신 quantity + 수정 quantity) ≤ allocatedQuantity 검증
   */
  @Transactional
  public SaleResponse update(Long id, SaleUpdateRequest request) {
    validateUpdateRequest(request);

    // 배정 행 잠금(등록과 같은 잠금) — 대상 매도가 없으면 아래 findOwnedSale이 404를 낸다
    saleRepository
        .findByIdAndDeletedAtIsNull(id)
        .ifPresent(s -> allocationRepository.findByIdForUpdate(s.getAllocationId()));

    // (1) 대상 Sale 조회 — 없거나 CANCELLED이면 404, 타 계정 소유면 403
    Sale sale = findOwnedSale(id);

    // (2) Allocation 조회
    Allocation allocation =
        allocationRepository
            .findByIdAndDeletedAtIsNull(sale.getAllocationId())
            .orElseThrow(() -> new SaleNotFoundException("배정 기록을 찾을 수 없습니다."));

    // (3) 기존 ACTIVE Sale들의 quantity 합계 조회 (자기 자신 제외)
    Long existingQuantitySum =
        saleRepository.sumActiveQuantityByAllocationIdExcludingId(allocation.getId(), id);
    if (existingQuantitySum == null) {
      existingQuantitySum = 0L;
    }

    // (4) 누적 매도수량 재검증: (기존 합계(자기자신 제외) + 수정 quantity) ≤ allocatedQuantity
    Long newQuantity = request.getQuantity() != null ? request.getQuantity() : sale.getQuantity();
    if (newQuantity > allocation.getAllocatedQuantity() - existingQuantitySum) {
      throw new SaleAllocationExceededException(
          "누적 매도수량이 배정수량을 초과합니다. 기존 합계(자기자신 제외): "
              + existingQuantitySum
              + ", 수정 수량: "
              + newQuantity
              + ", 배정수량: "
              + allocation.getAllocatedQuantity());
    }

    sale.updateDetails(
        request.getQuantity(),
        request.getPrice(),
        request.getAmount(),
        request.getSaleDate(),
        request.getFee(),
        request.getTax(),
        request.getOtherCost(),
        LocalDateTime.now(clock));
    Sale saved = saleRepository.save(sale);
    return SaleResponse.from(saved);
  }

  /**
   * allocationId가 없을 때 청약·배정 연결을 찾아 배정 ID를 반환한다. 기존 배정이 있으면 재사용하고, 없을 때만 새로 만든다. 신규 생성이 필요한 경우에만
   * allocatedQuantity를 요구한다.
   */
  private Long resolveAllocationId(SaleRegisterRequest request) {
    Subscription subscription = null;
    Long subscriptionId = request.getSubscriptionId();
    if (subscriptionId != null) {
      // 명시된 청약은 소유권을 먼저 확인하고, 함께 보낸 공모주·투자자·계좌가 청약과 같은지 확인한다
      subscription = subscriptionOwnership.requireOwnedActive(subscriptionId, OWNERSHIP_ERRORS);
      validateIdsMatch(request, subscription);
    } else {
      // 공모주·투자자·계좌 조합으로 기존 청약을 찾는다(없으면 새 청약·배정을 만든다)
      subscription =
          subscriptionRepository
              .findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
                  request.getInvestorId(),
                  request.getSecuritiesAccountId(),
                  request.getIpoId(),
                  RecordStatus.ACTIVE)
              // 조회 자체는 계정을 가리지 않으므로 소유권을 반드시 확인한다
              .map(
                  found -> {
                    subscriptionOwnership.requireOwnedIncludingDeleted(
                        found.getId(), OWNERSHIP_ERRORS);
                    return found;
                  })
              .orElse(null);
    }

    if (subscription != null) {
      Allocation existingAllocation =
          allocationRepository
              .findBySubscriptionIdAndStatusAndDeletedAtIsNull(
                  subscription.getId(), RecordStatus.ACTIVE)
              .orElse(null);
      if (existingAllocation != null) {
        return existingAllocation.getId();
      }
      if (request.getAllocatedQuantity() == null) {
        throw new SaleValidationException("이 청약에는 아직 배정이 없습니다. 배정수량(allocatedQuantity)을 입력해 주세요.");
      }
      return allocationService
          .register(
              AllocationRegisterRequest.builder()
                  .subscriptionId(subscription.getId())
                  .allocatedQuantity(request.getAllocatedQuantity())
                  .note("매도 등록에서 자동 생성된 배정 연결 기록")
                  .build())
          .getId();
    }

    // 기존 연결이 전혀 없으면 신규 배정 생성이 필요하므로 배정수량을 요구한다
    if (request.getAllocatedQuantity() == null) {
      throw new SaleValidationException("기존 청약·배정 연결이 없어 배정수량(allocatedQuantity)이 필요합니다.");
    }
    AllocationRegisterRequest allocationRequest =
        AllocationRegisterRequest.builder()
            .subscriptionId(null)
            .ipoId(request.getIpoId())
            .investorId(request.getInvestorId())
            .securitiesAccountId(request.getSecuritiesAccountId())
            .allocatedQuantity(request.getAllocatedQuantity())
            .note("매도 등록에서 자동 생성된 배정 연결 기록")
            .build();
    return allocationService.register(allocationRequest).getId();
  }

  /**
   * 명시된 allocationId의 소유권을 먼저 확인한 뒤, 함께 보낸 청약·공모주·투자자·계좌와 일치하는지 확인한다. 소유권 검증이 먼저여야 남의 배정 존재 여부가
   * 400으로 새어 나가지 않는다.
   */
  private void validateExplicitAllocationMatch(SaleRegisterRequest request, Long allocationId) {
    // (1) 존재하지 않으면 404, 타 계정 소유면 403 — 기존 등록 경로와 같은 검증을 먼저 수행한다
    Allocation allocation = allocationOwnership.requireOwnedActive(allocationId, OWNERSHIP_ERRORS);
    if (request.getSubscriptionId() != null
        && !request.getSubscriptionId().equals(allocation.getSubscriptionId())) {
      throw new SaleValidationException("입력한 청약(subscriptionId)이 배정의 청약과 다릅니다.");
    }
    // 청약이 이미 삭제된 경우에도 소유 계정은 유지되므로, 삭제 여부와 관계없이 소유권을 확인해 값을 읽는다
    Subscription subscription =
        subscriptionOwnership.requireOwnedIncludingDeleted(
            allocation.getSubscriptionId(), OWNERSHIP_ERRORS);
    validateIdsMatch(request, subscription);
  }

  /** 공모주·투자자·증권계좌 ID가 주어진 경우에만 청약의 값과 같은지 확인한다. */
  private void validateIdsMatch(SaleRegisterRequest request, Subscription subscription) {
    if (request.getIpoId() != null && !request.getIpoId().equals(subscription.getIpoId())) {
      throw new SaleValidationException("입력한 공모주(ipoId)가 청약의 공모주와 다릅니다.");
    }
    if (request.getInvestorId() != null
        && !request.getInvestorId().equals(subscription.getInvestorId())) {
      throw new SaleValidationException("입력한 투자자(investorId)가 청약의 투자자와 다릅니다.");
    }
    if (request.getSecuritiesAccountId() != null
        && !request.getSecuritiesAccountId().equals(subscription.getSecuritiesAccountId())) {
      throw new SaleValidationException("입력한 증권계좌(securitiesAccountId)가 청약의 계좌와 다릅니다.");
    }
  }

  // 합산·곱셈 오버플로를 막는 상한(하한 검증은 호출부에서 한다)
  private void validateUpperBounds(
      Long quantity, Long price, Long amount, Long fee, Long tax, Long otherCost) {
    if (quantity != null && quantity > InputLimits.MAX_QUANTITY) {
      throw new SaleValidationException("quantity는 " + InputLimits.MAX_QUANTITY + " 이하여야 합니다.");
    }
    String[] names = {"price", "amount", "fee", "tax", "otherCost"};
    Long[] values = {price, amount, fee, tax, otherCost};
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null && values[i] > InputLimits.MAX_AMOUNT) {
        throw new SaleValidationException(names[i] + "는 " + InputLimits.MAX_AMOUNT + " 이하여야 합니다.");
      }
    }
  }

  private void validateUpdateRequest(SaleUpdateRequest request) {
    if (request == null) {
      throw new SaleValidationException("매도 수정 정보는 필수입니다.");
    }
    validateUpperBounds(
        request.getQuantity(),
        request.getPrice(),
        request.getAmount(),
        request.getFee(),
        request.getTax(),
        request.getOtherCost());
    // quantity가 제공된 경우 0 초과 검증
    if (request.getQuantity() != null && request.getQuantity() <= 0) {
      throw new SaleValidationException("quantity는 0 초과여야 합니다.");
    }
    validateNonNegativeAmounts(
        request.getPrice(),
        request.getAmount(),
        request.getFee(),
        request.getTax(),
        request.getOtherCost());
  }

  private void validateRequest(SaleRegisterRequest request) {
    if (request == null) {
      throw new SaleValidationException("매도 등록 정보는 필수입니다.");
    }
    // 식별자(allocationId/subscriptionId)가 하나도 없으면 청약·배정을 찾을 수 있게 IPO·투자자·계좌가 필요하다.
    // allocatedQuantity는 기존 배정이 없어 새로 만들어야 하는 경우에만 요구한다(resolveAllocationId).
    if (request.getAllocationId() == null && request.getSubscriptionId() == null) {
      if (request.getIpoId() == null
          || request.getInvestorId() == null
          || request.getSecuritiesAccountId() == null) {
        throw new SaleValidationException(
            "allocationId 또는 subscriptionId가 없을 때 "
                + "ipoId, investorId, securitiesAccountId가 필요합니다.");
      }
    }
    if (request.getQuantity() == null) {
      throw new SaleValidationException("quantity는 필수입니다.");
    }
    if (request.getSaleDate() == null) {
      throw new SaleValidationException("saleDate는 필수입니다.");
    }
    validateUpperBounds(
        request.getQuantity(),
        request.getPrice(),
        request.getAmount(),
        request.getFee(),
        request.getTax(),
        request.getOtherCost());
    // AC-003: quantity는 0 초과여야 함
    if (request.getQuantity() <= 0) {
      throw new SaleValidationException("quantity는 0 초과여야 합니다.");
    }
    validateNonNegativeAmounts(
        request.getPrice(),
        request.getAmount(),
        request.getFee(),
        request.getTax(),
        request.getOtherCost());
  }

  private void validateNonNegativeAmounts(
      Long price, Long amount, Long fee, Long tax, Long otherCost) {
    String[] names = {"price", "amount", "fee", "tax", "otherCost"};
    Long[] values = {price, amount, fee, tax, otherCost};
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null && values[i] < 0) {
        throw new SaleValidationException(names[i] + "는 0 이상이어야 합니다.");
      }
    }
  }

  /**
   * SF-5: 매도 기록을 논리 취소한다 (AllocationService.cancel과 동일 패턴). status를 CANCELLED로 변경하고
   * deletedAt/updatedAt을 갱신하며, 물리 삭제는 수행하지 않는다(AC-016). 존재하지 않거나 이미 취소된 기록은
   * findByIdAndDeletedAtIsNull이 걸러내 404를 반환한다(AC-017, 재취소는 자연 배제).
   */
  @Transactional
  public void cancel(Long id) {
    Sale sale = findOwnedSale(id);
    LocalDateTime now = LocalDateTime.now(clock);
    sale.cancel(now);
    saleRepository.save(sale);
  }

  // 미삭제 매도 조회 + 소유권 검증(매도 → 배정 → 청약 → 투자자) — 없으면 404, 타 계정 소유면 403
  private Sale findOwnedSale(Long id) {
    Sale sale =
        saleRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SaleNotFoundException("매도 기록을 찾을 수 없습니다."));
    allocationOwnership.requireOwnedIncludingDeleted(sale.getAllocationId(), OWNERSHIP_ERRORS);
    return sale;
  }
}
