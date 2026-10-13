package com.vibeaiteam.brokerintegration.service.matching;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.validation.StockCodes;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 증권계좌의 청약·배정·매도·공모주를 모아 증권사 매도와 맞춰 볼 후보를 만든다.
 *
 * <p>조회만 하며 저장하지 않는다. 활성(ACTIVE) 청약·배정·매도만 대상이다.
 */
@Component
@RequiredArgsConstructor
public class MatchCandidateAssembler {

  private final SubscriptionRepository subscriptionRepository;
  private final AllocationRepository allocationRepository;
  private final IpoRepository ipoRepository;
  private final SaleRepository saleRepository;

  /**
   * 증권계좌에 속한 활성 배정 ID 집합을 돌려준다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 활성 배정 ID 집합
   */
  public Set<Long> allocationIds(Long securitiesAccountId) {
    return loadAllocations(securitiesAccountId).stream()
        .map(Allocation::getId)
        .collect(Collectors.toSet());
  }

  private List<Allocation> loadAllocations(Long securitiesAccountId) {
    List<Long> subscriptionIds =
        loadSubscriptions(securitiesAccountId).stream().map(Subscription::getId).toList();
    return subscriptionIds.isEmpty()
        ? List.of()
        : allocationRepository.findBySubscriptionIdIn(subscriptionIds).stream()
            .filter(a -> a.getStatus() == RecordStatus.ACTIVE)
            .toList();
  }

  private List<Subscription> loadSubscriptions(Long securitiesAccountId) {
    return subscriptionRepository
        .findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(securitiesAccountId)
        .stream()
        .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
        .toList();
  }

  /**
   * 증권계좌의 활성 배정을 증권사 매도와 맞춰 볼 후보로 만든다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 배정별 종목코드·배정 수량·이미 등록된 매도를 담은 후보 목록
   */
  public List<MatchCandidate> candidates(Long securitiesAccountId) {
    List<Subscription> subscriptions = loadSubscriptions(securitiesAccountId);
    if (subscriptions.isEmpty()) {
      return List.of();
    }
    Map<Long, Subscription> subscriptionById =
        subscriptions.stream().collect(Collectors.toMap(Subscription::getId, s -> s));
    Map<Long, Ipo> ipoById =
        ipoRepository
            .findAllById(subscriptions.stream().map(Subscription::getIpoId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Ipo::getId, i -> i));
    List<Allocation> allocations = loadAllocations(securitiesAccountId);
    Map<Long, List<ExistingSale>> salesByAllocation =
        existingSales(allocations.stream().map(Allocation::getId).toList());
    return allocations.stream()
        .map(
            a -> {
              Ipo ipo = ipoById.get(subscriptionById.get(a.getSubscriptionId()).getIpoId());
              return new MatchCandidate(
                  a.getId(),
                  ipo == null ? null : StockCodes.normalize(ipo.getStockCode()),
                  a.getAllocatedQuantity(),
                  salesByAllocation.getOrDefault(a.getId(), List.of()));
            })
        .toList();
  }

  /**
   * 배정 행을 ID 순서로 잠근다(매도 등록과 같은 잠금). 호출한 트랜잭션이 끝날 때까지 유지된다.
   *
   * @param allocationIds 잠글 배정 ID
   */
  public void lockAllocations(Collection<Long> allocationIds) {
    allocationIds.stream().sorted().forEach(allocationRepository::findByIdForUpdate);
  }

  /**
   * 배정별 활성 매도를 조회한다.
   *
   * @param allocationIds 대상 배정 ID
   * @return 배정 ID별 활성 매도 목록(매도가 없는 배정은 키가 없다)
   */
  public Map<Long, List<ExistingSale>> existingSales(Collection<Long> allocationIds) {
    Map<Long, List<ExistingSale>> salesByAllocation = new HashMap<>();
    if (allocationIds.isEmpty()) {
      return salesByAllocation;
    }
    for (Sale sale :
        saleRepository.findByAllocationIdInAndStatus(
            new ArrayList<>(allocationIds), RecordStatus.ACTIVE)) {
      salesByAllocation
          .computeIfAbsent(sale.getAllocationId(), k -> new ArrayList<>())
          .add(
              new ExistingSale(
                  sale.getId(),
                  sale.getSaleDate().toLocalDate(),
                  sale.getQuantity(),
                  sale.getAmount() == null ? 0 : sale.getAmount(),
                  sale.getFee() == null ? 0 : sale.getFee(),
                  sale.getTax() == null ? 0 : sale.getTax()));
    }
    return salesByAllocation;
  }
}
