package com.vibeaiteam.profitcalculation.service.profit;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.profitcalculation.controller.profit.exception.ProfitNotFoundException;
import com.vibeaiteam.profitcalculation.dto.profit.ProfitDto;
import com.vibeaiteam.profitcalculation.service.profit.calculator.ProfitCalculator;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 단일 매도건 및 투자자별 수익 계산을 담당하는 읽기 전용 서비스.
// 트랜잭션 경계는 @Transactional(readOnly = true)로 선언한다.
@Service
@RequiredArgsConstructor
public class ProfitCalculationService {

    private final SaleRepository saleRepository;
    private final AllocationRepository allocationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CostRepository costRepository;
    private final IpoRepository ipoRepository;

    // 단일 매도건의 순수익을 계산한다.
    // 계산 흐름: Sale → Allocation → Subscription → (IPO 조건부) → Cost → ProfitDto 구성
    @Transactional(readOnly = true)
    public ProfitDto calculateForSale(Long saleId) {
        // 1. Sale 조회 — 없거나 CANCELLED이면 404
        Sale sale = saleRepository.findByIdAndDeletedAtIsNull(saleId)
                .orElseThrow(() -> new ProfitNotFoundException("해당 매도 건을 찾을 수 없습니다."));
        if (!"ACTIVE".equals(sale.getStatus())) {
            throw new ProfitNotFoundException("해당 매도 건을 찾을 수 없습니다.");
        }

        // 2. Allocation 조회
        Allocation allocation = allocationRepository.findById(sale.getAllocationId())
                .orElseThrow(() -> new ProfitNotFoundException("배정 기록을 찾을 수 없습니다."));

        // 3. Subscription 조회
        Subscription subscription = subscriptionRepository.findById(allocation.getSubscriptionId())
                .orElseThrow(() -> new ProfitNotFoundException("청약 기록을 찾을 수 없습니다."));

        // 4. IPO 조건부 조회 — allocationPrice가 null이면 fixedPrice 사용
        Long allocationPrice = allocation.getAllocationPrice();
        Long fixedPrice = null;
        if (allocationPrice == null) {
            Ipo ipo = ipoRepository.findByIdAndDeletedAtIsNull(subscription.getIpoId())
                    .orElse(null);
            if (ipo != null) {
                fixedPrice = ipo.getFixedPrice();
            }
        }

        // 5. 대응취득가액 계산
        Long acquisitionAmount = ProfitCalculator.calculateAcquisitionAmount(
                allocationPrice, fixedPrice, sale.getQuantity());

        // 6. ACTIVE Cost 조회 및 합계 계산
        List<Cost> activeCosts = costRepository.findBySubscriptionIdAndStatusAndDeletedAtIsNull(
                subscription.getId(), "ACTIVE");
        Long totalActiveCost = activeCosts.stream()
                .map(Cost::getAmount)
                .reduce(0L, Long::sum);

        // 7. 대응취득비용 계산 (안분)
        Long matchedCost = ProfitCalculator.calculateMatchedCost(
                totalActiveCost, sale.getQuantity(), allocation.getAllocatedQuantity());

        // 8. 세전순수익 계산
        Long preTaxProfit = ProfitCalculator.calculatePreTaxProfit(
                sale.getAmount(), acquisitionAmount, matchedCost, sale.getFee(), sale.getOtherCost());

        // 9. 세후순수익 계산
        Long afterTaxProfit = ProfitCalculator.calculateAfterTaxProfit(preTaxProfit, sale.getTax());

        // 10. ProfitDto 구성 및 반환 (저장 없음)
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