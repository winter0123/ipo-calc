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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardAggregationService 단위 테스트
 *
 * 핵심 검증 대상:
 * 1. loadCalculatedSales()가 올바른 파라미터로 Repository를 호출하는지
 * 2. getSummary/getByInvestor/getBySecuritiesFirm/getByPeriod가 스텁으로 동작하는지 (SF-1 범위)
 * 3. getSummary가 실제 집계 로직을 올바르게 수행하는지 (SF-2 범위)
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

    private void setupMocksWithSales(List<Sale> sales, List<Allocation> allocations, List<Subscription> subscriptions,
                                     List<Investor> investors, List<SecuritiesAccount> securitiesAccounts,
                                     List<SecuritiesFirm> securitiesFirms, List<Cost> costs, List<Ipo> ipos) {
        when(ownershipVerifier.getCurrentAccountId()).thenReturn(1L);
        when(saleRepository.findAllByStatusAndInvestorAccountId(eq("ACTIVE"), eq(1L))).thenReturn(sales);
        when(allocationRepository.findByIdIn(any())).thenReturn(allocations);
        when(subscriptionRepository.findAllById(any())).thenReturn(subscriptions);
        when(investorRepository.findAllById(any())).thenReturn(investors);
        when(securitiesAccountRepository.findAllById(any())).thenReturn(securitiesAccounts);
        when(securitiesFirmRepository.findAllById(any())).thenReturn(securitiesFirms);
        when(costRepository.findBySubscriptionIdInAndStatusAndDeletedAtIsNull(any(), anyString())).thenReturn(costs);
        when(ipoRepository.findAllById(any())).thenReturn(ipos);
    }

    private Sale createSale(Long id, Long allocationId, Long quantity, Long amount, Long fee, Long tax, Long otherCost) {
        return Sale.builder()
                .id(id)
                .allocationId(allocationId)
                .quantity(quantity)
                .amount(amount)
                .fee(fee != null ? fee : 0L)
                .tax(tax != null ? tax : 0L)
                .otherCost(otherCost != null ? otherCost : 0L)
                .saleDate(LocalDateTime.now())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Allocation createAllocation(Long id, Long subscriptionId, Long allocatedQuantity, Long allocationPrice) {
        return Allocation.builder()
                .id(id)
                .subscriptionId(subscriptionId)
                .allocatedQuantity(allocatedQuantity)
                .allocationPrice(allocationPrice)
                .allocationMethod("PROPORTIONAL")
                .allocatedAt(LocalDateTime.now())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Subscription createSubscription(Long id, Long investorId, Long securitiesAccountId, Long ipoId, Long quantity) {
        return Subscription.builder()
                .id(id)
                .investorId(investorId)
                .securitiesAccountId(securitiesAccountId)
                .ipoId(ipoId)
                .quantity(quantity)
                .depositAmount(5000000L)
                .subscribedAt(LocalDateTime.now())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Investor createInvestor(Long id, Long accountId, String name) {
        return Investor.builder()
                .id(id)
                .accountId(accountId)
                .displayName(name)
                .relationship("SELF")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SecuritiesAccount createSecuritiesAccount(Long id, Long securitiesFirmId) {
        return SecuritiesAccount.builder()
                .id(id)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber("1234567890")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SecuritiesFirm createSecuritiesFirm(Long id, String name) {
        return SecuritiesFirm.builder()
                .id(id)
                .name(name)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Ipo createIpo(Long id, Long fixedPrice) {
        return Ipo.builder()
                .id(id)
                .ipoName("테스트 IPO")
                .fixedPrice(fixedPrice)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
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
    void getSummaryAggregatesCorrectlyWithMultipleSales() {
        // Given: 2개의 Sale, 각각 다른 allocationPrice와 수량
        // Sale 1: quantity=10, amount=150000, fee=1000, tax=5000, otherCost=200
        //          allocation: allocatedQuantity=50, allocationPrice=10000
        //          subscription: investorId=10, securitiesAccountId=1, ipoId=1, quantity=100
        //          ipo: fixedPrice=9000
        //          cost: totalActiveCost=100000
        // Sale 2: quantity=20, amount=300000, fee=2000, tax=10000, otherCost=500
        //          allocation: allocatedQuantity=100, allocationPrice=12000
        //          subscription: investorId=10, securitiesAccountId=1, ipoId=1, quantity=100
        //          cost: totalActiveCost=100000 (same subscription)
        
        Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
        Sale sale2 = createSale(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L);
        
        Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
        Allocation allocation2 = createAllocation(200L, 1000L, 100L, 12000L);
        
        Subscription subscription = createSubscription(1000L, 10L, 1L, 1L, 100L);
        
        Investor investor = createInvestor(10L, 1L, "테스트 투자자");
        
        SecuritiesAccount securitiesAccount = createSecuritiesAccount(1L, 100L);
        
        SecuritiesFirm securitiesFirm = createSecuritiesFirm(100L, "테스트 증권사");
        
        Ipo ipo = createIpo(1L, 9000L);
        
        Cost cost = Cost.builder()
                .id(1L)
                .subscriptionId(1000L)
                .amount(100000L)
                .costType("TRANSFER_FEE")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        setupMocksWithSales(
                List.of(sale1, sale2),
                List.of(allocation1, allocation2),
                List.of(subscription),
                List.of(investor),
                List.of(securitiesAccount),
                List.of(securitiesFirm),
                List.of(cost),
                List.of(ipo)
        );

        // When
        SummaryDto result = dashboardAggregationService.getSummary();

        // Debug: verify mocks were called
        verify(saleRepository).findAllByStatusAndInvestorAccountId(eq("ACTIVE"), eq(1L));
        verify(allocationRepository).findByIdIn(any());
        verify(subscriptionRepository).findAllById(any());
        verify(investorRepository).findAllById(any());
        verify(securitiesAccountRepository).findAllById(any());
        verify(securitiesFirmRepository).findAllById(any());
        verify(costRepository).findBySubscriptionIdInAndStatusAndDeletedAtIsNull(any(), anyString());
        verify(ipoRepository).findAllById(any());

        // Then: 집계 검증
        assertThat(result).isNotNull();
        
        // totalInvestmentAmount = Σ(allocatedQuantity × allocationPrice) = 50*10000 + 100*12000 = 500000 + 1200000 = 1700000
        assertThat(result.getTotalInvestmentAmount()).isEqualTo(1700000L);
        
        // 참여 건수 = 2
        assertThat(result.getParticipationCount()).isEqualTo(2);
        
        // totalPreTaxProfit과 totalAfterTaxProfit은 ProfitCalculator 계산 결과 검증 (sale quantity 기준)
        // acquisitionAmount1 = 10000 * 10 = 100000
        // matchedCost1 = round(100000 * 10 / 50) = 20000
        // preTaxProfit1 = 150000 - 100000 - 20000 - 1000 - 200 = 28800
        // afterTaxProfit1 = 28800 - 5000 = 23800
        
        // acquisitionAmount2 = 12000 * 20 = 240000
        // matchedCost2 = round(100000 * 20 / 100) = 20000
        // preTaxProfit2 = 300000 - 240000 - 20000 - 2000 - 500 = 37500
        // afterTaxProfit2 = 37500 - 10000 = 27500
        
        assertThat(result.getTotalPreTaxProfit()).isEqualTo(28800L + 37500L); // 66300
        assertThat(result.getTotalAfterTaxProfit()).isEqualTo(23800L + 27500L); // 51300
        
        // totalProfitRate = (totalAfterTaxProfit / totalInvestmentAmount) * 100 = (51300 / 1700000) * 100 = 3.0176...
        assertThat(result.getTotalProfitRate()).isCloseTo(3.018, org.assertj.core.api.Assertions.offset(0.01));
    }

    @Test
    void getSummaryReturnsZeroProfitRateWhenTotalInvestmentAmountIsZero() {
        // Given: allocationPrice가 null인 Sale (총투자금 0)
        Sale sale = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
        
        Allocation allocation = createAllocation(100L, 1000L, 50L, null); // allocationPrice = null
        
        Subscription subscription = createSubscription(1000L, 10L, 1L, 1L, 100L);
        
        Investor investor = createInvestor(10L, 1L, "테스트 투자자");
        
        SecuritiesAccount securitiesAccount = createSecuritiesAccount(1L, 100L);
        
        SecuritiesFirm securitiesFirm = createSecuritiesFirm(100L, "테스트 증권사");
        
        Ipo ipo = createIpo(1L, 9000L); // fixedPrice가 있지만 allocationPrice가 null이므로 0으로 취급
        
        Cost cost = Cost.builder()
                .id(1L)
                .subscriptionId(1000L)
                .amount(100000L)
                .costType("TRANSFER_FEE")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        setupMocksWithSales(
                List.of(sale),
                List.of(allocation),
                List.of(subscription),
                List.of(investor),
                List.of(securitiesAccount),
                List.of(securitiesFirm),
                List.of(cost),
                List.of(ipo)
        );

        // When
        SummaryDto result = dashboardAggregationService.getSummary();

        // Then: totalInvestmentAmount = 0 (allocationPrice가 null이므로), totalProfitRate = 0.0 (AC-007)
        assertThat(result.getTotalInvestmentAmount()).isEqualTo(0L);
        assertThat(result.getTotalProfitRate()).isEqualTo(0.0);
        // 세전/세후수익은 여전히 계산됨 (acquisitionAmount = fixedPrice * saleQuantity = 9000 * 10 = 90000)
        // preTaxProfit = 150000 - 90000 - 20000 - 1000 - 200 = 38800
        // afterTaxProfit = 38800 - 5000 = 33800
        assertThat(result.getTotalPreTaxProfit()).isEqualTo(38800L);
        assertThat(result.getTotalAfterTaxProfit()).isEqualTo(33800L);
        assertThat(result.getParticipationCount()).isEqualTo(1);
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