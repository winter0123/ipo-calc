package com.vibeaiteam.profitcalculation.service.profit;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.profitcalculation.controller.profit.exception.ProfitForbiddenException;
import com.vibeaiteam.profitcalculation.controller.profit.exception.ProfitNotFoundException;
import com.vibeaiteam.profitcalculation.dto.profit.InvestorProfitDto;
import com.vibeaiteam.profitcalculation.dto.profit.ProfitDto;
import com.vibeaiteam.profitcalculation.service.profit.calculator.ProfitCalculator;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단일 매도건 및 투자자별 수익 계산을 담당하는 읽기 전용 서비스.
 * 트랜잭션 경계는 @Transactional(readOnly = true)로 선언한다.
 */
@Service
@RequiredArgsConstructor
public class ProfitCalculationService {

  private final SaleRepository saleRepository;
  private final AllocationRepository allocationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final CostRepository costRepository;
  private final IpoRepository ipoRepository;
  private final OwnershipGuard ownershipGuard;

  /**
   * 단일 매도건의 순수익을 계산한다.
   * 계산 흐름: Sale → Allocation → Subscription → (IPO 조건부) → Cost → ProfitDto 구성
   */
  @Transactional(readOnly = true)
  public ProfitDto calculateForSale(Long saleId) {
    // 1. Sale 조회 — 없거나 CANCELLED이면 404
    Sale sale =
        saleRepository
            .findByIdAndDeletedAtIsNull(saleId)
            .orElseThrow(() -> new ProfitNotFoundException("해당 매도 건을 찾을 수 없습니다."));
    if (sale.getStatus() != RecordStatus.ACTIVE) {
      throw new ProfitNotFoundException("해당 매도 건을 찾을 수 없습니다.");
    }

    // 2. Allocation 조회
    Allocation allocation =
        allocationRepository
            .findById(sale.getAllocationId())
            .orElseThrow(() -> new ProfitNotFoundException("배정 기록을 찾을 수 없습니다."));

    // 3. Subscription 조회
    Subscription subscription =
        subscriptionRepository
            .findById(allocation.getSubscriptionId())
            .orElseThrow(() -> new ProfitNotFoundException("청약 기록을 찾을 수 없습니다."));
    ownershipGuard.requireOwnedInvestor(
        subscription.getInvestorId(),
        () -> new ProfitNotFoundException("해당 투자자를 찾을 수 없습니다."),
        () -> new ProfitForbiddenException("계정 소유권이 없습니다."));

    // 4. IPO 조건부 조회 — allocationPrice가 null이면 fixedPrice 사용
    Long fixedPrice = null;
    if (allocation.getAllocationPrice() == null) {
      Ipo ipo = ipoRepository.findByIdAndDeletedAtIsNull(subscription.getIpoId()).orElse(null);
      if (ipo != null) {
        fixedPrice = ipo.getFixedPrice();
      }
    }

    // 5. ACTIVE Cost 조회 및 합계 계산
    List<Cost> activeCosts =
        costRepository.findBySubscriptionIdAndStatusAndDeletedAtIsNull(
            subscription.getId(), RecordStatus.ACTIVE);
    Long totalActiveCost = activeCosts.stream().map(Cost::getAmount).reduce(0L, Long::sum);

    // 6~9. 대응취득가액/대응취득비용/세전·세후순수익 계산 및 ProfitDto 구성
    return buildProfitDto(sale, allocation, fixedPrice, totalActiveCost);
  }

