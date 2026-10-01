package com.vibeaiteam.subscriptionmanagement.service.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipErrors;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionOwnershipTest {

  private static final Long ME = 100L;
  private static final OwnershipErrors ERRORS =
      new OwnershipErrors(NotFound::new, Forbidden::new);

  private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
  private final InvestorRepository investorRepository = mock(InvestorRepository.class);
  private final SecurityUtils securityUtils = mock(SecurityUtils.class);
  private SubscriptionOwnership ownership;

  @BeforeEach
  void setUp() {
    ownership =
        new SubscriptionOwnership(
            subscriptionRepository, new OwnershipGuard(securityUtils, investorRepository));
    when(securityUtils.getCurrentAccountId()).thenReturn(ME);
  }

  private Subscription subscription(Long investorId) {
    return Subscription.builder().id(7L).investorId(investorId).build();
  }

  private void investorOwnedBy(Long investorId, Long accountId) {
    when(investorRepository.findById(investorId))
        .thenReturn(Optional.of(Investor.builder().id(investorId).accountId(accountId).build()));
  }

  @Test
  void activeReturnsOwnedSubscription() {
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L))
        .thenReturn(Optional.of(subscription(1L)));
    investorOwnedBy(1L, ME);

    assertThat(ownership.requireOwnedActive(7L, ERRORS).getId()).isEqualTo(7L);
  }

  @Test
  void missingSubscriptionThrowsDomainNotFoundWithStandardMessage() {
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.requireOwnedActive(7L, ERRORS))
        .isInstanceOf(NotFound.class)
        .hasMessage("청약 기록을 찾을 수 없습니다.");
  }

  @Test
  void missingInvestorThrowsDomainNotFound() {
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L))
        .thenReturn(Optional.of(subscription(1L)));
    when(investorRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.requireOwnedActive(7L, ERRORS))
        .isInstanceOf(NotFound.class)
        .hasMessage("투자자를 찾을 수 없습니다.");
  }

  @Test
  void otherAccountsSubscriptionThrowsDomainForbidden() {
    when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L))
        .thenReturn(Optional.of(subscription(1L)));
    investorOwnedBy(1L, 200L);

    assertThatThrownBy(() -> ownership.requireOwnedActive(7L, ERRORS))
        .isInstanceOf(Forbidden.class)
        .hasMessage("계정 소유권이 없습니다.");
  }

  @Test
  void includingDeletedLooksUpWithoutTheDeletedFilterButStillChecksOwner() {
    when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription(1L)));
    investorOwnedBy(1L, 200L);

    assertThatThrownBy(() -> ownership.requireOwnedIncludingDeleted(7L, ERRORS))
        .isInstanceOf(Forbidden.class);
    verify(subscriptionRepository, never()).findByIdAndDeletedAtIsNull(7L);
  }

  static class NotFound extends RuntimeException {
    NotFound(String message) {
      super(message);
    }
  }

  static class Forbidden extends RuntimeException {
    Forbidden(String message) {
      super(message);
    }
  }
}
