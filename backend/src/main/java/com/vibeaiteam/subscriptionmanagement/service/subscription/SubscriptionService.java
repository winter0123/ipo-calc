package com.vibeaiteam.subscriptionmanagement.service.subscription;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipErrors;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionRegisterRequest;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionResponse;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionUpdateRequest;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionConflictException;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionForbiddenException;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionNotFoundException;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionValidationException;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.vibeaiteam.subscriptionmanagement.service.ownership.SubscriptionOwnership;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 청약 관리 서비스. */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

  private static final OwnershipErrors OWNERSHIP_ERRORS =
      new OwnershipErrors(SubscriptionNotFoundException::new, SubscriptionForbiddenException::new);

  private final SubscriptionRepository subscriptionRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final IpoRepository ipoRepository;
  private final OwnershipGuard ownershipGuard;
  private final SubscriptionOwnership subscriptionOwnership;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /** 청약을 등록한다. */
  @Transactional
  public SubscriptionResponse register(SubscriptionRegisterRequest request) {
    validateRequest(request);

    Long investorId = request.getInvestorId();
    Long securitiesAccountId = request.getSecuritiesAccountId();
    Long ipoId = request.getIpoId();

    // Investor 존재/소유권 검증 — InvestorService.findOwnedInvestor와 동일 패턴(없으면 404, 타 계정 소유면 403)
    validateInvestorOwnership(investorId);

    // SecuritiesAccount 존재/2단계 소유권 검증 — SecuritiesAccountService.getById 패턴(존재+미삭제 404)
    validateSecuritiesAccountOwnership(securitiesAccountId);

    // IPO 존재 검증만 수행(전역 기준정보 — 계정과 무관, 삭제되지 않은 IPO만 허용)
    validateIpoExists(ipoId);

    checkDuplicate(investorId, securitiesAccountId, ipoId, null);

    LocalDateTime now = LocalDateTime.now(clock);
    Subscription subscription =
        Subscription.builder()
            .investorId(investorId)
            .securitiesAccountId(securitiesAccountId)
            .ipoId(ipoId)
            .quantity(request.getQuantity())
            .depositAmount(request.getDepositAmount())
            .subscribedAt(request.getSubscribedAt())
            .memo(request.getMemo())
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    try {
      Subscription saved = subscriptionRepository.save(subscription);
      return SubscriptionResponse.from(saved);
    } catch (DataIntegrityViolationException ex) {
      // AC-005: 부분 유니크 인덱스(uk_subscription_active_combo)가 최종 방어선.
      // Service 중복 검사를 놓친 동시 요청이 유니크 위반을 일으키면 409로 변환한다.
      throw new SubscriptionConflictException("이미 등록된 청약입니다.");
    }
  }

  /**
   * 현재 계정 소유 투자자의 청약 목록을 createdAt 내림차순으로 조회한다. investorId/securitiesAccountId가 주어지면 해당 조건으로 필터링하고,
   * 삭제되지 않은(ACTIVE) 기록만 반환한다. 타 계정 소유 ID를 필터로 주면 빈 목록이다.
   */
  @Transactional(readOnly = true)
  public List<SubscriptionResponse> list(Long investorId, Long securitiesAccountId) {
    return subscriptionRepository
        .findAllByAccountWithFilters(
            securityUtils.getCurrentAccountId(), investorId, securitiesAccountId)
        .stream()
        .map(SubscriptionResponse::from)
        .toList();
  }

  /** 청약 ID로 단건 청약을 조회한다. 삭제되지 않은(ACTIVE) 기록만 반환하고, 존재하지 않거나 취소된 기록이면 404, 타 계정 소유면 403을 던진다. */
  @Transactional(readOnly = true)
  public SubscriptionResponse getById(Long id) {
    return SubscriptionResponse.from(findOwnedSubscription(id));
  }

  /**
   * SF-4: 청약 기록의 일부 필드만 부분 수정한다(IpoService.update와 동일한 패턴 — 제공된 필드만 갱신). 이미 취소(CANCELLED)되었거나 존재하지
   * 않는 기록은 404(엣지케이스). investorId/securitiesAccountId/ipoId가 새로 제공된 경우에만 존재/소유권을 재검증하고, 최종 조합은 자기
   * 자신을 제외하고 중복 검사한다. 배정(Allocation) 연결 시 수량 축소 제한은 Epic 7 구현 시 함께 추가할 예정으로 이번 SF에서는 다루지 않는다.
   */
  @Transactional
  public SubscriptionResponse update(Long id, SubscriptionUpdateRequest request) {
    final Subscription subscription = findOwnedSubscription(id);

    Long updatedInvestorId = request.getInvestorId();
    final Long updatedSecuritiesAccountId = request.getSecuritiesAccountId();
    final Long updatedIpoId = request.getIpoId();
    Long updatedQuantity = request.getQuantity();
    Long updatedDepositAmount = request.getDepositAmount();

    if (updatedQuantity != null && updatedQuantity <= 0) {
      throw new SubscriptionValidationException("quantity는 0보다 커야 합니다.");
    }
    if (updatedDepositAmount != null && updatedDepositAmount < 0) {
      throw new SubscriptionValidationException("depositAmount는 음수일 수 없습니다.");
    }

    // 새로 제공된 경우에만 존재/소유권을 재검증한다 (미제공 시 기존값 유지, 재검증하지 않음)
    if (updatedInvestorId != null) {
      validateInvestorOwnership(updatedInvestorId);
    }
    if (updatedSecuritiesAccountId != null) {
      validateSecuritiesAccountOwnership(updatedSecuritiesAccountId);
    }
    if (updatedIpoId != null) {
      validateIpoExists(updatedIpoId);
    }

    Long finalInvestorId =
        updatedInvestorId != null ? updatedInvestorId : subscription.getInvestorId();
    Long finalSecuritiesAccountId =
        updatedSecuritiesAccountId != null
            ? updatedSecuritiesAccountId
            : subscription.getSecuritiesAccountId();
    Long finalIpoId = updatedIpoId != null ? updatedIpoId : subscription.getIpoId();

    checkDuplicate(finalInvestorId, finalSecuritiesAccountId, finalIpoId, id);

    if (updatedInvestorId != null) {
      subscription.setInvestorId(updatedInvestorId);
    }
    if (updatedSecuritiesAccountId != null) {
      subscription.setSecuritiesAccountId(updatedSecuritiesAccountId);
    }
    if (updatedIpoId != null) {
      subscription.setIpoId(updatedIpoId);
    }
    if (updatedQuantity != null) {
      subscription.setQuantity(updatedQuantity);
    }
    if (updatedDepositAmount != null) {
      subscription.setDepositAmount(updatedDepositAmount);
    }
    if (request.getSubscribedAt() != null) {
      subscription.setSubscribedAt(request.getSubscribedAt());
    }
    if (request.getMemo() != null) {
      subscription.setMemo(request.getMemo());
    }

    subscription.setUpdatedAt(LocalDateTime.now(clock));
    try {
      Subscription saved = subscriptionRepository.save(subscription);
      return SubscriptionResponse.from(saved);
    } catch (DataIntegrityViolationException ex) {
      throw new SubscriptionConflictException("이미 등록된 청약입니다.");
    }
  }

  /**
   * SF-5: 청약 기록을 논리 취소한다(IpoService.deactivate와 동일 패턴). status를 CANCELLED로 변경하고
   * deletedAt/updatedAt을 갱신하며, 물리 삭제는 수행하지 않는다(AC-016). 존재하지 않거나 이미 취소된 기록은
   * findByIdAndDeletedAtIsNull이 걸러내 404를 반환한다(AC-017, 재취소는 자연 배제).
   */
  @Transactional
  public void cancel(Long id) {
    Subscription subscription = findOwnedSubscription(id);
    LocalDateTime now = LocalDateTime.now(clock);
    subscription.setStatus(RecordStatus.CANCELLED);
    subscription.setDeletedAt(now);
    subscription.setUpdatedAt(now);
    subscriptionRepository.save(subscription);
  }

  // 미삭제 청약 조회 + 소유 투자자 검증 — 없으면 404, 타 계정 소유면 403
  private Subscription findOwnedSubscription(Long id) {
    return subscriptionOwnership.requireOwnedActive(id, OWNERSHIP_ERRORS);
  }

  // Investor 존재/소유권 검증 — InvestorService.findOwnedInvestor와 동일 패턴(없으면 404, 타 계정 소유면 403)
  private void validateInvestorOwnership(Long investorId) {
    ownershipGuard.requireOwnedInvestor(investorId, OWNERSHIP_ERRORS);
  }

  // SecuritiesAccount 존재/2단계 소유권 검증 — SecuritiesAccountService.getById 패턴(존재+미삭제 404)
  private void validateSecuritiesAccountOwnership(Long securitiesAccountId) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(securitiesAccountId)
            .orElseThrow(() -> new SubscriptionNotFoundException("증권계좌를 찾을 수 없습니다."));
    ownershipGuard.requireOwnedInvestor(
        account.getInvestorId(),
        () -> new SubscriptionNotFoundException("증권계좌를 찾을 수 없습니다."),
        () -> new SubscriptionForbiddenException("계정 소유권이 없습니다."));
  }

  // IPO 존재 검증만 수행(전역 기준정보 — 계정과 무관, 삭제되지 않은 IPO만 허용)
  private void validateIpoExists(Long ipoId) {
    ipoRepository
        .findByIdAndDeletedAtIsNull(ipoId)
        .orElseThrow(() -> new SubscriptionNotFoundException("IPO를 찾을 수 없습니다."));
  }

  private void validateRequest(SubscriptionRegisterRequest request) {
    if (request == null) {
      throw new SubscriptionValidationException("청약 등록 정보는 필수입니다.");
    }

    if (request.getInvestorId() == null
        || request.getSecuritiesAccountId() == null
        || request.getIpoId() == null
        || request.getQuantity() == null
        || request.getDepositAmount() == null
        || request.getSubscribedAt() == null) {
      throw new SubscriptionValidationException(
          "investorId, securitiesAccountId, ipoId, quantity, depositAmount, subscribedAt는 필수입니다.");
    }

    if (request.getQuantity() <= 0) {
      throw new SubscriptionValidationException("quantity는 0보다 커야 합니다.");
    }
    if (request.getDepositAmount() < 0) {
      throw new SubscriptionValidationException("depositAmount는 음수일 수 없습니다.");
    }
  }

  private void checkDuplicate(
      Long investorId, Long securitiesAccountId, Long ipoId, Long excludeId) {
    // AC-005: (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE 기록이 이미 있으면 409 (AC-006:
    // CANCELLED는 무관)
    // AC-014: 수정 시에는 자기 자신(excludeId)을 제외하고 검사한다 (IpoService.checkDuplicate와 동일 패턴)
    subscriptionRepository
        .findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
            investorId, securitiesAccountId, ipoId, RecordStatus.ACTIVE)
        .filter(existing -> !existing.getId().equals(excludeId))
        .ifPresent(
            existing -> {
              throw new SubscriptionConflictException("이미 등록된 청약입니다.");
            });
  }
}
