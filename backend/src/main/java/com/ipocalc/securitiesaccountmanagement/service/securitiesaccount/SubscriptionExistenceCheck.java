package com.ipocalc.securitiesaccountmanagement.service.securitiesaccount;

/** 증권계좌에 연결된 청약 이력 존재 여부를 확인하는 저장소 경계. */
public interface SubscriptionExistenceCheck {
  /** 해당 증권계좌를 참조하는 청약(삭제된 것 포함)이 하나라도 있는지 확인한다. */
  boolean existsBySecuritiesAccountId(Long securitiesAccountId);
}
