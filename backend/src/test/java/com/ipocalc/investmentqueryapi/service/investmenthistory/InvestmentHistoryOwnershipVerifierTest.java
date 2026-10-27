package com.ipocalc.investmentqueryapi.service.investmenthistory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.investmentqueryapi.exception.InvestmentQueryForbiddenException;
import com.ipocalc.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
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
 * InvestmentHistoryOwnershipVerifier 단위 테스트
 *
 * <p>이 Epic의 보안 핵심 로직인 소유권 검증을 검증한다: 1. investorId 필터: investorRepository.findById →
 * Investor.accountId 비교 2. securitiesAccountId 필터: SecuritiesAccount → Investor(accountId) 2홉 비교 3.
 * ipoId 필터: subscriptionRepository.existsByIpoIdAndInvestorAccountId 4. 단건 조회: Subscription의
 * Investor.accountId 비교
 *
 * <p>각 검증에 대해 성공/실패(403, 404) 케이스를 모두 테스트한다.
 */
@SpringBootTest(classes = com.ipocalc.IpoCalcApplication.class)
@ActiveProfiles("test")
class InvestmentHistoryOwnershipVerifierTest {

  @Autowired private InvestmentHistoryOwnershipVerifier ownershipVerifier;

  @MockitoBean private SecurityUtils securityUtils;

  @MockitoBean private InvestorRepository investorRepository;

  @MockitoBean private SecuritiesAccountRepository securitiesAccountRepository;

  @MockitoBean private SubscriptionRepository subscriptionRepository;

  private static final Long CURRENT_ACCOUNT_ID = 1L;
  private static final Long OTHER_ACCOUNT_ID = 2L;
  private static final Long INVESTOR_ID = 10L;
  private static final Long SECURITIES_ACCOUNT_ID = 20L;
  private static final Long IPO_ID = 100L;

  private Investor currentAccountInvestor;
  private Investor otherAccountInvestor;
  private SecuritiesAccount securitiesAccount;
  private SecuritiesAccount otherAccountSecuritiesAccount;

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

    securitiesAccount =
        SecuritiesAccount.builder()
            .id(SECURITIES_ACCOUNT_ID)
            .investorId(INVESTOR_ID)
            .securitiesFirmId(1L)
            .accountNumber("123-456-789")
            .accountName("테스트 계좌")
            .isDefault(true)
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    otherAccountSecuritiesAccount =
        SecuritiesAccount.builder()
            .id(SECURITIES_ACCOUNT_ID + 1)
            .investorId(INVESTOR_ID + 1)
            .securitiesFirmId(1L)
            .accountNumber("987-654-321")
            .accountName("타인 계좌")
            .isDefault(false)
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
  // investorId 필터 검증 테스트
  // ===========================================

  @Test
  void verifyInvestorIdOwnershipSucceedsWhenInvestorBelongsToCurrentAccount() {
    // Given: investorId가 현재 계정 소유의 투자자임
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(currentAccountInvestor));

