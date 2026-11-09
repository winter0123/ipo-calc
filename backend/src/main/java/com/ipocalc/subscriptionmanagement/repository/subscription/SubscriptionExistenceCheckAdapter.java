package com.ipocalc.subscriptionmanagement.repository.subscription;

import com.ipocalc.securitiesaccountmanagement.service.securitiesaccount.SubscriptionExistenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 청약 저장소를 증권계좌 도메인의 존재 확인 포트에 연결한다. */
@Component
@RequiredArgsConstructor
public class SubscriptionExistenceCheckAdapter implements SubscriptionExistenceCheck {
  private final SubscriptionRepository subscriptionRepository;

  @Override
  public boolean existsBySecuritiesAccountId(Long securitiesAccountId) {
    return subscriptionRepository.existsBySecuritiesAccountId(securitiesAccountId);
  }
}
