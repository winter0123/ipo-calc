package com.ipocalc.profitcalculation.service.profit;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.service.ownership.OwnershipGuard;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.profitcalculation.controller.profit.exception.ProfitForbiddenException;
import com.ipocalc.profitcalculation.controller.profit.exception.ProfitNotFoundException;
import com.ipocalc.profitcalculation.controller.profit.exception.ProfitValidationException;
import com.ipocalc.profitcalculation.dto.profit.InvestorProfitDto;
import com.ipocalc.profitcalculation.dto.profit.ProfitDto;
import com.ipocalc.profitcalculation.service.profit.calculator.ProfitCalculator;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 단일 매도건 및 투자자별 수익 계산을 담당하는 읽기 전용 서비스. 트랜잭션 경계는 @Transactional(readOnly = true)로 선언한다. */
@Service
@RequiredArgsConstructor
public class ProfitCalculationService {

  private final SaleRepository saleRepository;
  private final AllocationRepository allocationRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final IpoRepository ipoRepository;
  private final OwnershipGuard ownershipGuard;

  /** 단일 매도건의 순수익을 계산한다. 계산 흐름: Sale → Allocation → Subscription → (IPO 조건부) → ProfitDto 구성 */
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
    Ipo ipo = ipoRepository.findByIdAndDeletedAtIsNull(subscription.getIpoId()).orElse(null);

