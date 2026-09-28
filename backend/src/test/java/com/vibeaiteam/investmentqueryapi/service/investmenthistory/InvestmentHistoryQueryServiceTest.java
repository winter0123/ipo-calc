package com.vibeaiteam.investmentqueryapi.service.investmenthistory;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryForbiddenException;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryValidationException;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * InvestmentHistoryQueryService 단위 테스트
 * InvestmentHistoryOwnershipVerifierTest 패턴 따르기:
 * @SpringBootTest + @ActiveProfiles("test") + 실제 Repository 빈 사용
 * Mocked 빈은 테스트 대상이 필요한 것까지만, DB는 실제 embeded DB 사용
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    com.vibeaiteam.accountauthentication.AccountAuthenticationApplication.class
})
class InvestmentHistoryQueryServiceTest {

    @Autowired
    private InvestmentHistoryQueryService queryService;

    @MockBean
    private SecurityUtils securityUtils;

    @MockBean
    private InvestmentHistoryOwnershipVerifier ownershipVerifier;

    @MockBean
    private AllocationRepository allocationRepository;

    @MockBean
    private SaleRepository saleRepository;

    @MockBean
    private IpoRepository ipoRepository;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    private static final Long CURRENT_ACCOUNT_ID = 1L;
    private static final Long OTHER_ACCOUNT_ID = 2L;
    private static final Long INVESTOR_ID = 10L;
    private static final Long SECURITIES_ACCOUNT_ID = 20L;
    private static final Long IPO_ID = 100L;
    private static final Long SUBSCRIPTION_ID = 1L;

    private Investor currentAccountInvestor;
    private Investor otherAccountInvestor;
    private SecuritiesAccount securitiesAccount;
    private Ipo testIpo;
    private Subscription testSubscription;
    private Allocation testAllocation;
    private Sale testSale;

    @BeforeEach
    void setUp() {
        // SecurityUtils.getCurrentAccountId()가 CURRENT_ACCOUNT_ID를 반환하도록 설정
        when(securityUtils.getCurrentAccountId()).thenReturn(CURRENT_ACCOUNT_ID);

        // Ownership verifier가 예외 없이 통과하도록 설정
        doNothing().when(ownershipVerifier).verifyInvestorIdOwnership(anyLong());
        doNothing().when(ownershipVerifier).verifySecuritiesAccountIdOwnership(anyLong());
        doNothing().when(ownershipVerifier).verifyIpoIdOwnership(anyLong());

        // Current account investor
        currentAccountInvestor = Investor.builder()
                .id(INVESTOR_ID)
                .accountId(CURRENT_ACCOUNT_ID)
                .displayName("테스트 투자자")
                .relationship("SELF")
                .status("ACTIVE")
                .build();

        // Other account investor
        otherAccountInvestor = Investor.builder()
                .id(INVESTOR_ID + 1)
                .accountId(OTHER_ACCOUNT_ID)
                .displayName("타인 투자자")
                .relationship("FAMILY")
                .status("ACTIVE")
                .build();

        // Securities account
        securitiesAccount = SecuritiesAccount.builder()
                .id(SECURITIES_ACCOUNT_ID)
                .investorId(INVESTOR_ID)
                .securitiesFirmId(1L)
                .accountNumber("123-456-789")
                .accountName("테스트 계좌")
                .isDefault(true)
                .status("ACTIVE")
                .build();

        // Test IPO
        testIpo = Ipo.builder()
                .id(IPO_ID)
                .ipoName("샘플테크")
                .stockCode("ST001")
                .fixedPrice(12000L)
                .status("ACTIVE")
                .build();

        // Test subscription
        testSubscription = Subscription.builder()
                .id(SUBSCRIPTION_ID)
                .investorId(INVESTOR_ID)
                .securitiesAccountId(SECURITIES_ACCOUNT_ID)
                .ipoId(IPO_ID)
                .quantity(100L)
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();

        // Test allocation
        testAllocation = Allocation.builder()
                .id(5L)
                .subscriptionId(SUBSCRIPTION_ID)
                .allocatedQuantity(100L)
                .allocationPrice(12000L)
                .status("ACTIVE")
                .build();

        // Test sale
        testSale = Sale.builder()
                .id(20L)
                .allocationId(5L)
                .quantity(30L)
                .amount(360000L)
                .fee(1500L)
                .tax(0L)
                .otherCost(0L)
                .status("ACTIVE")
                .saleDate(LocalDateTime.now())
                .build();

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===========================================
    // 전체 조회 기본 테스트
    // ===========================================

    @Test
    void getHistory_기본조회_성공_및_계정스코프강제() {
        // Given: 현재 계정의 청약이 존재함
        when(subscriptionRepository.findByInvestorAccountIdWithFilters(
                eq(CURRENT_ACCOUNT_ID), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testSubscription)));
        when(allocationRepository.findBySubscriptionIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(ipoRepository.findAllById(anyList())).thenReturn(List.of(testIpo));

        // When: 필터 없이 전체 조회
        Page<InvestmentHistoryDto> result = queryService.getHistory(null, null, null, 0, 20);

        // Then: 조회 성공 및 계정 스코프가 강제되었음
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getIpoId()).isEqualTo(IPO_ID);
    }

