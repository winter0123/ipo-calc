package com.ipocalc.investormanagement.service.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** OwnershipGuard 단위 테스트: 소유자 일치, 타 계정 소유, 대상 없음. */
@ExtendWith(MockitoExtension.class)
class OwnershipGuardTest {

  private static final Long CURRENT_ACCOUNT_ID = 1L;
  private static final Long INVESTOR_ID = 10L;

  @Mock private SecurityUtils securityUtils;
  @Mock private InvestorRepository investorRepository;

  private OwnershipGuard guard;

  @BeforeEach
  void setUp() {
    guard = new OwnershipGuard(securityUtils, investorRepository);
    when(securityUtils.getCurrentAccountId()).thenReturn(CURRENT_ACCOUNT_ID);
  }

  private static Investor investorOf(Long accountId) {
    return Investor.builder().id(INVESTOR_ID).accountId(accountId).build();
  }

  @Test
  void requireOwnerReturnsCurrentAccountIdWhenOwnerMatches() {
    Investor owned = investorOf(CURRENT_ACCOUNT_ID);

    Long result = guard.requireOwner(owned, () -> new IllegalStateException("forbidden"));

    assertThat(result).isEqualTo(CURRENT_ACCOUNT_ID);
  }

  @Test
  void requireOwnerThrowsSuppliedExceptionWhenOwnerDiffers() {
    Investor other = investorOf(2L);

    assertThatThrownBy(
            () -> guard.requireOwner(other, () -> new IllegalStateException("forbidden")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forbidden");
  }

  @Test
  void requireOwnedInvestorReturnsInvestorWhenOwnerMatches() {
    Investor owned = investorOf(CURRENT_ACCOUNT_ID);
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(owned));

    Investor result =
        guard.requireOwnedInvestor(
            INVESTOR_ID,
            () -> new IllegalArgumentException("not found"),
            () -> new IllegalStateException("forbidden"));

    assertThat(result).isSameAs(owned);
  }

  @Test
  void requireOwnedInvestorThrowsForbiddenWhenOwnerDiffers() {
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.of(investorOf(2L)));

    assertThatThrownBy(
            () ->
                guard.requireOwnedInvestor(
                    INVESTOR_ID,
                    () -> new IllegalArgumentException("not found"),
                    () -> new IllegalStateException("forbidden")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forbidden");
  }

  @Test
  void requireOwnedInvestorThrowsNotFoundWhenInvestorMissing() {
    when(investorRepository.findById(INVESTOR_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                guard.requireOwnedInvestor(
                    INVESTOR_ID,
                    () -> new IllegalArgumentException("not found"),
                    () -> new IllegalStateException("forbidden")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("not found");
  }
}
