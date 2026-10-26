package com.ipocalc.subscriptionmanagement.repository.subscription;

import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 청약 도메인의 조인·집계 QueryDSL 저장소. */
public interface SubscriptionQueryRepository {
  /** 갱신 잠금을 사용해 청약을 조회한다. */
  Optional<Subscription> findByIdForUpdate(Long id);

  /** 청약에 활성 배정 결과가 있는지 확인한다. */
  boolean hasActiveAllocation(Long subscriptionId);

  /** 청약의 활성 배정 수량을 합산한다. */
  long activeAllocatedQuantity(Long subscriptionId);

  /** 계정과 선택 필터에 해당하는 청약 내역을 조회한다. */
  List<Subscription> findAllByAccountWithFilters(
      Long accountId, Long investorId, Long securitiesAccountId);

  /** 투자자 계정이 해당 공모주에 청약했는지 확인한다. */
  boolean existsByIpoIdAndInvestorAccountId(Long ipoId, Long currentAccountId);

  /** 투자자 계정의 청약 내역을 페이지로 조회한다. */
  Page<Subscription> findByInvestorAccountId(Long currentAccountId, Pageable pageable);

  /** 계정과 선택 필터에 해당하는 청약 내역을 페이지로 조회한다. */
  Page<Subscription> findByInvestorAccountIdWithFilters(
      Long currentAccountId,
      Long investorId,
      Long securitiesAccountId,
      Long ipoId,
      Pageable pageable);
}