  /**
   * 투자자의 모든 ACTIVE Sale에 대한 수익을 계산하고 합계를 반환한다.
   * N+1 방지를 위해 Allocation/Subscription/Cost/IPO를 배치 조회한다.
   */
  @Transactional(readOnly = true)
  public InvestorProfitDto calculateForInvestor(Long investorId) {
    // 1. Investor 조회 — 없으면 404, 타 계정 소유면 403
    Investor investor =
        ownershipGuard.requireOwnedInvestor(
            investorId,
            () -> new ProfitNotFoundException("해당 투자자를 찾을 수 없습니다."),
            () -> new ProfitForbiddenException("계정 소유권이 없습니다."));

    // 2. ACTIVE Sale 목록 조회 (Sale → Allocation → Subscription → Investor 조인)
    List<Sale> sales =
        saleRepository.findActiveWithFilters(investor.getAccountId(), null, investorId, null);
    if (sales.isEmpty()) {
      return InvestorProfitDto.builder()
          .investorId(investorId)
          .sales(List.of())
          .totalSaleAmount(0L)
          .totalPreTaxProfit(0L)
          .totalAfterTaxProfit(0L)
          .build();
    }

    // 3. 배치 조회 (N+1 방지)
    List<Long> allocationIds = sales.stream().map(Sale::getAllocationId).distinct().toList();
    Map<Long, Allocation> allocationsById =
        allocationRepository.findAllById(allocationIds).stream()
            .collect(Collectors.toMap(Allocation::getId, Function.identity()));

    List<Long> subscriptionIds =
        allocationsById.values().stream().map(Allocation::getSubscriptionId).distinct().toList();
    Map<Long, Subscription> subscriptionsById =
        subscriptionRepository.findAllById(subscriptionIds).stream()
            .collect(Collectors.toMap(Subscription::getId, Function.identity()));

    Map<Long, Long> totalActiveCostBySubscriptionId =
        costRepository
            .findBySubscriptionIdInAndStatusAndDeletedAtIsNull(subscriptionIds, RecordStatus.ACTIVE)
            .stream()
            .collect(
                Collectors.groupingBy(
                    Cost::getSubscriptionId, Collectors.summingLong(Cost::getAmount)));

    List<Long> ipoIds =
        allocationsById.values().stream()
            .filter(allocation -> allocation.getAllocationPrice() == null)
            .map(allocation -> subscriptionsById.get(allocation.getSubscriptionId()))
            .filter(Objects::nonNull)
            .map(Subscription::getIpoId)
            .distinct()
            .toList();
    Map<Long, Ipo> ipoById =
        ipoRepository.findAllById(ipoIds).stream()
            .collect(Collectors.toMap(Ipo::getId, Function.identity()));

    // 4. 각 Sale마다 수익 계산 (SF-2 로직 재사용)
    List<ProfitDto> profits =
        sales.stream()
            .map(
                sale -> {
                  Allocation allocation = allocationsById.get(sale.getAllocationId());
                  if (allocation == null) {
                    throw new ProfitNotFoundException("배정 기록을 찾을 수 없습니다.");
                  }
                  Subscription subscription = subscriptionsById.get(allocation.getSubscriptionId());
                  if (subscription == null) {
                    throw new ProfitNotFoundException("청약 기록을 찾을 수 없습니다.");
                  }
                  Long fixedPrice = null;
                  if (allocation.getAllocationPrice() == null) {
                    Ipo ipo = ipoById.get(subscription.getIpoId());
                    if (ipo != null) {
                      fixedPrice = ipo.getFixedPrice();
                    }
                  }
                  Long totalActiveCost =
                      totalActiveCostBySubscriptionId.getOrDefault(subscription.getId(), 0L);
                  return buildProfitDto(sale, allocation, fixedPrice, totalActiveCost);
                })
            // 5. 정렬 — saleDate 오름차순, 동일 saleDate인 경우 id 오름차순
            .sorted(
                Comparator.comparing(ProfitDto::getSaleDate).thenComparing(ProfitDto::getSaleId))
            .toList();

    // 6. 합계 — 단순 합산 (추가 반올림 없음)
    Long totalSaleAmount = profits.stream().map(ProfitDto::getSaleAmount).reduce(0L, Long::sum);
    Long totalPreTaxProfit = profits.stream().map(ProfitDto::getPreTaxProfit).reduce(0L, Long::sum);
    Long totalAfterTaxProfit =
        profits.stream().map(ProfitDto::getAfterTaxProfit).reduce(0L, Long::sum);

    return InvestorProfitDto.builder()
        .investorId(investorId)
        .sales(profits)
        .totalSaleAmount(totalSaleAmount)
        .totalPreTaxProfit(totalPreTaxProfit)
        .totalAfterTaxProfit(totalAfterTaxProfit)
        .build();
  }

  // 대응취득가액/대응취득비용/세전·세후순수익을 계산해 ProfitDto를 구성한다 (저장 없음).
  private ProfitDto buildProfitDto(
      Sale sale, Allocation allocation, Long fixedPrice, Long totalActiveCost) {
    Long acquisitionAmount =
        ProfitCalculator.calculateAcquisitionAmount(
            allocation.getAllocationPrice(), fixedPrice, sale.getQuantity());
    Long matchedCost =
        ProfitCalculator.calculateMatchedCost(
            totalActiveCost, sale.getQuantity(), allocation.getAllocatedQuantity());
    Long preTaxProfit =
        ProfitCalculator.calculatePreTaxProfit(
            sale.getAmount(), acquisitionAmount, matchedCost, sale.getFee(), sale.getOtherCost());
    Long afterTaxProfit = ProfitCalculator.calculateAfterTaxProfit(preTaxProfit, sale.getTax());

    return ProfitDto.builder()
        .saleId(sale.getId())
        .saleDate(sale.getSaleDate())
        .allocationId(allocation.getId())
        .quantity(sale.getQuantity())
        .saleAmount(sale.getAmount())
        .matchedAcquisitionAmount(acquisitionAmount)
        .matchedCostAmount(matchedCost)
        .saleFee(sale.getFee() != null ? sale.getFee() : 0L)
        .saleTax(sale.getTax() != null ? sale.getTax() : 0L)
        .saleOtherCost(sale.getOtherCost() != null ? sale.getOtherCost() : 0L)
        .preTaxProfit(preTaxProfit)
        .afterTaxProfit(afterTaxProfit)
        .build();
  }
}
