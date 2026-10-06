package com.vibeaiteam.allocationmanagement.service.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipErrors;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.vibeaiteam.subscriptionmanagement.service.ownership.SubscriptionOwnership;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllocationOwnershipTest {

  private static final Long ME = 100L;
  private static final OwnershipErrors ERRORS = new OwnershipErrors(NotFound::new, Forbidden::new);

  private final AllocationRepository allocationRepository = mock(AllocationRepository.class);
  private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
  private final InvestorRepository investorRepository = mock(InvestorRepository.class);
  private final SecurityUtils securityUtils = mock(SecurityUtils.class);
  private AllocationOwnership ownership;

  @BeforeEach
  void setUp() {
    ownership =
        new AllocationOwnership(
            allocationRepository,
            new SubscriptionOwnership(
                subscriptionRepository, new OwnershipGuard(securityUtils, investorRepository)));
    when(securityUtils.getCurrentAccountId()).thenReturn(ME);
  }

  private void chain(Long accountId) {
    when(subscriptionRepository.findById(20L))
        .thenReturn(Optional.of(Subscription.builder().id(20L).investorId(1L).build()));
    when(investorRepository.findById(1L))
        .thenReturn(Optional.of(Investor.builder().id(1L).accountId(accountId).build()));
  }

  private Allocation allocation() {
    return Allocation.builder().id(30L).subscriptionId(20L).build();
  }

  @Test
  void activeAllocationOwnedThroughSubscriptionIsReturned() {
    when(allocationRepository.findByIdAndDeletedAtIsNull(30L))
        .thenReturn(Optional.of(allocation()));
    chain(ME);

    assertThat(ownership.requireOwnedActive(30L, ERRORS).getId()).isEqualTo(30L);
  }

  @Test
  void missingAllocationThrowsDomainNotFoundWithStandardMessage() {
    when(allocationRepository.findByIdAndDeletedAtIsNull(30L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.requireOwnedActive(30L, ERRORS))
        .isInstanceOf(NotFound.class)
        .hasMessage("배정 기록을 찾을 수 없습니다.");
  }

  @Test
  void parentSubscriptionMissingThrowsNotFound() {
    when(allocationRepository.findByIdAndDeletedAtIsNull(30L))
        .thenReturn(Optional.of(allocation()));
    when(subscriptionRepository.findById(20L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.requireOwnedActive(30L, ERRORS))
        .isInstanceOf(NotFound.class)
        .hasMessage("청약 기록을 찾을 수 없습니다.");
  }

  @Test
  void otherAccountsAllocationThrowsDomainForbidden() {
    when(allocationRepository.findByIdAndDeletedAtIsNull(30L))
        .thenReturn(Optional.of(allocation()));
    chain(200L);

    assertThatThrownBy(() -> ownership.requireOwnedActive(30L, ERRORS))
        .isInstanceOf(Forbidden.class)
        .hasMessage("계정 소유권이 없습니다.");
  }

  @Test
  void includingDeletedUsesPlainFindByIdForTheAllocation() {
    when(allocationRepository.findById(30L)).thenReturn(Optional.of(allocation()));
    chain(ME);

    assertThat(ownership.requireOwnedIncludingDeleted(30L, ERRORS).getId()).isEqualTo(30L);
    verify(allocationRepository, never()).findByIdAndDeletedAtIsNull(30L);
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
