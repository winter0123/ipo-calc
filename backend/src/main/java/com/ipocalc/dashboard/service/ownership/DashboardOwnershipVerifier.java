package com.ipocalc.dashboard.service.ownership;

import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.dashboard.exception.DashboardForbiddenException;
import com.ipocalc.dashboard.exception.DashboardNotFoundException;
import com.ipocalc.investormanagement.service.ownership.OwnershipGuard;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 조회 대상의 소유권을 검증한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardOwnershipVerifier {

  private final SecurityUtils securityUtils;
  private final SaleRepository saleRepository;
  private final AllocationRepository allocationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final OwnershipGuard ownershipGuard;

  /** Sale ID를 통한 소유권 검증 Sale → Allocation → Subscription → Investor 경유로 현재 계정 소유 확인. */
  public void verifySaleOwnership(Long saleId) {
    Sale sale =
        saleRepository
            .findByIdAndDeletedAtIsNull(saleId)
            .orElseThrow(() -> new DashboardNotFoundException("해당 매도 기록을 찾을 수 없습니다."));

    ownershipGuard.requireOwnedInvestor(
        getInvestorIdFromSale(sale),
        () -> new DashboardNotFoundException("해당 투자자를 찾을 수 없습니다."),
        () -> new DashboardForbiddenException("해당 데이터에 접근 권한이 없습니다."));
  }

  /** 현재 계정 소유의 ACTIVE Sale만 조회하도록 필터링 loadCalculatedSales()에서 호출하여 계정 스코프 강제. */
  public Long getCurrentAccountId() {
    return securityUtils.getCurrentAccountId();
  }

  private Long getInvestorIdFromSale(Sale sale) {
    // Sale → Allocation → Subscription → Investor 경로로 investorId 획득
    // 엔티티에 JPA 연관관계가 없으므로 Repository로 순차 조회
    Allocation allocation =
        allocationRepository
            .findByIdAndDeletedAtIsNull(sale.getAllocationId())
            .orElseThrow(() -> new DashboardNotFoundException("해당 배정 기록을 찾을 수 없습니다."));
    Subscription subscription =
        subscriptionRepository
            .findByIdAndDeletedAtIsNull(allocation.getSubscriptionId())
            .orElseThrow(() -> new DashboardNotFoundException("해당 청약 기록을 찾을 수 없습니다."));
    return subscription.getInvestorId();
  }
}
