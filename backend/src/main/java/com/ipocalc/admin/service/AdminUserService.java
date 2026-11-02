package com.ipocalc.admin.service;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.admin.dto.AdminUserDetailResponse;
import com.ipocalc.admin.dto.AdminUserListResponse;
import com.ipocalc.admin.dto.AllocationSummaryResponse;
import com.ipocalc.admin.dto.InvestorSummaryResponse;
import com.ipocalc.admin.dto.SaleSummaryResponse;
import com.ipocalc.admin.dto.SubscriptionSummaryResponse;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.profitcalculation.service.profit.calculator.ProfitCalculator;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 회원 관리 서비스. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

  private static final Set<String> ALLOWED_SORT_FIELDS =
      Set.of("createdAt", "email", "name", "nickname", "id");

  private final AccountRepository accountRepository;
  private final InvestorRepository investorRepository;
  private final SubscriptionRepository subscriptionRepository;
  // removed to avoid bean ambiguity; use subscriptionRepository (extends query repo)
  private final AllocationRepository allocationRepository;
  // removed to avoid bean ambiguity; use allocationRepository
  private final SaleRepository saleRepository;
  private final IpoRepository ipoRepository;

  /** 회원 목록을 조회한다 (페이징, 검색, 정렬 지원). */
  @Transactional(readOnly = true)
  public Page<AdminUserListResponse> getUserList(
      int page, int size, String search, String role, Boolean approved, Boolean locked, Sort sort) {

    if (size < 1 || size > 100) {
      throw new IllegalArgumentException("페이지 크기는 1~100 사이여야 합니다");
    }
    for (Sort.Order order : sort) {
      if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
        throw new IllegalArgumentException("정렬 필드가 허용되지 않습니다: " + order.getProperty());
      }
    }
    if (role != null && !role.isBlank()) {
      try {
        AccountRole.valueOf(role.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("잘못된 역할 값: " + role);
      }
    }

    org.springframework.data.domain.Pageable pageable =
        org.springframework.data.domain.PageRequest.of(page, size, sort);

    Specification<Account> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (search != null && !search.isBlank()) {
            String searchTerm = "%" + search.toLowerCase() + "%";
            Predicate emailPredicate = cb.like(cb.lower(root.get("email")), searchTerm);
            Predicate namePredicate = cb.like(cb.lower(root.get("name")), searchTerm);
            Predicate nicknamePredicate = cb.like(cb.lower(root.get("nickname")), searchTerm);
            predicates.add(cb.or(emailPredicate, namePredicate, nicknamePredicate));
          }

          if (role != null && !role.isBlank()) {
            AccountRole roleEnum = AccountRole.valueOf(role.toUpperCase());
            predicates.add(cb.equal(root.get("role"), roleEnum));
          }

          if (approved != null) {
            predicates.add(cb.equal(root.get("approved"), approved));
          }

          if (locked != null) {
            if (locked) {
              predicates.add(cb.isNotNull(root.get("lockedUntil")));
              predicates.add(
                  cb.greaterThan(root.get("lockedUntil"), java.time.LocalDateTime.now()));
            } else {
              predicates.add(
                  cb.or(
                      cb.isNull(root.get("lockedUntil")),
                      cb.lessThanOrEqualTo(
                          root.get("lockedUntil"), java.time.LocalDateTime.now())));
            }
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return accountRepository.findAll(spec, pageable).map(this::toListResponse);
  }

  private AdminUserListResponse toListResponse(Account account) {
    Long accountId = account.getId();
    int investorCount = (int) investorRepository.countByAccountIdAndDeletedAtIsNull(accountId);
    int subscriptionCount = 0;
    List<Investor> accountInvestors =
        investorRepository.findAllByAccountIdAndDeletedAtIsNull(account.getId());
    for (Investor inv : accountInvestors) {
      subscriptionCount +=
          (int) subscriptionRepository.countByInvestorIdAndDeletedAtIsNull(inv.getId());
    }
    return AdminUserListResponse.builder()
        .id(account.getId())
        .email(account.getEmail())
        .name(account.getName())
        .nickname(account.getNickname())
        .role(account.getRole().name())
        .approved(account.isApproved())
        .locked(account.isAccountLocked(java.time.LocalDateTime.now()))
        .lockedUntil(account.getLockedUntil())
        .lastLoginAt(account.getLastLoginAt())
        .createdAt(account.getCreatedAt())
        .investorCount(investorCount)
        .subscriptionCount(subscriptionCount)
        .build();
  }

  /** 회원 상세 조회. */
  @Transactional(readOnly = true)
  public AdminUserDetailResponse getUserDetail(Long accountId) {
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(
                () ->
                    new com.ipocalc.accountauthentication.exception.AccountNotFoundException(
                        "회원을 찾을 수 없습니다"));

    List<Investor> investors =
        investorRepository.findAllByAccountIdAndDeletedAtIsNull(account.getId());

    final List<AdminUserDetailResponse.InvestorSummaryResponse> investorResponses =
        investors.stream()
            .map(
                inv ->
                    AdminUserDetailResponse.InvestorSummaryResponse.builder()
                        .id(inv.getId())
                        .displayName(inv.getDisplayName())
                        .relationship(inv.getRelationship())
                        .status(inv.getStatus().name())
                        .build())
            .collect(Collectors.toList());

    // 통계 조회 - 실제 repository 사용
    long subscriptionCount = 0L;
    long allocationCount = 0L;
    long saleCount = 0L;
    long totalProfit = 0L;

    List<Investor> accountInvestors =
        investorRepository.findAllByAccountIdAndDeletedAtIsNull(account.getId());
    for (Investor inv : accountInvestors) {
      subscriptionCount += subscriptionRepository.countByInvestorIdAndDeletedAtIsNull(inv.getId());
    }

    // 배정은 subscriptions를 통해 조회하지 않고 투자자별 조회가 더 복잡하므로, 현재는 투자자 기반으로 단순 집계
    // 실제로는 allocation repository의 findAllByAccountWithFilters를 사용해야 하지만,
    // 해당 repository는 QueryDSL 기반이므로 단순 count는 별도 쿼리로 수행
    // 여기서는 기존 repository 메서드로 간결하게 처리
    List<Long> investorIds =
        accountInvestors.stream().map(Investor::getId).collect(Collectors.toList());

    for (Investor inv : accountInvestors) {
      List<Subscription> subs =
          subscriptionRepository.findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(
              inv.getId());
      for (Subscription sub : subs) {
        List<Allocation> allocations =
            allocationRepository.findBySubscriptionIdInAndDeletedAtIsNull(
                java.util.Collections.singletonList(sub.getId()));
        allocationCount += allocations.size();
      }
    }

    // Sale count: 투자자별 배정 기반으로 조회
    for (Investor inv : accountInvestors) {
      List<Subscription> subs =
          subscriptionRepository.findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(
              inv.getId());
      for (Subscription sub : subs) {
        List<Allocation> allocations =
            allocationRepository.findBySubscriptionIdInAndDeletedAtIsNull(
                java.util.Collections.singletonList(sub.getId()));
        for (Allocation alloc : allocations) {
          List<Sale> sales =
              saleRepository
                  .findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(
                      alloc.getId(),
                      com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
                  .stream()
                  .filter(
                      s ->
                          s.getStatus()
                              == com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
                  .toList();
          saleCount += sales.size();
          for (Sale s : sales) {
            Allocation a = allocationRepository.findById(s.getAllocationId()).orElse(null);
            Long fixedPrice = null;
            if (a != null && a.getAllocationPrice() == null) {
              Subscription su = subscriptionRepository.findById(a.getSubscriptionId()).orElse(null);
              if (su != null) {
                Ipo ipo = ipoRepository.findByIdAndDeletedAtIsNull(su.getIpoId()).orElse(null);
                if (ipo != null) {
                  fixedPrice = ipo.getFixedPrice();
                }
              }
            }
            long acq =
                ProfitCalculator.calculateAcquisitionAmount(
                    a == null ? null : a.getAllocationPrice(), fixedPrice, s.getQuantity());
            long realized =
                ProfitCalculator.calculateRealizedProfit(
                    s.getAmount(), acq, s.getTax(), s.getOtherCost());
            totalProfit += realized;
          }
        }
      }
    }

    return AdminUserDetailResponse.builder()
        .id(accountId)
        .email(account.getEmail())
        .name(account.getName())
        .nickname(account.getNickname())
        .role(account.getRole().name())
        .approved(account.isApproved())
        .locked(account.isAccountLocked(java.time.LocalDateTime.now()))
        .lockedUntil(account.getLockedUntil())
        .lastLoginAt(account.getLastLoginAt())
        .createdAt(account.getCreatedAt())
        .investors(investorResponses)
        .stats(
            AdminUserDetailResponse.UserStats.builder()
                .subscriptionCount((int) subscriptionCount)
                .allocationCount((int) allocationCount)
                .saleCount((int) saleCount)
                .totalProfit(totalProfit)
                .build())
        .build();
  }

  /** 회원 투자자 목록 조회. */
  public List<InvestorSummaryResponse> getInvestors(Long accountId) {
    return investorRepository.findAllByAccountIdAndDeletedAtIsNull(accountId).stream()
        .map(
            inv ->
                InvestorSummaryResponse.builder()
                    .id(inv.getId())
                    .displayName(inv.getDisplayName())
                    .relationship(inv.getRelationship())
                    .status(inv.getStatus().name())
                    .build())
        .collect(Collectors.toList());
  }

  /** 회원의 청약 내역 조회. */
  public Page<SubscriptionSummaryResponse> getSubscriptions(Long accountId, Pageable pageable) {
    return subscriptionRepository
        .findByInvestorAccountId(accountId, pageable)
        .map(this::toSubscriptionResponse);
  }

  private SubscriptionSummaryResponse toSubscriptionResponse(Subscription sub) {
    return SubscriptionSummaryResponse.builder()
        .id(sub.getId())
        .quantity(sub.getQuantity())
        .depositAmount(sub.getDepositAmount())
        .subscribedAt(sub.getSubscribedAt() != null ? sub.getSubscribedAt().toString() : null)
        .status(sub.getStatus() != null ? sub.getStatus().name() : null)
        .build();
  }

  /** 회원의 배정 내역 조회. */
  public Page<AllocationSummaryResponse> getAllocations(Long accountId, Pageable pageable) {
    return allocationRepository
        .findPageByAccountWithFilters(accountId, null, null, pageable)
        .map(this::toAllocationResponse);
  }

  private AllocationSummaryResponse toAllocationResponse(Allocation alloc) {
    return AllocationSummaryResponse.builder()
        .id(alloc.getId())
        .allocatedQuantity(alloc.getAllocatedQuantity())
        .status(alloc.getStatus() != null ? alloc.getStatus().name() : null)
        .build();
  }

  /** 회원의 매도 내역 조회. */
  public Page<SaleSummaryResponse> getSales(Long accountId, Pageable pageable) {
    return saleRepository
        .findActivePageByFilters(accountId, null, null, null, pageable)
        .map(this::toSaleResponse);
  }

  private SaleSummaryResponse toSaleResponse(Sale sale) {
    return SaleSummaryResponse.builder()
        .id(sale.getId())
        .quantity(sale.getQuantity())
        .price(sale.getPrice())
        .amount(sale.getAmount())
        .fee(sale.getFee())
        .tax(sale.getTax())
        .otherCost(sale.getOtherCost())
        .soldAt(sale.getSaleDate())
        .status(sale.getStatus() != null ? sale.getStatus().name() : null)
        .build();
  }
}
