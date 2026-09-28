package com.vibeaiteam.investmentqueryapi.service.investmenthistory;

import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryForbiddenException;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvestmentHistoryQueryService {

    private final SecurityUtils securityUtils;
    private final InvestmentHistoryOwnershipVerifier ownershipVerifier;
    private final AllocationRepository allocationRepository;
    private final IpoRepository ipoRepository;
    private final SaleRepository saleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvestmentHistoryAssembler assembler;

    @PersistenceContext
    private EntityManager entityManager;

    public InvestmentHistoryDto getHistoryDetail(Long subscriptionId) {
        // a. Subscription 조회 (없으면 404)
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(subscriptionId)
                .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 청약을 찾을 수 없습니다."));

        // b. 소유권 검증 (타 계정 소유면 403)
        ownershipVerifier.verifySubscriptionOwnership(subscription.getInvestorId());

        // c. Allocation 조회 (없으면 null)
        Allocation allocation = allocationRepository.findBySubscriptionIdAndStatusAndDeletedAtIsNull(subscriptionId, "ACTIVE").orElse(null);

        // d. ACTIVE Sale 조회 (Allocation이 있으면, 없으면 빈 리스트)
        List<Sale> activeSales = List.of();
        if (allocation != null) {
            activeSales = saleRepository.findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(allocation.getId(), "ACTIVE");
        }

        // e. IpoMap 구성 (단건이므로 Map.of)
        Map<Long, Ipo> ipoMap;
        Long ipoId = subscription.getIpoId();
        if (ipoId != null) {
            Ipo ipo = ipoRepository.findById(ipoId).orElse(null);
            if (ipo != null) {
                ipoMap = Map.of(ipoId, ipo);
            } else {
                ipoMap = Map.of();
            }
        } else {
            ipoMap = Map.of();
        }

        // f. Assembler로 DTO 조합
        return assembler.toDto(subscription, allocation, activeSales, ipoMap);
    }

    public Page<InvestmentHistoryDto> getHistory(
            Long investorId,
            Long securitiesAccountId,
            Long ipoId,
            int page,
            int size) {

        Long currentAccountId = securityUtils.getCurrentAccountId();

        // Step 1: 인증 및 소유권 검증 (모든 필터에 대해 재검증)
        if (investorId != null) {
            ownershipVerifier.verifyInvestorIdOwnership(investorId);
        }
        if (securitiesAccountId != null) {
            ownershipVerifier.verifySecuritiesAccountIdOwnership(securitiesAccountId);
        }
        if (ipoId != null) {
            ownershipVerifier.verifyIpoIdOwnership(ipoId);
        }

        // Step 2: 필터와 페이지네이션을 적용한 Subscription 조회
        // 반드시 현재 계정 소유 Investor로 스코프를 강제 (AC-005 리스크 방지)
        Page<Subscription> subscriptionPage = subscriptionRepository.findByInvestorAccountIdWithFilters(
                currentAccountId, investorId, securitiesAccountId, ipoId, Pageable.ofSize(size).withPage(page));

        List<Subscription> subscriptionList = subscriptionPage.getContent();

        // Step 3: N+1 방지를 위한 배치 조회
        List<Long> subscriptionIds = subscriptionList.stream()
                .map(Subscription::getId)
                .collect(Collectors.toList());

        // Allocation 배치 조회: subscriptionId -> Allocation 매핑
        Map<Long, Allocation> allocationMap;
        if (!subscriptionIds.isEmpty()) {
            allocationMap = allocationRepository.findBySubscriptionIdIn(subscriptionIds).stream()
                    .collect(Collectors.toMap(Allocation::getSubscriptionId, java.util.function.Function.identity()));
        } else {
            allocationMap = Map.of();
        }

        // Sale 배치 조회: allocation.getId() 목록으로 조회 (allocationMap의 키는 subscriptionId이므로 혼동 금지)
        List<Long> allocationIds = allocationMap.values().stream()
                .map(Allocation::getId)
                .collect(Collectors.toList());
        Map<Long, List<Sale>> saleMapByAllocationId;
        if (!allocationIds.isEmpty()) {
            saleMapByAllocationId = saleRepository.findByAllocationIdInAndStatus(allocationIds, "ACTIVE").stream()
                    .collect(Collectors.groupingBy(Sale::getAllocationId));
        } else {
            saleMapByAllocationId = Map.of();
        }

        // Assembler는 subscriptionId 기준으로 sales를 조회하므로, allocationId 기준 결과를 subscriptionId 기준으로 재매핑한다.
        Map<Long, List<Sale>> saleMap = allocationMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> saleMapByAllocationId.getOrDefault(e.getValue().getId(), List.of())));

        // Ipo 매핑 준비: ipoId -> Ipo 매핑 (DTO에 ipoName, ipoFixedPrice 채우기)
        Map<Long, Ipo> ipoMap;
        if (!subscriptionIds.isEmpty()) {
            // 조회된 Subscription들에 연결된 IPO만 조회
            List<Long> distinctIpoIds = subscriptionList.stream()
                    .map(s -> s.getIpoId())
                    .distinct()
                    .collect(Collectors.toList());
            ipoMap = ipoRepository.findAllById(distinctIpoIds).stream()
                    .collect(Collectors.toMap(Ipo::getId, java.util.function.Function.identity()));
        } else {
            ipoMap = Map.of();
        }

        // Step 4: DTO 조합
        List<InvestmentHistoryDto> dtoList = assembler.toDtos(subscriptionList, allocationMap, saleMap, ipoMap);

        // Step 5: 페이지 메타데이터 및 반환
        return new PageImpl<>(dtoList, subscriptionPage.getPageable(), subscriptionPage.getTotalElements());
    }
}