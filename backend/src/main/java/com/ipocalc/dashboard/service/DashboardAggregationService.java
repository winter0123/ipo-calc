package com.ipocalc.dashboard.service;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.dashboard.dto.InvestorAggregationDto;
import com.ipocalc.dashboard.dto.PeriodAggregationDto;
import com.ipocalc.dashboard.dto.SecuritiesFirmAggregationDto;
import com.ipocalc.dashboard.dto.SummaryDto;
import com.ipocalc.dashboard.service.ownership.DashboardOwnershipVerifier;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 집계 서비스. 요약, 투자자별, 증권사별, 기간별 집계를 제공한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAggregationService {

  private final DashboardOwnershipVerifier ownershipVerifier;
  private final SaleRepository saleRepository;
  private final AllocationRepository allocationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final InvestorRepository investorRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final IpoRepository ipoRepository;

  /** 현재 계정 소유 ACTIVE Sale을 연관 엔티티와 함께 배치 조회해 건별 수익을 계산한다. 4개 집계 엔드포인트가 공유한다. */
  private List<CalculatedSale> loadCalculatedSales() {
    Long currentAccountId = ownershipVerifier.getCurrentAccountId();
    List<Sale> activeSales =
        saleRepository.findAllByStatusAndInvestorAccountId(RecordStatus.ACTIVE, currentAccountId);

    if (activeSales.isEmpty()) {
      return Collections.emptyList();
    }

    List<Long> allocationIds = activeSales.stream().map(Sale::getAllocationId).distinct().toList();
    Map<Long, Allocation> allocationMap =
        indexById(allocationRepository.findByIdIn(allocationIds), Allocation::getId);

    Set<Long> subscriptionIds =
        allocationMap.values().stream()
            .map(Allocation::getSubscriptionId)
            .collect(Collectors.toSet());
    List<Subscription> subscriptions = subscriptionRepository.findAllById(subscriptionIds);
    Map<Long, Subscription> subscriptionMap = indexById(subscriptions, Subscription::getId);

    Set<Long> investorIds =
        subscriptions.stream().map(Subscription::getInvestorId).collect(Collectors.toSet());
    Map<Long, Investor> investorMap =
        indexById(investorRepository.findAllById(investorIds), Investor::getId);

    Set<Long> securitiesAccountIds =
        subscriptions.stream()
            .map(Subscription::getSecuritiesAccountId)
            .collect(Collectors.toSet());
    List<SecuritiesAccount> securitiesAccounts =
        securitiesAccountRepository.findAllById(securitiesAccountIds);
    Map<Long, SecuritiesAccount> securitiesAccountMap =
        indexById(securitiesAccounts, SecuritiesAccount::getId);

    Set<Long> securitiesFirmIds =
        securitiesAccounts.stream()
            .map(SecuritiesAccount::getSecuritiesFirmId)
            .collect(Collectors.toSet());
    Map<Long, SecuritiesFirm> securitiesFirmMap =
        indexById(securitiesFirmRepository.findAllById(securitiesFirmIds), SecuritiesFirm::getId);

    Set<Long> ipoIds =
        subscriptions.stream().map(Subscription::getIpoId).collect(Collectors.toSet());
    Map<Long, Ipo> ipoMap = indexById(ipoRepository.findAllById(ipoIds), Ipo::getId);

    List<CalculatedSale> calculatedSales = new ArrayList<>();
    for (Sale sale : activeSales) {
      Allocation allocation = allocationMap.get(sale.getAllocationId());
      if (allocation == null) {
        continue; // Allocation이 없으면 건너뜀 (데이터 무결성 문제)
      }

      Subscription subscription = subscriptionMap.get(allocation.getSubscriptionId());
      if (subscription == null) {
        continue;
      }

      Investor investor = investorMap.get(subscription.getInvestorId());
      if (investor == null) {
        continue;
      }

      SecuritiesAccount securitiesAccount =
          securitiesAccountMap.get(subscription.getSecuritiesAccountId());
      SecuritiesFirm securitiesFirm =
          (securitiesAccount != null)
              ? securitiesFirmMap.get(securitiesAccount.getSecuritiesFirmId())
              : null;

      calculatedSales.add(
          CalculatedSale.of(
              sale,
              allocation,
              investor,
              securitiesFirm != null ? securitiesFirm.getId() : null,
              securitiesFirm != null ? securitiesFirm.getName() : null,
              ipoMap.get(subscription.getIpoId())));
    }

    return calculatedSales;
  }

  private static <T> Map<Long, T> indexById(List<T> items, Function<T, Long> idOf) {
    Map<Long, T> map = new HashMap<>();
    for (T item : items) {
      map.put(idOf.apply(item), item);
    }
    return map;
  }

  /** 전체 집계. 매도 기록이 없으면 모든 필드 0인 SummaryDto (AC-006). */
  public SummaryDto getSummary() {
    return DashboardAggregator.summarize(loadCalculatedSales());
  }

  /** 투자자별 집계. investorId 오름차순 (AC-002), 데이터가 없으면 빈 리스트. */
  public List<InvestorAggregationDto> getByInvestor() {
    return DashboardAggregator.byInvestor(loadCalculatedSales());
  }

  /** 증권사별 집계. securitiesFirmId 오름차순 (AC-003), securitiesFirmId가 null인 데이터는 제외. */
  public List<SecuritiesFirmAggregationDto> getBySecuritiesFirm() {
    return DashboardAggregator.bySecuritiesFirm(loadCalculatedSales());
  }

  /**
   * 기간별 집계. period 오름차순 (AC-004, AC-005), 데이터가 없으면 빈 리스트.
   *
   * @param unit "month" 또는 "year" (Controller에서 사전 검증됨)
   */
  public List<PeriodAggregationDto> getByPeriod(String unit) {
    return getByPeriod(unit, null, null);
  }

  /** 기간별 집계를 연/월로 좁힌다. {@link DashboardAggregator#byPeriod(List, String, Integer, Integer)} 참고. */
  public List<PeriodAggregationDto> getByPeriod(String unit, Integer year, Integer month) {
    return DashboardAggregator.byPeriod(loadCalculatedSales(), unit, year, month);
  }
}