    // 5. 대응취득가액/세전·세후순수익 계산 및 ProfitDto 구성
    return buildProfitDto(sale, allocation, ipo);
  }

  /**
   * 투자자의 모든 ACTIVE Sale에 대한 수익을 계산하고 합계를 반환한다. N+1 방지를 위해 Allocation/Subscription/IPO를 배치 조회한다.
   *
   * @param investorId 투자자 ID
   */
  @Transactional(readOnly = true)
  public InvestorProfitDto calculateForInvestor(Long investorId) {
    return calculateForInvestor(investorId, null, null);
  }

  /**
   * 투자자의 ACTIVE Sale 중 공모주 상장일(없으면 매도일)이 from~to 범위에 포함되는 것만 수익을 계산하고 합계를 반환한다. 양 끝 날짜는 포함하며 어느 한쪽이
   * 없으면 해당 방향의 제한을 두지 않는다. 거른 결과가 비면 빈 목록과 합계 0을 반환한다.
   *
   * @param investorId 투자자 ID
   * @param from 조회 시작일(포함, null이면 무제한)
   * @param to 조회 종료일(포함, null이면 무제한)
   */
  @Transactional(readOnly = true)
  public InvestorProfitDto calculateForInvestor(Long investorId, LocalDate from, LocalDate to) {
    // 0. 기간 검증 — from이 to보다 늦으면 400
    if (from != null && to != null && from.isAfter(to)) {
      throw new ProfitValidationException("조회 시작일이 종료일보다 늦을 수 없습니다.");
    }

    // 1. Investor 조회 — 없으면 404, 타 계정 소유면 403
    Investor investor =
        ownershipGuard.requireOwnedInvestor(
            investorId,
            () -> new ProfitNotFoundException("해당 투자자를 찾을 수 없습니다."),
            () -> new ProfitForbiddenException("계정 소유권이 없습니다."));

    // 2. ACTIVE Sale 목록 조회 (Sale → Allocation → Subscription → Investor 조인)
    List<Sale> sales =
        saleRepository.findActiveWithFilters(investor.getAccountId(), null, investorId, null);

    // 3. 배치 조회 (N+1 방지) — 기간 필터에 상장일이 필요해 거르기 전에 읽는다
    List<Long> allocationIds = sales.stream().map(Sale::getAllocationId).distinct().toList();
    Map<Long, Allocation> allocationsById =
        allocationRepository.findAllById(allocationIds).stream()
            .collect(Collectors.toMap(Allocation::getId, Function.identity()));

    List<Long> subscriptionIds =
        allocationsById.values().stream().map(Allocation::getSubscriptionId).distinct().toList();
    Map<Long, Subscription> subscriptionsById =
        subscriptionRepository.findAllById(subscriptionIds).stream()
            .collect(Collectors.toMap(Subscription::getId, Function.identity()));

    List<Long> ipoIds =
        subscriptionsById.values().stream().map(Subscription::getIpoId).distinct().toList();
    Map<Long, Ipo> ipoById =
        ipoRepository.findAllById(ipoIds).stream()
            .collect(Collectors.toMap(Ipo::getId, Function.identity()));

    // 4. 기간 필터 — 사용자 결정(2026-10-13): 공모주 상장일 기준(10월 상장을 11월에 팔아도 10월). 상장일이 없으면 매도일.
    List<Sale> filteredSales =
        sales.stream()
            .filter(
                sale ->
                    isInPeriod(
                        basisDay(sale, allocationsById, subscriptionsById, ipoById), from, to))
            .toList();
    if (filteredSales.isEmpty()) {
      return InvestorProfitDto.builder()
          .investorId(investorId)
          .sales(List.of())
          .totalSaleAmount(0L)
          .totalPreTaxProfit(0L)
          .totalAfterTaxProfit(0L)
          .totalRealizedProfit(0L)
          .build();
    }

    // 5. 각 Sale마다 수익 계산 (SF-2 로직 재사용)
    List<ProfitDto> profits =
        filteredSales.stream()
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
                  return buildProfitDto(sale, allocation, ipoById.get(subscription.getIpoId()));
                })
            // 6. 정렬 — saleDate 오름차순, 동일 saleDate인 경우 id 오름차순
            .sorted(
                Comparator.comparing(ProfitDto::getSaleDate).thenComparing(ProfitDto::getSaleId))
            .toList();

    // 7. 합계 — 단순 합산 (추가 반올림 없음)
    // 매도 금액은 선택 입력(nullable)이라 null은 0으로 합산한다 (ProfitCalculator와 동일 규칙).
    Long totalSaleAmount =
        profits.stream()
            .map(ProfitDto::getSaleAmount)
            .filter(Objects::nonNull)
            .reduce(0L, Long::sum);
    Long totalPreTaxProfit = profits.stream().map(ProfitDto::getPreTaxProfit).reduce(0L, Long::sum);
    Long totalAfterTaxProfit =
        profits.stream().map(ProfitDto::getAfterTaxProfit).reduce(0L, Long::sum);
    Long totalRealizedProfit =
        profits.stream().map(ProfitDto::getRealizedProfit).reduce(0L, Long::sum);

    return InvestorProfitDto.builder()
        .investorId(investorId)
        .sales(profits)
        .totalSaleAmount(totalSaleAmount)
        .totalPreTaxProfit(totalPreTaxProfit)
        .totalAfterTaxProfit(totalAfterTaxProfit)
        .totalRealizedProfit(totalRealizedProfit)
        .build();
  }

  // 기간 판단 기준일: 그 매도의 공모주 상장일, 상장일을 모르면 매도일.
  private static LocalDate basisDay(
      Sale sale,
      Map<Long, Allocation> allocationsById,
      Map<Long, Subscription> subscriptionsById,
      Map<Long, Ipo> ipoById) {
    Allocation allocation = allocationsById.get(sale.getAllocationId());
    Subscription subscription =
        allocation == null ? null : subscriptionsById.get(allocation.getSubscriptionId());
    Ipo ipo = subscription == null ? null : ipoById.get(subscription.getIpoId());
    return ipo != null && ipo.getListingDate() != null
        ? ipo.getListingDate()
        : sale.getSaleDate().toLocalDate();
  }

  // 기준일이 from·to 사이(양 끝 포함)면 true다. null인 경계는 무제한으로 본다.
  private static boolean isInPeriod(LocalDate day, LocalDate from, LocalDate to) {
    boolean fromOk = from == null || !day.isBefore(from);
    boolean toOk = to == null || !day.isAfter(to);
    return fromOk && toOk;
  }

  // 대응취득가액/세전·세후순수익/실현손익을 계산해 ProfitDto를 구성한다 (저장 없음).
  private ProfitDto buildProfitDto(Sale sale, Allocation allocation, Ipo ipo) {
    // allocationPrice가 없을 때만 공모가(fixedPrice)로 대체한다.
    Long fixedPrice =
        allocation.getAllocationPrice() == null && ipo != null ? ipo.getFixedPrice() : null;
    Long acquisitionAmount =
        ProfitCalculator.calculateAcquisitionAmount(
            allocation.getAllocationPrice(), fixedPrice, sale.getQuantity());
    Long preTaxProfit =
        ProfitCalculator.calculatePreTaxProfit(
            sale.getAmount(), acquisitionAmount, sale.getFee(), sale.getOtherCost());
    Long afterTaxProfit = ProfitCalculator.calculateAfterTaxProfit(preTaxProfit, sale.getTax());
    Long realizedProfit =
        ProfitCalculator.calculateRealizedProfit(
            sale.getAmount(), acquisitionAmount, sale.getTax(), sale.getOtherCost());

    return ProfitDto.builder()
        .saleId(sale.getId())
        .saleDate(sale.getSaleDate())
        .allocationId(allocation.getId())
        .quantity(sale.getQuantity())
        .saleAmount(sale.getAmount())
        .matchedAcquisitionAmount(acquisitionAmount)
        .saleFee(sale.getFee() != null ? sale.getFee() : 0L)
        .saleTax(sale.getTax() != null ? sale.getTax() : 0L)
        .saleOtherCost(sale.getOtherCost() != null ? sale.getOtherCost() : 0L)
        .preTaxProfit(preTaxProfit)
        .afterTaxProfit(afterTaxProfit)
        .realizedProfit(realizedProfit)
        .listingDate(ipo == null ? null : ipo.getListingDate())
        .ipoName(ipo == null ? null : ipo.getIpoName())
        .build();
  }
}
