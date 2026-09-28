package com.vibeaiteam.dashboard.service.ownership;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.dashboard.exception.DashboardForbiddenException;
import com.vibeaiteam.dashboard.exception.DashboardNotFoundException;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardOwnershipVerifier {

    private final SecurityUtils securityUtils;
    private final SaleRepository saleRepository;
    private final AllocationRepository allocationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvestorRepository investorRepository;

    /**
     * Sale ID를 통한 소유권 검증
     * Sale → Allocation → Subscription → Investor 경유로 현재 계정 소유 확인
     */
    public void verifySaleOwnership(Long saleId) {
        Long currentAccountId = securityUtils.getCurrentAccountId();
        Sale sale = saleRepository.findByIdAndDeletedAtIsNull(saleId)
                .orElseThrow(() -> new DashboardNotFoundException("해당 매도 기록을 찾을 수 없습니다."));
        
        Long investorId = getInvestorIdFromSale(sale);
        Investor investor = investorRepository.findById(investorId)
                .orElseThrow(() -> new DashboardNotFoundException("해당 투자자를 찾을 수 없습니다."));
        
        if (!investor.getAccountId().equals(currentAccountId)) {
            throw new DashboardForbiddenException("해당 데이터에 접근 권한이 없습니다.");
        }
    }

    /**
     * 현재 계정 소유의 ACTIVE Sale만 조회하도록 필터링
     * loadCalculatedSales()에서 호출하여 계정 스코프 강제
     */
    public Long getCurrentAccountId() {
        return securityUtils.getCurrentAccountId();
    }

    private Long getInvestorIdFromSale(Sale sale) {
        // Sale → Allocation → Subscription → Investor 경로로 investorId 획득
        // 엔티티에 JPA 연관관계가 없으므로 Repository로 순차 조회
        Allocation allocation = allocationRepository.findByIdAndDeletedAtIsNull(sale.getAllocationId())
                .orElseThrow(() -> new DashboardNotFoundException("해당 배정 기록을 찾을 수 없습니다."));
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(allocation.getSubscriptionId())
                .orElseThrow(() -> new DashboardNotFoundException("해당 청약 기록을 찾을 수 없습니다."));
        return subscription.getInvestorId();
    }
}