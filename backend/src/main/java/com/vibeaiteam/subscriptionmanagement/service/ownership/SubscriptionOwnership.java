package com.vibeaiteam.subscriptionmanagement.service.ownership;

import com.vibeaiteam.investormanagement.service.ownership.OwnershipErrors;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 청약 → 투자자 → 계정 소유권 검증을 한 곳에 모은다.
 *
 * <p>청약 아래 단계(비용·배정·매도)의 서비스는 각자 이 경로를 풀지 않고 이 클래스를 쓴다. 도메인별 예외는 {@link OwnershipErrors}로 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionOwnership {

  /** 청약을 찾지 못했을 때의 표준 메시지. */
  public static final String SUBSCRIPTION_NOT_FOUND = "청약 기록을 찾을 수 없습니다.";

  private final SubscriptionRepository subscriptionRepository;
  private final OwnershipGuard ownershipGuard;

  /**
   * 삭제되지 않은 청약을 조회하고 현재 계정 소유인지 검증한다.
   *
   * @param id 청약 ID
   * @param errors 도메인 예외(없음 404, 타 계정 403)
   * @return 소유가 확인된 청약
   */
  public Subscription requireOwnedActive(Long id, OwnershipErrors errors) {
    Subscription subscription =
        subscriptionRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> errors.notFound(SUBSCRIPTION_NOT_FOUND));
    ownershipGuard.requireOwnedInvestor(subscription.getInvestorId(), errors);
    return subscription;
  }

  /**
   * 삭제 여부와 관계없이 청약을 조회하고 현재 계정 소유인지 검증한다. 이미 만든 하위 기록(배정·비용·매도)의 부모를 따라 올라갈 때 쓴다.
   *
   * @param id 청약 ID
   * @param errors 도메인 예외(없음 404, 타 계정 403)
   * @return 소유가 확인된 청약
   */
  public Subscription requireOwnedIncludingDeleted(Long id, OwnershipErrors errors) {
    Subscription subscription =
        subscriptionRepository
            .findById(id)
            .orElseThrow(() -> errors.notFound(SUBSCRIPTION_NOT_FOUND));
    ownershipGuard.requireOwnedInvestor(subscription.getInvestorId(), errors);
    return subscription;
  }
}
