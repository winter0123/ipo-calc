package com.vibeaiteam.dashboard.service;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.dashboard.dto.InvestorAggregationDto;
import com.vibeaiteam.dashboard.dto.PeriodAggregationDto;
import com.vibeaiteam.dashboard.dto.SecuritiesFirmAggregationDto;
import com.vibeaiteam.dashboard.dto.SummaryDto;
import com.vibeaiteam.dashboard.service.ownership.DashboardOwnershipVerifier;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardAggregationService 단위 테스트
 *
 * 핵심 검증 대상:
 * 1. loadCalculatedSales()가 올바른 파라미터로 Repository를 호출하는지
 * 2. getSummary/getByInvestor/getBySecuritiesFirm/getByPeriod가 스텁으로 동작하는지 (SF-1 범위)
 */
@SpringBootTest(classes = com.vibeaiteam.accountauthentication.AccountAuthenticationApplication.class)
@ActiveProfiles("test")
class DashboardAggregationServiceTest {

    @Autowired
    private DashboardAggregationService dashboardAggregationService;

    @MockBean
    private DashboardOwnershipVerifier ownershipVerifier;

    @MockBean
    private SaleRepository saleRepository;

    @MockBean
    private AllocationRepository allocationRepository;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private InvestorRepository investorRepository;

    @MockBean
    private SecuritiesAccountRepository securitiesAccountRepository;

    @MockBean
    private SecuritiesFirmRepository securitiesFirmRepository;

    @MockBean
    private CostRepository costRepository;

    @MockBean
    private IpoRepository ipoRepository;

    private void setupEmptyMocks() {
        when(ownershipVerifier.getCurrentAccountId()).thenReturn(1L);
        when(saleRepository.findAllByStatusAndInvestorAccountId(anyString(), anyLong())).thenReturn(Collections.emptyList());
        when(allocationRepository.findByIdIn(anyList())).thenReturn(Collections.emptyList());
        when(subscriptionRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(investorRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(securitiesAccountRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(securitiesFirmRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
        when(costRepository.findBySubscriptionIdInAndStatusAndDeletedAtIsNull(anyList(), anyString())).thenReturn(Collections.emptyList());
        when(ipoRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    }

    @Test
    void getSummaryReturnsEmptyAggregationWhenNoSales() {
        // Given: 현재 계정 ID 반환
        setupEmptyMocks();

        // When
        SummaryDto result = dashboardAggregationService.getSummary();

        // Then: 모든 필드 0인 빈 집계 반환 (AC-006)
        assertThat(result).isNotNull();
        assertThat(result.getTotalInvestmentAmount()).isEqualTo(0L);
        assertThat(result.getTotalPreTaxProfit()).isEqualTo(0L);
        assertThat(result.getTotalAfterTaxProfit()).isEqualTo(0L);
        assertThat(result.getTotalProfitRate()).isEqualTo(0.0);
        assertThat(result.getParticipationCount()).isEqualTo(0);
    }

    @Test
    void getByInvestorReturnsEmptyListWhenNoSales() {
        // Given: 현재 계정 ID 반환, Sale 없음
        setupEmptyMocks();

        // When
        List<InvestorAggregationDto> result = dashboardAggregationService.getByInvestor();

        // Then: 빈 배열 반환
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void getBySecuritiesFirmReturnsEmptyListWhenNoSales() {
        // Given: 현재 계정 ID 반환, Sale 없음
        setupEmptyMocks();

        // When
        List<SecuritiesFirmAggregationDto> result = dashboardAggregationService.getBySecuritiesFirm();

        // Then: 빈 배열 반환
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void getByPeriodReturnsEmptyListWhenNoSales() {
        // Given: 현재 계정 ID 반환, Sale 없음
        setupEmptyMocks();

        // When
        List<PeriodAggregationDto> result = dashboardAggregationService.getByPeriod("month");

        // Then: 빈 배열 반환
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void loadCalculatedSalesCallsRepositoryWithCorrectParameters() {
        // Given
        setupEmptyMocks();

        // When: private 메서드이므로 리플렉션으로 호출하거나 getSummary를 통해 간접 검증
        dashboardAggregationService.getSummary();

        // Then: 올바른 파라미터로 Repository 호출 확인
        verify(saleRepository).findAllByStatusAndInvestorAccountId("ACTIVE", 1L);
    }

    @Test
    void getByPeriodAcceptsValidUnitParameters() {
        // Given
        setupEmptyMocks();

        // When & Then: month, year 모두 예외 없이 동작 (검증은 Controller에서 수행)
        dashboardAggregationService.getByPeriod("month");
        dashboardAggregationService.getByPeriod("year");
    }
}