    // When & Then: 예외 없이 통과
    ownershipVerifier.verifyInvestorIdOwnership(INVESTOR_ID);
  }

  @Test
  void verifyInvestorIdOwnershipThrowsForbiddenWhenInvestorBelongsToOtherAccount() {
    // Given: investorId가 타 계정 소유의 투자자임
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(otherAccountInvestor));

    // When & Then: InvestmentQueryForbiddenException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifyInvestorIdOwnership(INVESTOR_ID))
        .isInstanceOf(InvestmentQueryForbiddenException.class)
        .hasMessage("해당 데이터에 접근 권한이 없습니다.");
  }

  @Test
  void verifyInvestorIdOwnershipThrowsNotFoundWhenInvestorDoesNotExist() {
    // Given: investorId에 해당하는 투자자가 없음
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.empty());

    // When & Then: InvestmentQueryNotFoundException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifyInvestorIdOwnership(INVESTOR_ID))
        .isInstanceOf(InvestmentQueryNotFoundException.class)
        .hasMessage("해당 투자자를 찾을 수 없습니다.");
  }

  // ===========================================
  // securitiesAccountId 필터 검증 테스트 (2홉)
  // ===========================================

  @Test
  void verifySecuritiesAccountIdOwnershipSucceedsWhenSecuritiesAccountBelongsToCurrentAccount() {
    // Given: securitiesAccountId가 현재 계정 소유의 증권계좌임 (2홉 경유)
    when(securitiesAccountRepository.findById(SECURITIES_ACCOUNT_ID))
        .thenReturn(Optional.of(securitiesAccount));
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(currentAccountInvestor));

    // When & Then: 예외 없이 통과
    ownershipVerifier.verifySecuritiesAccountIdOwnership(SECURITIES_ACCOUNT_ID);
  }

  @Test
  void verifySecuritiesAccountIdOwnershipThrowsForbiddenWhenOtherAccountOwnsIt() {
    // Given: securitiesAccountId가 타 계정 소유의 증권계좌임 (2홉 경유)
    when(securitiesAccountRepository.findById(SECURITIES_ACCOUNT_ID))
        .thenReturn(Optional.of(otherAccountSecuritiesAccount));
    when(investorRepository.findById(INVESTOR_ID + 1))
        .thenReturn(Optional.of(otherAccountInvestor));

    // When & Then: InvestmentQueryForbiddenException 발생
    assertThatThrownBy(
            () -> ownershipVerifier.verifySecuritiesAccountIdOwnership(SECURITIES_ACCOUNT_ID))
        .isInstanceOf(InvestmentQueryForbiddenException.class)
        .hasMessage("해당 데이터에 접근 권한이 없습니다.");
  }

  @Test
  void verifySecuritiesAccountIdOwnershipThrowsNotFoundWhenSecuritiesAccountDoesNotExist() {
    // Given: securitiesAccountId에 해당하는 증권계좌가 없음
    when(securitiesAccountRepository.findById(SECURITIES_ACCOUNT_ID)).thenReturn(Optional.empty());

    // When & Then: InvestmentQueryNotFoundException 발생
    assertThatThrownBy(
            () -> ownershipVerifier.verifySecuritiesAccountIdOwnership(SECURITIES_ACCOUNT_ID))
        .isInstanceOf(InvestmentQueryNotFoundException.class)
        .hasMessage("해당 증권 계좌를 찾을 수 없습니다.");
  }

  @Test
  void verifySecuritiesAccountIdOwnershipThrowsNotFoundWhenInvestorDoesNotExist() {
    // Given: 증권계좌는 있지만 연결된 투자자가 없음
    when(securitiesAccountRepository.findById(SECURITIES_ACCOUNT_ID))
        .thenReturn(Optional.of(securitiesAccount));
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.empty());

    // When & Then: InvestmentQueryNotFoundException 발생
    assertThatThrownBy(
            () -> ownershipVerifier.verifySecuritiesAccountIdOwnership(SECURITIES_ACCOUNT_ID))
        .isInstanceOf(InvestmentQueryNotFoundException.class)
        .hasMessage("해당 투자자를 찾을 수 없습니다.");
  }

  // ===========================================
  // ipoId 필터 검증 테스트
  // ===========================================

  @Test
  void verifyIpoIdOwnershipSucceedsWhenSubscriptionExistsForCurrentAccount() {
    // Given: 현재 계정 소유 청약 중 해당 IPO 건이 존재함
    when(subscriptionRepository.existsByIpoIdAndInvestorAccountId(IPO_ID, CURRENT_ACCOUNT_ID))
        .thenReturn(true);

    // When & Then: 예외 없이 통과
    ownershipVerifier.verifyIpoIdOwnership(IPO_ID);
  }

  @Test
  void verifyIpoIdOwnershipThrowsForbiddenWhenNoSubscriptionForCurrentAccount() {
    // Given: 현재 계정 소유 청약 중 해당 IPO 건이 없음
    when(subscriptionRepository.existsByIpoIdAndInvestorAccountId(IPO_ID, CURRENT_ACCOUNT_ID))
        .thenReturn(false);

    // When & Then: InvestmentQueryForbiddenException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifyIpoIdOwnership(IPO_ID))
        .isInstanceOf(InvestmentQueryForbiddenException.class)
        .hasMessage("해당 데이터에 접근 권한이 없습니다.");
  }

  // ===========================================
  // 단건 조회 Subscription 소유권 검증 테스트
  // ===========================================

  @Test
  void verifySubscriptionOwnershipSucceedsWhenSubscriptionBelongsToCurrentAccount() {
    // Given: Subscription의 Investor가 현재 계정 소유임
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(currentAccountInvestor));

    // When & Then: 예외 없이 통과
    ownershipVerifier.verifySubscriptionOwnership(INVESTOR_ID);
  }

  @Test
  void verifySubscriptionOwnershipThrowsForbiddenWhenSubscriptionBelongsToOtherAccount() {
    // Given: Subscription의 Investor가 타 계정 소유임
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(otherAccountInvestor));

    // When & Then: InvestmentQueryForbiddenException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySubscriptionOwnership(INVESTOR_ID))
        .isInstanceOf(InvestmentQueryForbiddenException.class)
        .hasMessage("해당 데이터에 접근 권한이 없습니다.");
  }

  @Test
  void verifySubscriptionOwnershipThrowsNotFoundWhenInvestorDoesNotExist() {
    // Given: Subscription의 Investor가 존재하지 않음
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.empty());

    // When & Then: InvestmentQueryNotFoundException 발생
    assertThatThrownBy(() -> ownershipVerifier.verifySubscriptionOwnership(INVESTOR_ID))
        .isInstanceOf(InvestmentQueryNotFoundException.class)
        .hasMessage("해당 투자자를 찾을 수 없습니다.");
  }
}
