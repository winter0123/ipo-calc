package com.vibeaiteam.costrecording.service.cost;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.costrecording.controller.cost.dto.CostRegisterRequest;
import com.vibeaiteam.costrecording.controller.cost.dto.CostResponse;
import com.vibeaiteam.costrecording.controller.cost.dto.CostUpdateRequest;
import com.vibeaiteam.costrecording.controller.cost.exception.CostForbiddenException;
import com.vibeaiteam.costrecording.controller.cost.exception.CostNotFoundException;
import com.vibeaiteam.costrecording.controller.cost.exception.CostValidationException;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비용 관리 서비스.
 */
@Service
@RequiredArgsConstructor
public class CostService {

  private final CostRepository costRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final OwnershipGuard ownershipGuard;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /**
   * 비용을 등록한다. subscriptionId 존재 검증(404), 계정 소유권 검증(403), amount 0 이상 검증(400)을 수행하고,
   * status=ACTIVE로 새 Cost를 생성해 저장한다.
   */
  @Transactional
  public CostResponse register(CostRegisterRequest request) {
    validateRequest(request);

    Long subscriptionId = request.getSubscriptionId();

    // 1. Subscription 존재 검증 (deletedAt IS NULL, CANCELLED 제외) — 404
    Subscription subscription =
        subscriptionRepository
            .findByIdAndDeletedAtIsNull(subscriptionId)
            .orElseThrow(() -> new CostNotFoundException("청약 기록을 찾을 수 없습니다."));

    // 2. 계정 소유권 검증 — Subscription → Investor → Account 체인으로 확인 — 403
    validateSubscriptionOwnership(subscription);

    // 3. Cost 생성 및 저장 (status=ACTIVE)
    LocalDateTime now = LocalDateTime.now(clock);
    Cost cost =
        Cost.builder()
            .subscriptionId(subscriptionId)
            .costType(request.getCostType())
            .amount(request.getAmount())
            .incurredAt(request.getIncurredAt())
            .memo(request.getMemo())
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Cost saved = costRepository.save(cost);
    return CostResponse.from(saved);
  }

  /**
   * 비용을 부분 수정한다. 제공된 필드(costType/amount/incurredAt/memo)만 갱신하며,
   * subscriptionId는 요청에 없으므로 기존 청약의 소유권만 검증한다.
   * CANCELLED(deletedAt IS NOT NULL) 대상이면 404, amount 0 미만/memo 255자 초과는 400을 반환한다.
   * updatedAt을 갱신한다.
   */
  @Transactional
  public CostResponse update(Long id, CostUpdateRequest request) {
    validateUpdateRequest(request);

    // 대상 Cost 조회 — 없거나 CANCELLED이면 404, 타 계정 소유면 403
    Cost cost = findOwnedCost(id);

    // 필드 업데이트 (제공된 필드만)
    if (request.getCostType() != null) {
      cost.setCostType(request.getCostType());
    }
    if (request.getAmount() != null) {
      cost.setAmount(request.getAmount());
    }
    if (request.getIncurredAt() != null) {
      cost.setIncurredAt(request.getIncurredAt());
    }
    if (request.getMemo() != null) {
      cost.setMemo(request.getMemo());
    }

    cost.setUpdatedAt(LocalDateTime.now(clock));
    Cost saved = costRepository.save(cost);
    return CostResponse.from(saved);
  }

  /**
   * 삭제되지 않은 비용 목록을 조회한다. subscriptionId, investorId, investorName으로 필터링 가능하며,
   * investorId/investorName은 Cost→Subscription→Investor 2단계 JOIN으로 수행한다.
   * 정렬은 incurredAt 내림차순, createdAt 내림차순이다. CANCELLED(deletedAt IS NOT NULL)는 제외한다.
   */
  @Transactional(readOnly = true)
  public List<CostResponse> list(Long subscriptionId, Long investorId, String investorName) {
    String investorNamePattern = (investorName != null) ? investorName + "%" : null;
    List<Cost> costs =
        costRepository.findActiveWithFilters(
            securityUtils.getCurrentAccountId(), subscriptionId, investorId, investorNamePattern);
    return costs.stream().map(CostResponse::from).toList();
  }

  /**
   * 비용 ID로 단건 조회한다. deletedAt이 NULL인 ACTIVE 기록만 조회되며,
   * 존재하지 않거나 취소된 비용은 404 CostNotFoundException을 던진다.
   * 타 계정 소유면 403을 던진다.
   */
  @Transactional(readOnly = true)
  public CostResponse getById(Long id) {
    return CostResponse.from(findOwnedCost(id));
  }

  // 요청 필수값 검증: subscriptionId, costType, amount, incurredAt 누락 시 400
  // amount 0 미만 시 400, memo 255자 초과 시 400
  private void validateRequest(CostRegisterRequest request) {
    if (request == null) {
      throw new CostValidationException("비용 등록 정보는 필수입니다.");
    }

    if (request.getSubscriptionId() == null
        || request.getCostType() == null
        || request.getAmount() == null
        || request.getIncurredAt() == null) {
      throw new CostValidationException("subscriptionId, costType, amount, incurredAt는 필수입니다.");
    }

    if (request.getAmount() < 0) {
      throw new CostValidationException("amount는 0 이상이어야 합니다.");
    }

    if (request.getMemo() != null && request.getMemo().length() > 255) {
      throw new CostValidationException("memo는 255자 이하여야 합니다.");
    }
  }

  // 미삭제 비용 조회 + 소유권 검증(비용 → 청약 → 투자자) — 없으면 404, 타 계정 소유면 403
  private Cost findOwnedCost(Long id) {
    Cost cost =
        costRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new CostNotFoundException("비용 기록을 찾을 수 없습니다."));
    Subscription subscription =
        subscriptionRepository
            .findById(cost.getSubscriptionId())
            .orElseThrow(() -> new CostNotFoundException("청약 기록을 찾을 수 없습니다."));
    validateSubscriptionOwnership(subscription);
    return cost;
  }

  // Subscription 소유권 검증: Subscription → Investor(accountId) → 현재 계정 ID 비교
  private void validateSubscriptionOwnership(Subscription subscription) {
    ownershipGuard.requireOwnedInvestor(
        subscription.getInvestorId(),
        () -> new CostNotFoundException("투자자를 찾을 수 없습니다."),
        () -> new CostForbiddenException("계정 소유권이 없습니다."));
  }

  // 수정 요청 검증: amount 0 미만 시 400, memo 255자 초과 시 400
  private void validateUpdateRequest(CostUpdateRequest request) {
    if (request == null) {
      throw new CostValidationException("비용 수정 정보는 필수입니다.");
    }

    if (request.getAmount() != null && request.getAmount() < 0) {
      throw new CostValidationException("amount는 0 이상이어야 합니다.");
    }

    if (request.getMemo() != null && request.getMemo().length() > 255) {
      throw new CostValidationException("memo는 255자 이하여야 합니다.");
    }
  }

  /**
   * 비용을 논리 취소한다. status를 CANCELLED로 변경하고 deletedAt/updatedAt을 갱신하며,
   * 물리 삭제는 수행하지 않는다. 존재하지 않거나 이미 취소된 기록은
   * findByIdAndDeletedAtIsNull이 걸러내 404를 반환한다(재취소는 자연 배제).
   */
  @Transactional
  public void cancel(Long id) {
    Cost cost = findOwnedCost(id);
    LocalDateTime now = LocalDateTime.now(clock);
    cost.setStatus(RecordStatus.CANCELLED);
    cost.setDeletedAt(now);
    cost.setUpdatedAt(now);
    costRepository.save(cost);
  }
}
