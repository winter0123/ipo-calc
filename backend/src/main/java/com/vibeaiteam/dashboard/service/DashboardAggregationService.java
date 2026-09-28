package com.vibeaiteam.dashboard.service;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.dashboard.dto.InvestorAggregationDto;
import com.vibeaiteam.dashboard.dto.PeriodAggregationDto;
import com.vibeaiteam.dashboard.dto.SecuritiesFirmAggregationDto;
import com.vibeaiteam.dashboard.dto.SummaryDto;
import com.vibeaiteam.dashboard.service.ownership.DashboardOwnershipVerifier;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.profitcalculation.service.profit.calculator.ProfitCalculator;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final CostRepository costRepository;
    private final IpoRepository ipoRepository;

    /**
     * 공통 데이터 준비 헬퍼: 현재 계정 소유 ACTIVE Sale 조회 + 배치 조회 + ProfitCalculator 적용
     * 4개 엔드포인트(getSummary, getByInvestor, getBySecuritiesFirm, getByPeriod)가 공유
     */
    private List<CalculatedSale> loadCalculatedSales() {
        Long currentAccountId = ownershipVerifier.getCurrentAccountId();
        List<Sale> activeSales = saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId);

        if (activeSales.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Subscription ID 수집 (Sale -> Allocation -> Subscription 경로)
        Set<Long> subscriptionIds = activeSales.stream()
                .map(Sale::getAllocationId)
                .collect(Collectors.toSet());

        // 2. Allocation 배치 조회 (subscriptionId로 조회 필요하므로 allocationId로 먼저 조회 후 subscriptionId 매핑)
        Map<Long, Allocation> allocationMap = new HashMap<>();
        List<Long> allocationIds = activeSales.stream().map(Sale::getAllocationId).distinct().toList();
        List<Allocation> allocations = allocationRepository.findByIdIn(allocationIds);
        for (Allocation a : allocations) {
            allocationMap.put(a.getId(), a);
        }

        // 3. Subscription ID 매핑 (Allocation -> Subscription)
        Map<Long, Long> allocationToSubscription = new HashMap<>();
        for (Map.Entry<Long, Allocation> entry : allocationMap.entrySet()) {
            allocationToSubscription.put(entry.getKey(), entry.getValue().getSubscriptionId());
        }
        subscriptionIds = new HashSet<>(allocationToSubscription.values());

        // 4. Subscription 배치 조회
        Map<Long, Subscription> subscriptionMap = new HashMap<>();
        List<Subscription> subscriptions = subscriptionRepository.findAllById(subscriptionIds);
        for (Subscription s : subscriptions) {
            subscriptionMap.put(s.getId(), s);
        }

        // 5. Investor ID 수집 및 배치 조회
        Set<Long> investorIds = subscriptions.stream().map(Subscription::getInvestorId).collect(Collectors.toSet());
        Map<Long, Investor> investorMap = new HashMap<>();
        List<Investor> investors = investorRepository.findAllById(investorIds);
        for (Investor i : investors) {
            investorMap.put(i.getId(), i);
        }

        // 6. SecuritiesAccount ID 수집 및 배치 조회
        Set<Long> securitiesAccountIds = subscriptions.stream()
                .map(Subscription::getSecuritiesAccountId)
                .collect(Collectors.toSet());
        Map<Long, SecuritiesAccount> securitiesAccountMap = new HashMap<>();
        List<SecuritiesAccount> securitiesAccounts = securitiesAccountRepository.findAllById(securitiesAccountIds);
        for (SecuritiesAccount sa : securitiesAccounts) {
            securitiesAccountMap.put(sa.getId(), sa);
        }

        // 7. SecuritiesFirm ID 수집 및 배치 조회
        Set<Long> securitiesFirmIds = securitiesAccounts.stream()
                .map(SecuritiesAccount::getSecuritiesFirmId)
                .collect(Collectors.toSet());
        Map<Long, SecuritiesFirm> securitiesFirmMap = new HashMap<>();
        List<SecuritiesFirm> securitiesFirms = securitiesFirmRepository.findAllById(securitiesFirmIds);
        for (SecuritiesFirm sf : securitiesFirms) {
            securitiesFirmMap.put(sf.getId(), sf);
        }

        // 8. IPO ID 수집 및 배치 조회 (allocationPrice가 null일 때 fixedPrice 대체용)
        Set<Long> ipoIds = subscriptions.stream().map(Subscription::getIpoId).collect(Collectors.toSet());
        Map<Long, Ipo> ipoMap = new HashMap<>();
        List<Ipo> ipos = ipoRepository.findAllById(ipoIds);
        for (Ipo ipo : ipos) {
            ipoMap.put(ipo.getId(), ipo);
        }

        // 9. Cost 배치 조회 (ACTIVE만)
        Map<Long, List<Cost>> costsBySubscription = new HashMap<>();
        List<Cost> costs = costRepository.findBySubscriptionIdInAndStatusAndDeletedAtIsNull(
                new ArrayList<>(subscriptionIds), "ACTIVE");
        for (Cost c : costs) {
            costsBySubscription.computeIfAbsent(c.getSubscriptionId(), k -> new ArrayList<>()).add(c);
        }

        // 10. 각 Sale에 대해 ProfitCalculator로 세전/세후순수익 계산
        List<CalculatedSale> calculatedSales = new ArrayList<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("yyyy");

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

            SecuritiesAccount securitiesAccount = securitiesAccountMap.get(subscription.getSecuritiesAccountId());
            Long securitiesFirmId = (securitiesAccount != null) ? securitiesAccount.getSecuritiesFirmId() : null;

            // 총 ACTIVE 비용 합계 계산
            Long totalActiveCost = costsBySubscription.getOrDefault(subscription.getId(), Collections.emptyList()).stream()
                    .mapToLong(Cost::getAmount)
                    .sum();

            // ProfitCalculator로 세전/세후순수익 계산
            Long acquisitionAmount = ProfitCalculator.calculateAcquisitionAmount(
                    allocation.getAllocationPrice(),
                    ipoMap.get(subscription.getIpoId()) != null ? ipoMap.get(subscription.getIpoId()).getFixedPrice() : null,
                    sale.getQuantity()
            );

            Long matchedCost = ProfitCalculator.calculateMatchedCost(
                    totalActiveCost,
                    sale.getQuantity(),
                    allocation.getAllocatedQuantity()
            );

            Long preTaxProfit = ProfitCalculator.calculatePreTaxProfit(
                    sale.getAmount(),
                    acquisitionAmount,
                    matchedCost,
                    sale.getFee(),
                    sale.getOtherCost()
            );

            Long afterTaxProfit = ProfitCalculator.calculateAfterTaxProfit(
                    preTaxProfit,
                    sale.getTax()
            );

            // saleDateKey 생성 (기간별 그룹핑용)
            String saleDateKey = sale.getSaleDate().format(monthFormatter); // 기본값은 월별, 실제 사용 시 year/month 구분

            calculatedSales.add(new CalculatedSale(
                    sale,
                    preTaxProfit,
                    afterTaxProfit,
                    investor.getId(),
                    securitiesFirmId,
                    saleDateKey,
                    investor.getDisplayName(),
                    securitiesFirmId != null ? securitiesFirmMap.get(securitiesFirmId).getName() : null,
                    allocation.getAllocatedQuantity(),
                    allocation.getAllocationPrice()
            ));
        }

        return calculatedSales;
    }

    /**
     * 전체 집계 (SF-2에서 구현 예정)
     * SF-1에서는 loadCalculatedSales()가 정상 동작하는지만 검증하고 스텁 결과 반환
     */
    public SummaryDto getSummary() {
        // loadCalculatedSales() 호출로 공통 데이터 준비 로직 검증
        loadCalculatedSales();
        
        // SF-1에서는 스텁 반환 (실제 집계는 SF-2에서 구현)
        return SummaryDto.builder()
                .totalInvestmentAmount(0L)
                .totalPreTaxProfit(0L)
                .totalAfterTaxProfit(0L)
                .totalProfitRate(0.0)
                .participationCount(0)
                .build();
    }

    /**
     * 투자자별 집계 (SF-3에서 구현 예정)
     */
    public List<InvestorAggregationDto> getByInvestor() {
        // TODO: loadCalculatedSales() 결과를 investorId로 그룹핑 후 집계
        return Collections.emptyList();
    }

    /**
     * 증권사별 집계 (SF-3에서 구현 예정)
     */
    public List<SecuritiesFirmAggregationDto> getBySecuritiesFirm() {
        // TODO: loadCalculatedSales() 결과를 securitiesFirmId로 그룹핑 후 집계
        return Collections.emptyList();
    }

    /**
     * 기간별 집계 (SF-4에서 구현 예정)
     */
    public List<PeriodAggregationDto> getByPeriod(String unit) {
        // TODO: unit 검증 후 loadCalculatedSales() 결과를 saleDate 기준 그룹핑
        // unit=month: "YYYY-MM", unit=year: "YYYY"
        return Collections.emptyList();
    }

    /**
     * 계산용 내부 레코드: Sale + 세전/세후순수익 + 그룹핑 키 + 추가 정보
     */
    public record CalculatedSale(
            Sale sale,
            Long preTaxProfit,
            Long afterTaxProfit,
            Long investorId,
            Long securitiesFirmId,
            String saleDateKey,        // "YYYY-MM" (월별 기본, 실제 사용 시 재포맷)
            String investorName,
            String securitiesFirmName,
            Long allocatedQuantity,
            Long allocationPrice
    ) {}
}