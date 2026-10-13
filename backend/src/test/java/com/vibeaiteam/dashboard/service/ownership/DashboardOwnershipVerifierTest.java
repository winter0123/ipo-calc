package com.vibeaiteam.dashboard.service.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
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
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * DashboardOwnershipVerifier 단위 테스트
 *
 * <p>이 Epic의 보안 핵심 로직인 소유권 검증을 검증한다: 1. Sale ID를 통한 소유권 검증: Sale → Allocation → Subscription →
 * Investor 경유로 현재 계정 소유 확인 2. 현재 계정 ID 획득
 *
 * <p>각 검증에 대해 성공/실패(403, 404) 케이스를 모두 테스트한다.
 */
@SpringBootTest(
    classes = com.vibeaiteam.accountauthentication.AccountAuthenticationApplication.class)
@ActiveProfiles("test")
class DashboardOwnershipVerifierTest {

  @Autowired private DashboardOwnershipVerifier ownershipVerifier;

  @MockitoBean private SecurityUtils securityUtils;

  @MockitoBean private SaleRepository saleRepository;

  @MockitoBean private AllocationRepository allocationRepository;

  @MockitoBean private SubscriptionRepository subscriptionRepository;

  @MockitoBean private InvestorRepository investorRepository;

  private static final Long CURRENT_ACCOUNT_ID = 1L;
  private static final Long OTHER_ACCOUNT_ID = 2L;
  private static final Long SALE_ID = 100L;
  private static final Long INVESTOR_ID = 10L;
  private static final Long ALLOCATION_ID = 50L;
  private static final Long SUBSCRIPTION_ID = 200L;

  private Sale currentAccountSale;
  private Sale otherAccountSale;
  private Investor currentAccountInvestor;
  private Investor otherAccountInvestor;
  private Allocation currentAccountAllocation;
  private Allocation otherAccountAllocation;
  private Subscription currentAccountSubscription;
  private Subscription otherAccountSubscription;

  @BeforeEach
  void setUp() {
    currentAccountInvestor =
        Investor.builder()
            .id(INVESTOR_ID)
            .accountId(CURRENT_ACCOUNT_ID)
            .displayName("테스트 투자자")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    otherAccountInvestor =
        Investor.builder()
            .id(INVESTOR_ID + 1)
            .accountId(OTHER_ACCOUNT_ID)
            .displayName("타인 투자자")
            .relationship("FAMILY")
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    currentAccountSubscription =
        Subscription.builder()
            .id(SUBSCRIPTION_ID)
            .investorId(INVESTOR_ID)
            .securitiesAccountId(1L)
            .ipoId(1L)
            .quantity(100L)
            .depositAmount(5000000L)
            .subscribedAt(LocalDateTime.now())
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    otherAccountSubscription =
        Subscription.builder()
            .id(SUBSCRIPTION_ID + 1)
            .investorId(INVESTOR_ID + 1)
            .securitiesAccountId(1L)
            .ipoId(1L)
            .quantity(100L)
            .depositAmount(5000000L)
            .subscribedAt(LocalDateTime.now())
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    currentAccountAllocation =
        Allocation.builder()
            .id(ALLOCATION_ID)
            .subscriptionId(SUBSCRIPTION_ID)
            .allocatedQuantity(50L)
            .allocatedAt(LocalDateTime.now())
            .allocationMethod("PROPORTIONAL")
            .allocationPrice(10000L)
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    otherAccountAllocation =
        Allocation.builder()
            .id(ALLOCATION_ID + 1)
            .subscriptionId(SUBSCRIPTION_ID + 1)
            .allocatedQuantity(50L)
            .allocatedAt(LocalDateTime.now())
            .allocationMethod("PROPORTIONAL")
            .allocationPrice(10000L)
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    currentAccountSale =
        Sale.builder()
            .id(SALE_ID)
            .allocationId(ALLOCATION_ID)
            .quantity(10L)
            .amount(100000L)
            .saleDate(LocalDateTime.now())
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    otherAccountSale =
        Sale.builder()
            .id(SALE_ID + 1)
            .allocationId(ALLOCATION_ID + 1)
            .quantity(20L)
            .amount(200000L)
            .saleDate(LocalDateTime.now())
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // SecurityUtils.getCurrentAccountId()가 CURRENT_ACCOUNT_ID를 반환하도록 설정
    when(securityUtils.getCurrentAccountId()).thenReturn(CURRENT_ACCOUNT_ID);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ===========================================
  // Sale 소유권 검증 테스트
  // ===========================================

  @Test
  void verifySaleOwnershipSucceedsWhenSaleBelongsToCurrentAccount() {
    // Given: Sale이 현재 계정 소유의 투자자 경유로 연결됨
    when(saleRepository.findByIdAndDeletedAtIsNull(SALE_ID))
        .thenReturn(Optional.of(currentAccountSale));
    when(allocationRepository.findByIdAndDeletedAtIsNull(ALLOCATION_ID))
        .thenReturn(Optional.of(currentAccountAllocation));
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(SUBSCRIPTION_ID))
        .thenReturn(Optional.of(currentAccountSubscription));
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(currentAccountInvestor));