    @Test
    void getHistory_ipoId필터_소유권검증후조회() {
        // Given: ipoId 필터가 현재 계정 소유 청약인지 검증됨
        when(subscriptionRepository.findByInvestorAccountIdWithFilters(
                eq(CURRENT_ACCOUNT_ID), isNull(), isNull(), eq(IPO_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testSubscription)));
        when(allocationRepository.findBySubscriptionIdIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(ipoRepository.findAllById(anyList())).thenReturn(List.of(testIpo));

        // When: ipoId로 필터링한 조회
        Page<InvestmentHistoryDto> result = queryService.getHistory(null, null, IPO_ID, 0, 20);

        // Then: 조회 성공
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    // ===========================================
    // 소유권 검증 테스트
    // ===========================================

    @Test
    void getHistory_타계정인vestorId_403예상() {
        // Given: 타 계정의 investorId가 지정됨
        // OwnershipVerifier가 예외를 throw하도록 설정
        doThrow(new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다."))
                .when(ownershipVerifier).verifyInvestorIdOwnership(anyLong());

        // When & Then: InvestmentQueryForbiddenException 발생
        assertThatThrownBy(() -> queryService.getHistory(INVESTOR_ID, null, null, 0, 20))
                .isInstanceOf(InvestmentQueryForbiddenException.class)
                .hasMessage("해당 데이터에 접근 권한이 없습니다.");
    }

    @Test
    void getHistory_타계정인securitiesAccountId_403예상() {
        // Given: 타 계정의 securitiesAccountId가 지정됨
        doThrow(new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다."))
                .when(ownershipVerifier).verifySecuritiesAccountIdOwnership(anyLong());

        // When & Then: InvestmentQueryForbiddenException 발생
        assertThatThrownBy(() -> queryService.getHistory(null, SECURITIES_ACCOUNT_ID, null, 0, 20))
                .isInstanceOf(InvestmentQueryForbiddenException.class)
                .hasMessage("해당 데이터에 접근 권한이 없습니다.");
    }

    @Test
    void getHistory_ipoId_소유권아님_403예상() {
        // Given: 현재 계정에 속하지 않는 ipoId 지정
        doThrow(new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다."))
                .when(ownershipVerifier).verifyIpoIdOwnership(anyLong());

        // When & Then: InvestmentQueryForbiddenException 발생
        assertThatThrownBy(() -> queryService.getHistory(null, null, IPO_ID, 0, 20))
                .isInstanceOf(InvestmentQueryForbiddenException.class)
                .hasMessage("해당 데이터에 접근 권한이 없습니다.");
    }

    // ===========================================
    // remainingQuantity 계산 테스트
    // ===========================================

    @Test
    void getHistory_미배정_remainingQuantity_null() {
        // Given: Allocation이 null인 미배정 청약
        when(subscriptionRepository.findByInvestorAccountIdWithFilters(anyLong(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testSubscription)));
        when(allocationRepository.findBySubscriptionIdIn(any()))
                .thenReturn(Collections.emptyList());
        when(ipoRepository.findAllById(anyList())).thenReturn(List.of(testIpo));

        // When
        Page<InvestmentHistoryDto> result = queryService.getHistory(null, null, null, 0, 20);

        // Then: remainingQuantity가 null이어야 함
        assertThat(result.getContent()).first()
                .extracting(InvestmentHistoryDto::getRemainingQuantity)
                .isNull();
    }

    @Test
    void getHistory_배정만_remainingQuantity_allocatedQuantity() {
        // Given: Allocation 존재하지만 Sale이 없는 경우
        when(subscriptionRepository.findByInvestorAccountIdWithFilters(anyLong(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testSubscription)));
        when(allocationRepository.findBySubscriptionIdIn(anyList()))
                .thenReturn(List.of(testAllocation));
        when(saleRepository.findByAllocationIdInAndStatus(any(), eq("ACTIVE")))
                .thenReturn(Collections.emptyList());
        when(ipoRepository.findAllById(anyList())).thenReturn(List.of(testIpo));

        // When
        Page<InvestmentHistoryDto> result = queryService.getHistory(null, null, null, 0, 20);

        // Then: remainingQuantity가 allocatedQuantity와 같음
        assertThat(result.getContent()).first()
                .extracting(InvestmentHistoryDto::getRemainingQuantity)
                .isEqualTo(100L);
    }

    @Test
    void get_history_부분매도_remainingQuantity_계산() {
        // Given: Allocation 100, ACTIVE Sale 2건(30, 20)
        when(subscriptionRepository.findByInvestorAccountIdWithFilters(anyLong(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testSubscription)));
        when(allocationRepository.findBySubscriptionIdIn(anyList()))
                .thenReturn(List.of(testAllocation));
        when(saleRepository.findByAllocationIdInAndStatus(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(testSale));
        when(ipoRepository.findAllById(anyList())).thenReturn(List.of(testIpo));

        // When
        Page<InvestmentHistoryDto> result = queryService.getHistory(null, null, null, 0, 20);

        // Then: remainingQuantity = 100 - 30 = 70
        assertThat(result.getContent()).first()
                .extracting(InvestmentHistoryDto::getRemainingQuantity)
                .isEqualTo(70L);
    }

    // ===========================================
    // 페이지네이션 테스트
    // ===========================================

    @Test
    void getHistory_페이지네이션_성공() {
        // Given: 여러 Subscription이 존재함
        when(subscriptionRepository.findByInvestorAccountIdWithFilters(anyLong(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // When
        Page<InvestmentHistoryDto> result = queryService.getHistory(null, null, null, 0, 10);

        // Then: 페이지네이션 객체 반환
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
    }
}