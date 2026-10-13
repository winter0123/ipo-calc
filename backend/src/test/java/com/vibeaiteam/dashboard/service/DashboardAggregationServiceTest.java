package com.vibeaiteam.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.dashboard.dto.InvestorAggregationDto;
import com.vibeaiteam.dashboard.dto.PeriodAggregationDto;
import com.vibeaiteam.dashboard.dto.SecuritiesFirmAggregationDto;
import com.vibeaiteam.dashboard.dto.SummaryDto;
import com.vibeaiteam.dashboard.service.ownership.DashboardOwnershipVerifier;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * DashboardAggregationService 단위 테스트
 *
 * <p>핵심 검증 대상: 1. loadCalculatedSales()가 올바른 파라미터로 Repository를 호출하는지 2.
 * getSummary/getByInvestor/getBySecuritiesFirm/getByPeriod가 스텁으로 동작하는지 (SF-1 범위) 3. getSummary가 실제
 * 집계 로직을 올바르게 수행하는지 (SF-2 범위)
 */
@SpringBootTest(
    classes = com.vibeaiteam.accountauthentication.AccountAuthenticationApplication.class)
@ActiveProfiles("test")
class DashboardAggregationServiceTest {

  @Autowired private DashboardAggregationService dashboardAggregationService;

  @MockitoBean private DashboardOwnershipVerifier ownershipVerifier;

  @MockitoBean private SaleRepository saleRepository;

  @MockitoBean private AllocationRepository allocationRepository;

  @MockitoBean private SubscriptionRepository subscriptionRepository;

  @MockitoBean private InvestorRepository investorRepository;

  @MockitoBean private SecuritiesAccountRepository securitiesAccountRepository;

  @MockitoBean private SecuritiesFirmRepository securitiesFirmRepository;

  @MockitoBean private IpoRepository ipoRepository;