    // When & Then: 예외 없이 통과
    ownershipVerifier.verifySaleOwnership(SALE_ID);
  }

  @Test
  void verifySaleOwnershipThrowsForbiddenWhenSaleBelongsToOtherAccount() {
    // Given: Sale이 타 계정 소유의 투자자 경유로 연결됨
    when(saleRepository.findByIdAndDeletedAtIsNull(SALE_ID))
        .thenReturn(Optional.of(otherAccountSale));
    when(allocationRepository.findByIdAndDeletedAtIsNull(ALLOCATION_ID + 1))
        .thenReturn(Optional.of(otherAccountAllocation));
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(SUBSCRIPTION_ID + 1))
        .thenReturn(Optional.of(otherAccountSubscription));
    when(investorRepository.findById(INVESTOR_ID + 1))
        .thenReturn(Optional.of(otherAccountInvestor));

    // When & Then: DashboardForbiddenException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySaleOwnership(SALE_ID))
        .isInstanceOf(DashboardForbiddenException.class)
        .hasMessage("해당 데이터에 접근 권한이 없습니다.");
  }

  @Test
  void verifySaleOwnershipThrowsNotFoundWhenSaleDoesNotExist() {
    // Given: Sale ID에 해당하는 매도 기록이 없음
    when(saleRepository.findByIdAndDeletedAtIsNull(SALE_ID)).thenReturn(Optional.empty());

    // When & Then: DashboardNotFoundException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySaleOwnership(SALE_ID))
        .isInstanceOf(DashboardNotFoundException.class)
        .hasMessage("해당 매도 기록을 찾을 수 없습니다.");
  }

  @Test
  void verifySaleOwnershipThrowsNotFoundWhenAllocationDoesNotExist() {
    // Given: Sale은 있지만 연결된 배정이 없음
    when(saleRepository.findByIdAndDeletedAtIsNull(SALE_ID))
        .thenReturn(Optional.of(currentAccountSale));
    when(allocationRepository.findByIdAndDeletedAtIsNull(ALLOCATION_ID))
        .thenReturn(Optional.empty());

    // When & Then: DashboardNotFoundException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySaleOwnership(SALE_ID))
        .isInstanceOf(DashboardNotFoundException.class)
        .hasMessage("해당 배정 기록을 찾을 수 없습니다.");
  }

  @Test
  void verifySaleOwnershipThrowsNotFoundWhenSubscriptionDoesNotExist() {
    // Given: Sale, Allocation은 있지만 연결된 청약이 없음
    when(saleRepository.findByIdAndDeletedAtIsNull(SALE_ID))
        .thenReturn(Optional.of(currentAccountSale));
    when(allocationRepository.findByIdAndDeletedAtIsNull(ALLOCATION_ID))
        .thenReturn(Optional.of(currentAccountAllocation));
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(SUBSCRIPTION_ID))
        .thenReturn(Optional.empty());

    // When & Then: DashboardNotFoundException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySaleOwnership(SALE_ID))
        .isInstanceOf(DashboardNotFoundException.class)
        .hasMessage("해당 청약 기록을 찾을 수 없습니다.");
  }

  @Test
  void verifySaleOwnershipThrowsNotFoundWhenInvestorDoesNotExist() {
    // Given: Sale, Allocation, Subscription은 있지만 연결된 투자자가 없음
    when(saleRepository.findByIdAndDeletedAtIsNull(SALE_ID))
        .thenReturn(Optional.of(currentAccountSale));
    when(allocationRepository.findByIdAndDeletedAtIsNull(ALLOCATION_ID))
        .thenReturn(Optional.of(currentAccountAllocation));
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(SUBSCRIPTION_ID))
        .thenReturn(Optional.of(currentAccountSubscription));
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.empty());

    // When & Then: DashboardNotFoundException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySaleOwnership(SALE_ID))
        .isInstanceOf(DashboardNotFoundException.class)
        .hasMessage("해당 투자자를 찾을 수 없습니다.");
  }

  // ===========================================
  // 현재 계정 ID 획득 테스트
  // ===========================================

  @Test
  void getCurrentAccountIdReturnsSecurityUtilsValue() {
    // Given: SecurityUtils가 특정 계정 ID 반환하도록 설정
    Long expectedAccountId = 42L;
    when(securityUtils.getCurrentAccountId()).thenReturn(expectedAccountId);

    // When
    Long actualAccountId = ownershipVerifier.getCurrentAccountId();

    // Then
    assertThat(actualAccountId).isEqualTo(expectedAccountId);
  }
}
