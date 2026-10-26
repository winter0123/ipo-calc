package com.ipocalc.investmentqueryapi.service.investmenthistory;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 투자 내역 페이지에서 필요한 참조 데이터를 배치로 읽어 조립 가능한 형태로 제공한다. */
@Component
@RequiredArgsConstructor
public class InvestmentHistoryReadModelLoader {

  private final AllocationRepository allocationRepository;
  private final IpoRepository ipoRepository;
  private final SaleRepository saleRepository;
  private final InvestorRepository investorRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final SecuritiesFirmRepository securitiesFirmRepository;

  /** 청약 1건의 상세 화면 조립에 필요한 참조 데이터를 조회한다. */
  public DetailReadModel loadDetail(Subscription subscription) {
    Long subscriptionId = subscription.getId();
    Allocation allocation =
        allocationRepository
            .findBySubscriptionIdAndStatusAndDeletedAtIsNull(subscriptionId, RecordStatus.ACTIVE)
            .orElse(null);

    List<Sale> activeSales = List.of();
    if (allocation != null) {
      activeSales =
          saleRepository
              .findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(
                  allocation.getId(), RecordStatus.ACTIVE);
    }

    Long ipoId = subscription.getIpoId();
    Map<Long, Ipo> ipoMap =
        ipoId == null
            ? Map.of()
            : ipoRepository.findById(ipoId).map(ipo -> Map.of(ipoId, ipo)).orElseGet(Map::of);

    Investor investor = investorRepository.findById(subscription.getInvestorId()).orElse(null);
    String investorName = investor != null ? investor.getDisplayName() : null;

    SecuritiesAccount securitiesAccount =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(subscription.getSecuritiesAccountId())
            .orElse(null);
    String securitiesFirmName = null;
    String securitiesAccountName = null;
    if (securitiesAccount != null) {
      securitiesAccountName = securitiesAccount.getAccountName();
      securitiesFirmName =
          securitiesFirmRepository
              .findByIdAndDeletedAtIsNull(securitiesAccount.getSecuritiesFirmId())
              .map(SecuritiesFirm::getName)
              .orElse(null);
    }

    return new DetailReadModel(
        allocation,
        activeSales,
        ipoMap,
        investorName,
        securitiesFirmName,
        subscription.getSecuritiesAccountId(),
        securitiesAccountName);
  }

  /** 페이지에 포함된 청약과 연관된 데이터를 배치 조회한다. */
  public ReadModel load(List<Subscription> subscriptions) {
    if (subscriptions.isEmpty()) {
      return ReadModel.empty();
    }

    List<Long> subscriptionIds = subscriptions.stream().map(Subscription::getId).toList();
    Map<Long, Allocation> allocationMap =
        allocationRepository.findBySubscriptionIdIn(subscriptionIds).stream()
            .collect(Collectors.toMap(Allocation::getSubscriptionId, Function.identity()));

    List<Long> allocationIds = allocationMap.values().stream().map(Allocation::getId).toList();
    Map<Long, List<Sale>> salesByAllocationId =
        allocationIds.isEmpty()
            ? Map.of()
            : saleRepository
                .findByAllocationIdInAndStatus(allocationIds, RecordStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(Sale::getAllocationId));
    Map<Long, List<Sale>> saleMapBySubscriptionId =
        allocationMap.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        salesByAllocationId.getOrDefault(entry.getValue().getId(), List.of())));

    List<Long> ipoIds = subscriptions.stream().map(Subscription::getIpoId).distinct().toList();
    Map<Long, Ipo> ipoMap =
        ipoRepository.findAllById(ipoIds).stream()
            .collect(Collectors.toMap(Ipo::getId, Function.identity()));

    Set<Long> investorIds =
        subscriptions.stream().map(Subscription::getInvestorId).collect(Collectors.toSet());
    Map<Long, String> investorNameMap =
        investorRepository.findAllById(investorIds).stream()
            .collect(Collectors.toMap(Investor::getId, Investor::getDisplayName));

    Set<Long> securitiesAccountIds =
        subscriptions.stream()
            .map(Subscription::getSecuritiesAccountId)
            .collect(Collectors.toSet());
    Map<Long, SecuritiesAccount> securitiesAccountMap =
        securitiesAccountRepository.findAllById(securitiesAccountIds).stream()
            .collect(Collectors.toMap(SecuritiesAccount::getId, Function.identity()));
    Set<Long> securitiesFirmIds =
        securitiesAccountMap.values().stream()
            .map(SecuritiesAccount::getSecuritiesFirmId)
            .collect(Collectors.toSet());
    Map<Long, SecuritiesFirm> securitiesFirmMap =
        securitiesFirmRepository.findAllById(securitiesFirmIds).stream()
            .collect(Collectors.toMap(SecuritiesFirm::getId, Function.identity()));

    Map<Long, String> securitiesFirmNameMap = new LinkedHashMap<>();
    Map<Long, String> securitiesAccountNameMap = new LinkedHashMap<>();
    for (Map.Entry<Long, SecuritiesAccount> entry : securitiesAccountMap.entrySet()) {
      SecuritiesFirm firm = securitiesFirmMap.get(entry.getValue().getSecuritiesFirmId());
      securitiesFirmNameMap.put(entry.getKey(), firm != null ? firm.getName() : null);
      securitiesAccountNameMap.put(entry.getKey(), entry.getValue().getAccountName());
    }

    return new ReadModel(
        allocationMap,
        saleMapBySubscriptionId,
        ipoMap,
        investorNameMap,
        securitiesFirmNameMap,
        securitiesAccountNameMap);
  }

  /** DTO 조립에 필요한 페이지 참조 데이터 묶음이다. */
  public record ReadModel(
      Map<Long, Allocation> allocationMap,
      Map<Long, List<Sale>> saleMap,
      Map<Long, Ipo> ipoMap,
      Map<Long, String> investorNameMap,
      Map<Long, String> securitiesFirmNameMap,
      Map<Long, String> securitiesAccountNameMap) {

    private static ReadModel empty() {
      return new ReadModel(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
  }

  /** 단일 청약 상세의 DTO 조립 입력이다. */
  public record DetailReadModel(
      Allocation allocation,
      List<Sale> activeSales,
      Map<Long, Ipo> ipoMap,
      String investorName,
      String securitiesFirmName,
      Long securitiesAccountId,
      String securitiesAccountName) {}
}