  private void setupEmptyMocks() {
    when(ownershipVerifier.getCurrentAccountId()).thenReturn(1L);
    when(saleRepository.findAllByStatusAndInvestorAccountId(any(RecordStatus.class), anyLong()))
        .thenReturn(Collections.emptyList());
    when(allocationRepository.findByIdIn(anyList())).thenReturn(Collections.emptyList());
    when(subscriptionRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(investorRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(securitiesAccountRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(securitiesFirmRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
    when(ipoRepository.findAllById(anyList())).thenReturn(Collections.emptyList());
  }

  private void setupMocksWithSales(
      List<Sale> sales,
      List<Allocation> allocations,
      List<Subscription> subscriptions,
      List<Investor> investors,
      List<SecuritiesAccount> securitiesAccounts,
      List<SecuritiesFirm> securitiesFirms,
      List<Ipo> ipos) {
    when(ownershipVerifier.getCurrentAccountId()).thenReturn(1L);
    when(saleRepository.findAllByStatusAndInvestorAccountId(eq(RecordStatus.ACTIVE), eq(1L)))
        .thenReturn(sales);
    when(allocationRepository.findByIdIn(any())).thenReturn(allocations);
    when(subscriptionRepository.findAllById(any())).thenReturn(subscriptions);
    when(investorRepository.findAllById(any())).thenReturn(investors);
    when(securitiesAccountRepository.findAllById(any())).thenReturn(securitiesAccounts);
    when(securitiesFirmRepository.findAllById(any())).thenReturn(securitiesFirms);
    when(ipoRepository.findAllById(any())).thenReturn(ipos);
  }

  private Sale createSale(
      Long id, Long allocationId, Long quantity, Long amount, Long fee, Long tax, Long otherCost) {
    return Sale.builder()
        .id(id)
        .allocationId(allocationId)
        .quantity(quantity)
        .amount(amount)
        .fee(fee != null ? fee : 0L)
        .tax(tax != null ? tax : 0L)
        .otherCost(otherCost != null ? otherCost : 0L)
        .saleDate(LocalDateTime.now())
        .status(RecordStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private Sale createSaleWithDate(
      Long id,
      Long allocationId,
      Long quantity,
      Long amount,
      Long fee,
      Long tax,
      Long otherCost,
      LocalDateTime saleDate) {
    return Sale.builder()
        .id(id)
        .allocationId(allocationId)
        .quantity(quantity)
        .amount(amount)
        .fee(fee != null ? fee : 0L)
        .tax(tax != null ? tax : 0L)
        .otherCost(otherCost != null ? otherCost : 0L)
        .saleDate(saleDate)
        .status(RecordStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private Allocation createAllocation(
      Long id, Long subscriptionId, Long allocatedQuantity, Long allocationPrice) {
    return Allocation.builder()
        .id(id)
        .subscriptionId(subscriptionId)
        .allocatedQuantity(allocatedQuantity)
        .allocationPrice(allocationPrice)
        .allocationMethod("PROPORTIONAL")
        .allocatedAt(LocalDateTime.now())
        .status(RecordStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private Subscription createSubscription(
      Long id, Long investorId, Long securitiesAccountId, Long ipoId, Long quantity) {
    return Subscription.builder()
        .id(id)
        .investorId(investorId)
        .securitiesAccountId(securitiesAccountId)
        .ipoId(ipoId)
        .quantity(quantity)
        .depositAmount(5000000L)
        .subscribedAt(LocalDateTime.now())
        .status(RecordStatus.ACTIVE)
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
        .status(RecordStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private SecuritiesAccount createSecuritiesAccount(Long id, Long securitiesFirmId) {
    return SecuritiesAccount.builder()
        .id(id)
        .securitiesFirmId(securitiesFirmId)
        .accountNumber("1234567890")
        .status(RecordStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private SecuritiesFirm createSecuritiesFirm(Long id, String name) {
    return SecuritiesFirm.builder()
        .id(id)
        .name(name)
        .status(RecordStatus.ACTIVE)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private Ipo createIpo(Long id, Long fixedPrice) {
    return createIpo(id, fixedPrice, null);
  }

  private Ipo createIpo(Long id, Long fixedPrice, LocalDate listingDate) {
    return Ipo.builder()
        .id(id)
        .listingDate(listingDate)
        .ipoName("테스트 IPO")
        .fixedPrice(fixedPrice)
        .status(RecordStatus.ACTIVE)
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
    // Sale 2: quantity=20, amount=300000, fee=2000, tax=10000, otherCost=500
    //          allocation: allocatedQuantity=100, allocationPrice=12000
    //          subscription: investorId=10, securitiesAccountId=1, ipoId=1, quantity=100

    Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
    Sale sale2 = createSale(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L);

    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 1000L, 100L, 12000L);

    Subscription subscription = createSubscription(1000L, 10L, 1L, 1L, 100L);

    Investor investor = createInvestor(10L, 1L, "테스트 투자자");

    SecuritiesAccount securitiesAccount = createSecuritiesAccount(1L, 100L);

    SecuritiesFirm securitiesFirm = createSecuritiesFirm(100L, "테스트 증권사");

    Ipo ipo = createIpo(1L, 9000L);

    setupMocksWithSales(
        List.of(sale1, sale2),
        List.of(allocation1, allocation2),
        List.of(subscription),
        List.of(investor),
        List.of(securitiesAccount),
        List.of(securitiesFirm),
        List.of(ipo));

    // When
    SummaryDto result = dashboardAggregationService.getSummary();

    // Debug: verify mocks were called
    verify(saleRepository).findAllByStatusAndInvestorAccountId(eq(RecordStatus.ACTIVE), eq(1L));
    verify(allocationRepository).findByIdIn(any());
    verify(subscriptionRepository).findAllById(any());
    verify(investorRepository).findAllById(any());
    verify(securitiesAccountRepository).findAllById(any());
    verify(securitiesFirmRepository).findAllById(any());
    verify(ipoRepository).findAllById(any());

    // Then: 집계 검증
    assertThat(result).isNotNull();

    // totalInvestmentAmount = Σ(allocationPrice × 매도수량) = 10000*10 + 12000*20 =
    // 100000 + 240000 = 340000 (배정금액을 매도 수량 비율로 안분)
    assertThat(result.getTotalInvestmentAmount()).isEqualTo(340000L);

    // 참여 건수 = 2
    assertThat(result.getParticipationCount()).isEqualTo(2);

    // totalPreTaxProfit과 totalAfterTaxProfit은 ProfitCalculator 계산 결과 검증 (sale quantity 기준)
    // acquisitionAmount1 = 10000 * 10 = 100000
    // preTaxProfit1 = 150000 - 100000 - 1000 - 200 = 48800
    // afterTaxProfit1 = 48800 - 5000 = 43800

    // acquisitionAmount2 = 12000 * 20 = 240000
    // preTaxProfit2 = 300000 - 240000 - 2000 - 500 = 57500
    // afterTaxProfit2 = 57500 - 10000 = 47500

    assertThat(result.getTotalPreTaxProfit()).isEqualTo(48800L + 57500L); // 106300
    assertThat(result.getTotalAfterTaxProfit()).isEqualTo(43800L + 47500L); // 91300

    // totalProfitRate = (totalAfterTaxProfit / totalInvestmentAmount) * 100 = (91300 / 340000) *
    // 100 = 26.853...
    assertThat(result.getTotalProfitRate())
        .isCloseTo(26.853, org.assertj.core.api.Assertions.offset(0.01));
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

    setupMocksWithSales(
        List.of(sale),
        List.of(allocation),
        List.of(subscription),
        List.of(investor),
        List.of(securitiesAccount),
        List.of(securitiesFirm),
        List.of(ipo));

    // When
    SummaryDto result = dashboardAggregationService.getSummary();

    // Then: totalInvestmentAmount = 0 (allocationPrice가 null이므로), totalProfitRate = 0.0 (AC-007)
    assertThat(result.getTotalInvestmentAmount()).isEqualTo(0L);
    assertThat(result.getTotalProfitRate()).isEqualTo(0.0);
    // 세전/세후수익은 여전히 계산됨 (acquisitionAmount = fixedPrice * saleQuantity = 9000 * 10 = 90000)
    // preTaxProfit = 150000 - 90000 - 1000 - 200 = 58800
    // afterTaxProfit = 58800 - 5000 = 53800
    assertThat(result.getTotalPreTaxProfit()).isEqualTo(58800L);
    assertThat(result.getTotalAfterTaxProfit()).isEqualTo(53800L);
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
  void getByInvestorGroupsByInvestorIdAndSortsAscending() {
    // Given: 2명의 투자자, 각각 다른 Sale 데이터
    // 투자자 1 (id=10): Sale 1개
    // 투자자 2 (id=20): Sale 2개

    Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
    Sale sale2 = createSale(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L);
    Sale sale3 = createSale(3L, 300L, 15L, 200000L, 1500L, 7000L, 300L);

    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 2000L, 100L, 12000L);
    Allocation allocation3 = createAllocation(300L, 3000L, 80L, 11000L);

    Subscription subscription1 = createSubscription(1000L, 10L, 1L, 1L, 100L);
    Subscription subscription2 = createSubscription(2000L, 20L, 2L, 2L, 100L);
    Subscription subscription3 = createSubscription(3000L, 20L, 2L, 2L, 100L);

    Investor investor1 = createInvestor(10L, 1L, "투자자 김철수");
    Investor investor2 = createInvestor(20L, 1L, "투자자 이영희");

    SecuritiesAccount securitiesAccount1 = createSecuritiesAccount(1L, 100L);
    SecuritiesAccount securitiesAccount2 = createSecuritiesAccount(2L, 200L);

    SecuritiesFirm securitiesFirm1 = createSecuritiesFirm(100L, "테스트 증권사 A");
    SecuritiesFirm securitiesFirm2 = createSecuritiesFirm(200L, "테스트 증권사 B");

    Ipo ipo1 = createIpo(1L, 9000L);
    Ipo ipo2 = createIpo(2L, 9500L);

    setupMocksWithSales(
        List.of(sale1, sale2, sale3),
        List.of(allocation1, allocation2, allocation3),
        List.of(subscription1, subscription2, subscription3),
        List.of(investor1, investor2),
        List.of(securitiesAccount1, securitiesAccount2),
        List.of(securitiesFirm1, securitiesFirm2),
        List.of(ipo1, ipo2));

    // When
    List<InvestorAggregationDto> result = dashboardAggregationService.getByInvestor();

    // Then: 투자자별로 그룹핑, investorId 오름차순 정렬
    assertThat(result).hasSize(2);

    // 첫 번째: investorId=10
    InvestorAggregationDto dto1 = result.get(0);
    assertThat(dto1.getInvestorId()).isEqualTo(10L);
    assertThat(dto1.getInvestorName()).isEqualTo("투자자 김철수");
    assertThat(dto1.getParticipationCount()).isEqualTo(1);

    // 두 번째: investorId=20 (2건의 Sale)
    InvestorAggregationDto dto2 = result.get(1);
    assertThat(dto2.getInvestorId()).isEqualTo(20L);
    assertThat(dto2.getInvestorName()).isEqualTo("투자자 이영희");
    assertThat(dto2.getParticipationCount()).isEqualTo(2);

    // 투자금 검증(단가 × 매도수량): investor1 = 10000*10 = 100000, investor2 = 12000*20 + 11000*15 =
    // 240000 + 165000 = 405000
    assertThat(dto1.getTotalInvestmentAmount()).isEqualTo(100000L);
    assertThat(dto2.getTotalInvestmentAmount()).isEqualTo(405000L);
  }

  @Test
  void getBySecuritiesFirmGroupsBySecuritiesFirmIdAndSortsAscending() {
    // Given: 2개 증권사, 각각 다른 Sale 데이터
    // 증권사 1 (id=100): Sale 1개
    // 증권사 2 (id=200): Sale 2개

    Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
    Sale sale2 = createSale(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L);
    Sale sale3 = createSale(3L, 300L, 15L, 200000L, 1500L, 7000L, 300L);

    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 2000L, 100L, 12000L);
    Allocation allocation3 = createAllocation(300L, 3000L, 80L, 11000L);

    Subscription subscription1 = createSubscription(1000L, 10L, 1L, 1L, 100L);
    Subscription subscription2 = createSubscription(2000L, 20L, 2L, 2L, 100L);
    Subscription subscription3 = createSubscription(3000L, 30L, 2L, 2L, 100L);

    Investor investor1 = createInvestor(10L, 1L, "투자자 김철수");
    Investor investor2 = createInvestor(20L, 1L, "투자자 이영희");
    Investor investor3 = createInvestor(30L, 1L, "투자자 박민수");

    SecuritiesAccount securitiesAccount1 = createSecuritiesAccount(1L, 100L);
    SecuritiesAccount securitiesAccount2 = createSecuritiesAccount(2L, 200L);

    SecuritiesFirm securitiesFirm1 = createSecuritiesFirm(100L, "테스트 증권사 A");
    SecuritiesFirm securitiesFirm2 = createSecuritiesFirm(200L, "테스트 증권사 B");

    Ipo ipo1 = createIpo(1L, 9000L);
    Ipo ipo2 = createIpo(2L, 9500L);

    setupMocksWithSales(
        List.of(sale1, sale2, sale3),
        List.of(allocation1, allocation2, allocation3),
        List.of(subscription1, subscription2, subscription3),
        List.of(investor1, investor2, investor3),
        List.of(securitiesAccount1, securitiesAccount2),
        List.of(securitiesFirm1, securitiesFirm2),
        List.of(ipo1, ipo2));

    // When
    List<SecuritiesFirmAggregationDto> result = dashboardAggregationService.getBySecuritiesFirm();

    // Then: 증권사별로 그룹핑, securitiesFirmId 오름차순 정렬
    assertThat(result).hasSize(2);

    // 첫 번째: securitiesFirmId=100
    SecuritiesFirmAggregationDto dto1 = result.get(0);
    assertThat(dto1.getSecuritiesFirmId()).isEqualTo(100L);
    assertThat(dto1.getSecuritiesFirmName()).isEqualTo("테스트 증권사 A");
    assertThat(dto1.getParticipationCount()).isEqualTo(1);

    // 두 번째: securitiesFirmId=200 (2건의 Sale)
    SecuritiesFirmAggregationDto dto2 = result.get(1);
    assertThat(dto2.getSecuritiesFirmId()).isEqualTo(200L);
    assertThat(dto2.getSecuritiesFirmName()).isEqualTo("테스트 증권사 B");
    assertThat(dto2.getParticipationCount()).isEqualTo(2);

    // 투자금 검증(단가 × 매도수량): firm1 = 10000*10 = 100000, firm2 = 12000*20 + 11000*15 = 405000
    assertThat(dto1.getTotalInvestmentAmount()).isEqualTo(100000L);
    assertThat(dto2.getTotalInvestmentAmount()).isEqualTo(405000L);
  }

  @Test
  void getBySecuritiesFirmExcludesNullSecuritiesFirmId() {
    // Given: securitiesFirmId가 null인 Sale 포함
    Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
    Sale sale2 = createSale(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L);

    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 2000L, 100L, 12000L);

    // securitiesAccount에 securitiesFirmId가 null인 경우
    Subscription subscription1 = createSubscription(1000L, 10L, 1L, 1L, 100L);
    Subscription subscription2 = createSubscription(2000L, 20L, 2L, 2L, 100L);

    Investor investor1 = createInvestor(10L, 1L, "투자자 김철수");
    Investor investor2 = createInvestor(20L, 1L, "투자자 이영희");

    // securitiesFirmId가 null인 계좌
    SecuritiesAccount securitiesAccount1 =
        SecuritiesAccount.builder()
            .id(1L)
            .securitiesFirmId(null)
            .accountNumber("1234567890")
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    SecuritiesAccount securitiesAccount2 = createSecuritiesAccount(2L, 200L);

    SecuritiesFirm securitiesFirm2 = createSecuritiesFirm(200L, "테스트 증권사 B");

    Ipo ipo1 = createIpo(1L, 9000L);
    Ipo ipo2 = createIpo(2L, 9500L);

    setupMocksWithSales(
        List.of(sale1, sale2),
        List.of(allocation1, allocation2),
        List.of(subscription1, subscription2),
        List.of(investor1, investor2),
        List.of(securitiesAccount1, securitiesAccount2),
        List.of(securitiesFirm2),
        List.of(ipo1, ipo2));

    // When
    List<SecuritiesFirmAggregationDto> result = dashboardAggregationService.getBySecuritiesFirm();

    // Then: securitiesFirmId가 null인 데이터는 제외되고, 유효한 증권사만 반환
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getSecuritiesFirmId()).isEqualTo(200L);
  }

  @Test
  void splitSalesOfOneAllocationProrateInvestmentBySoldQuantity() {
    Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
    Sale sale2 = createSale(2L, 100L, 20L, 300000L, 2000L, 10000L, 500L);
    Allocation allocation = createAllocation(100L, 1000L, 50L, 10000L);

    setupMocksWithSales(
        List.of(sale1, sale2),
        List.of(allocation),
        List.of(createSubscription(1000L, 10L, 1L, 1L, 100L)),
        List.of(createInvestor(10L, 1L, "테스트 투자자")),
        List.of(createSecuritiesAccount(1L, 100L)),
        List.of(createSecuritiesFirm(100L, "테스트 증권사")),
        List.of(createIpo(1L, 9000L)));

    SummaryDto summary = dashboardAggregationService.getSummary();

    // 매도 10주 + 20주 × 10000 = 300000 (배정 50주 중 판 30주 몫만 투자금)
    assertThat(summary.getTotalInvestmentAmount()).isEqualTo(300000L);
    assertThat(summary.getParticipationCount()).isEqualTo(1);
    assertThat(dashboardAggregationService.getByInvestor().get(0).getTotalInvestmentAmount())
        .isEqualTo(300000L);
  }

  @Test
  void missingSecuritiesFirmRowDoesNotFailAndIsExcludedFromFirmAggregation() {
    Sale sale1 = createSale(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L);
    Sale sale2 = createSale(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L);
    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 2000L, 100L, 12000L);
    Subscription subscription1 = createSubscription(1000L, 10L, 1L, 1L, 100L);
    Subscription subscription2 = createSubscription(2000L, 20L, 2L, 2L, 100L);
    Investor investor1 = createInvestor(10L, 1L, "투자자 김철수");
    Investor investor2 = createInvestor(20L, 1L, "투자자 이영희");
    // 계좌 1의 증권사(999)는 증권사 테이블에 행이 없다
    SecuritiesAccount securitiesAccount1 = createSecuritiesAccount(1L, 999L);
    SecuritiesAccount securitiesAccount2 = createSecuritiesAccount(2L, 200L);
    SecuritiesFirm securitiesFirm2 = createSecuritiesFirm(200L, "테스트 증권사 B");

    setupMocksWithSales(
        List.of(sale1, sale2),
        List.of(allocation1, allocation2),
        List.of(subscription1, subscription2),
        List.of(investor1, investor2),
        List.of(securitiesAccount1, securitiesAccount2),
        List.of(securitiesFirm2),
        List.of(createIpo(1L, 9000L), createIpo(2L, 9500L)));

    List<SecuritiesFirmAggregationDto> byFirm = dashboardAggregationService.getBySecuritiesFirm();
    SummaryDto summary = dashboardAggregationService.getSummary();

    assertThat(byFirm).hasSize(1);
    assertThat(byFirm.get(0).getSecuritiesFirmId()).isEqualTo(200L);
    assertThat(summary.getParticipationCount()).isEqualTo(2);
  }

  @Test
  void getByPeriodUsesIpoListingDateEvenWhenSoldInLaterMonth() {
    // 10월 상장 공모주를 10월·11월에 나눠 팔아도 기간은 상장월(2024-10) 하나다.
    Sale oct =
        createSaleWithDate(
            1L, 100L, 10L, 150000L, 0L, 0L, 0L, LocalDateTime.of(2024, 10, 20, 9, 0));
    Sale nov =
        createSaleWithDate(2L, 100L, 20L, 300000L, 0L, 0L, 0L, LocalDateTime.of(2024, 11, 5, 9, 0));

    setupMocksWithSales(
        List.of(oct, nov),
        List.of(createAllocation(100L, 1000L, 50L, 10000L)),
        List.of(createSubscription(1000L, 10L, 1L, 1L, 100L)),
        List.of(createInvestor(10L, 1L, "테스트 투자자")),
        List.of(createSecuritiesAccount(1L, 100L)),
        List.of(createSecuritiesFirm(100L, "테스트 증권사")),
        List.of(createIpo(1L, 10000L, LocalDate.of(2024, 10, 15))));

    List<PeriodAggregationDto> byMonth = dashboardAggregationService.getByPeriod("month");
    assertThat(byMonth).extracting(PeriodAggregationDto::getPeriod).containsExactly("2024-10");
    assertThat(byMonth.get(0).getTotalInvestmentAmount()).isEqualTo(300000L);
    assertThat(dashboardAggregationService.getByPeriod("month", 2024, 11)).isEmpty();
  }

  @Test
  void getByPeriodGroupsByMonthAndSortsAscending() {
    // Given: 3개월(2024-01, 2024-02, 2024-03)에 각각 Sale 데이터
    LocalDateTime jan = LocalDateTime.of(2024, 1, 15, 10, 0);
    LocalDateTime feb = LocalDateTime.of(2024, 2, 20, 10, 0);
    LocalDateTime mar = LocalDateTime.of(2024, 3, 10, 10, 0);

    Sale sale1 = createSaleWithDate(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L, jan);
    Sale sale2 = createSaleWithDate(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L, feb);
    Sale sale3 = createSaleWithDate(3L, 300L, 15L, 200000L, 1500L, 7000L, 300L, mar);
    Sale sale4 =
        createSaleWithDate(4L, 400L, 10L, 180000L, 1000L, 5000L, 200L, mar); // 같은 기간(3월)에 2건

    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 2000L, 100L, 12000L);
    Allocation allocation3 = createAllocation(300L, 3000L, 80L, 11000L);
    Allocation allocation4 = createAllocation(400L, 4000L, 60L, 10000L);

    Subscription subscription1 = createSubscription(1000L, 10L, 1L, 1L, 100L);
    Subscription subscription2 = createSubscription(2000L, 20L, 2L, 2L, 100L);
    Subscription subscription3 = createSubscription(3000L, 30L, 3L, 3L, 100L);
    Subscription subscription4 = createSubscription(4000L, 40L, 4L, 4L, 100L);

    Investor investor1 = createInvestor(10L, 1L, "투자자 A");
    Investor investor2 = createInvestor(20L, 1L, "투자자 B");
    Investor investor3 = createInvestor(30L, 1L, "투자자 C");
    Investor investor4 = createInvestor(40L, 1L, "투자자 D");

    SecuritiesAccount securitiesAccount1 = createSecuritiesAccount(1L, 100L);
    SecuritiesAccount securitiesAccount2 = createSecuritiesAccount(2L, 200L);
    SecuritiesAccount securitiesAccount3 = createSecuritiesAccount(3L, 300L);
    SecuritiesAccount securitiesAccount4 = createSecuritiesAccount(4L, 400L);

    SecuritiesFirm securitiesFirm1 = createSecuritiesFirm(100L, "증권사 A");
    SecuritiesFirm securitiesFirm2 = createSecuritiesFirm(200L, "증권사 B");
    SecuritiesFirm securitiesFirm3 = createSecuritiesFirm(300L, "증권사 C");
    SecuritiesFirm securitiesFirm4 = createSecuritiesFirm(400L, "증권사 D");

    Ipo ipo1 = createIpo(1L, 9000L);
    Ipo ipo2 = createIpo(2L, 9500L);
    Ipo ipo3 = createIpo(3L, 10000L);
    Ipo ipo4 = createIpo(4L, 11000L);

    setupMocksWithSales(
        List.of(sale1, sale2, sale3, sale4),
        List.of(allocation1, allocation2, allocation3, allocation4),
        List.of(subscription1, subscription2, subscription3, subscription4),
        List.of(investor1, investor2, investor3, investor4),
        List.of(securitiesAccount1, securitiesAccount2, securitiesAccount3, securitiesAccount4),
        List.of(securitiesFirm1, securitiesFirm2, securitiesFirm3, securitiesFirm4),
        List.of(ipo1, ipo2, ipo3, ipo4));

    // When
    List<PeriodAggregationDto> result = dashboardAggregationService.getByPeriod("month");

    // Then: 월별로 그룹핑, period 오름차순 정렬 (2024-01, 2024-02, 2024-03)
    assertThat(result).hasSize(3);

    // 2024-01: 1건
    PeriodAggregationDto dto1 = result.get(0);
    assertThat(dto1.getPeriod()).isEqualTo("2024-01");
    assertThat(dto1.getParticipationCount()).isEqualTo(1);
    assertThat(dto1.getTotalInvestmentAmount()).isEqualTo(10L * 10000L); // 100000

    // 2024-02: 1건
    PeriodAggregationDto dto2 = result.get(1);
    assertThat(dto2.getPeriod()).isEqualTo("2024-02");
    assertThat(dto2.getParticipationCount()).isEqualTo(1);
    assertThat(dto2.getTotalInvestmentAmount()).isEqualTo(20L * 12000L); // 240000

    // 2024-03: 2건
    PeriodAggregationDto dto3 = result.get(2);
    assertThat(dto3.getPeriod()).isEqualTo("2024-03");
    assertThat(dto3.getParticipationCount()).isEqualTo(2);
    // 15*11000 + 10*10000 = 165000 + 100000 = 265000
    assertThat(dto3.getTotalInvestmentAmount()).isEqualTo(265000L);
  }

  @Test
  void getByPeriodGroupsByYearAndSortsAscending() {
    // Given: 3년(2023, 2024, 2025)에 각각 Sale 데이터
    LocalDateTime sale1Date = LocalDateTime.of(2023, 6, 15, 10, 0);
    LocalDateTime sale2Date = LocalDateTime.of(2024, 3, 20, 10, 0);
    LocalDateTime sale3Date = LocalDateTime.of(2024, 11, 10, 10, 0); // 같은 해(2024)에 2건
    LocalDateTime sale4Date = LocalDateTime.of(2025, 1, 10, 10, 0);

    Sale sale1 = createSaleWithDate(1L, 100L, 10L, 150000L, 1000L, 5000L, 200L, sale1Date);
    Sale sale2 = createSaleWithDate(2L, 200L, 20L, 300000L, 2000L, 10000L, 500L, sale2Date);
    Sale sale3 = createSaleWithDate(3L, 300L, 15L, 200000L, 1500L, 7000L, 300L, sale3Date);
    Sale sale4 = createSaleWithDate(4L, 400L, 10L, 180000L, 1000L, 5000L, 200L, sale4Date);

    Allocation allocation1 = createAllocation(100L, 1000L, 50L, 10000L);
    Allocation allocation2 = createAllocation(200L, 2000L, 100L, 12000L);
    Allocation allocation3 = createAllocation(300L, 3000L, 80L, 11000L);
    Allocation allocation4 = createAllocation(400L, 4000L, 60L, 10000L);

    Subscription subscription1 = createSubscription(1000L, 10L, 1L, 1L, 100L);
    Subscription subscription2 = createSubscription(2000L, 20L, 2L, 2L, 100L);
    Subscription subscription3 = createSubscription(3000L, 30L, 3L, 3L, 100L);
    Subscription subscription4 = createSubscription(4000L, 40L, 4L, 4L, 100L);

    Investor investor1 = createInvestor(10L, 1L, "투자자 A");
    Investor investor2 = createInvestor(20L, 1L, "투자자 B");
    Investor investor3 = createInvestor(30L, 1L, "투자자 C");
    Investor investor4 = createInvestor(40L, 1L, "투자자 D");

    SecuritiesAccount securitiesAccount1 = createSecuritiesAccount(1L, 100L);
    SecuritiesAccount securitiesAccount2 = createSecuritiesAccount(2L, 200L);
    SecuritiesAccount securitiesAccount3 = createSecuritiesAccount(3L, 300L);
    SecuritiesAccount securitiesAccount4 = createSecuritiesAccount(4L, 400L);

    SecuritiesFirm securitiesFirm1 = createSecuritiesFirm(100L, "증권사 A");
    SecuritiesFirm securitiesFirm2 = createSecuritiesFirm(200L, "증권사 B");
    SecuritiesFirm securitiesFirm3 = createSecuritiesFirm(300L, "증권사 C");
    SecuritiesFirm securitiesFirm4 = createSecuritiesFirm(400L, "증권사 D");

    Ipo ipo1 = createIpo(1L, 9000L);
    Ipo ipo2 = createIpo(2L, 9500L);
    Ipo ipo3 = createIpo(3L, 10000L);
    Ipo ipo4 = createIpo(4L, 11000L);

    setupMocksWithSales(
        List.of(sale1, sale2, sale3, sale4),
        List.of(allocation1, allocation2, allocation3, allocation4),
        List.of(subscription1, subscription2, subscription3, subscription4),
        List.of(investor1, investor2, investor3, investor4),
        List.of(securitiesAccount1, securitiesAccount2, securitiesAccount3, securitiesAccount4),
        List.of(securitiesFirm1, securitiesFirm2, securitiesFirm3, securitiesFirm4),
        List.of(ipo1, ipo2, ipo3, ipo4));

    // When
    List<PeriodAggregationDto> result = dashboardAggregationService.getByPeriod("year");

    // Then: 연도별로 그룹핑, period 오름차순 정렬 (2023, 2024, 2025)
    assertThat(result).hasSize(3);

    // 2023: 1건
    PeriodAggregationDto dto1 = result.get(0);
    assertThat(dto1.getPeriod()).isEqualTo("2023");
    assertThat(dto1.getParticipationCount()).isEqualTo(1);
    assertThat(dto1.getTotalInvestmentAmount()).isEqualTo(10L * 10000L); // 100000

    // 2024: 2건
    PeriodAggregationDto dto2 = result.get(1);
    assertThat(dto2.getPeriod()).isEqualTo("2024");
    assertThat(dto2.getParticipationCount()).isEqualTo(2);
    // 20*12000 + 15*11000 = 240000 + 165000 = 405000
    assertThat(dto2.getTotalInvestmentAmount()).isEqualTo(405000L);

    // 2025: 1건
    PeriodAggregationDto dto3 = result.get(2);
    assertThat(dto3.getPeriod()).isEqualTo("2025");
    assertThat(dto3.getParticipationCount()).isEqualTo(1);
    assertThat(dto3.getTotalInvestmentAmount()).isEqualTo(10L * 10000L); // 100000
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
    verify(saleRepository).findAllByStatusAndInvestorAccountId(RecordStatus.ACTIVE, 1L);
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
