package com.vibeaiteam.investmentqueryapi.service.investmenthistory;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 투자 이력 조회 서비스.
 */
@Service
@RequiredArgsConstructor
public class InvestmentHistoryQueryService {

  private final SecurityUtils securityUtils;
  private final InvestmentHistoryOwnershipVerifier ownershipVerifier;
  private final AllocationRepository allocationRepository;
  private final IpoRepository ipoRepository;
  private final SaleRepository saleRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final InvestorRepository investorRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final InvestmentHistoryAssembler assembler;

  @PersistenceContext private EntityManager entityManager;

  /**
   * 청약 ID로 투자 이력 상세를 조회한다. 청약이 없으면 404를 반환한다.
   */
  public InvestmentHistoryDto getHistoryDetail(Long subscriptionId) {
    // a. Subscription 조회 (없으면 404)
    Subscription subscription =
        subscriptionRepository
            .findByIdAndDeletedAtIsNull(subscriptionId)
            .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 청약을 찾을 수 없습니다."));

    // b. 소유권 검증 (타 계정 소유면 403)
    ownershipVerifier.verifySubscriptionOwnership(subscription.getInvestorId());

    // c. Allocation 조회 (없으면 null)
    Allocation allocation =
        allocationRepository
            .findBySubscriptionIdAndStatusAndDeletedAtIsNull(subscriptionId, RecordStatus.ACTIVE)
            .orElse(null);

    // d. ACTIVE Sale 조회 (Allocation이 있으면, 없으면 빈 리스트)
    List<Sale> activeSales = List.of();
    if (allocation != null) {
      activeSales =
          saleRepository
              .findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(
                  allocation.getId(), RecordStatus.ACTIVE);
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

    // f. Investor 조회 (투자자명용)
    String investorName = null;
    Investor investor = investorRepository.findById(subscription.getInvestorId()).orElse(null);
    if (investor != null) {
      investorName = investor.getDisplayName();
    }

    // g. SecuritiesAccount 조회 (증권사명용)
    String securitiesFirmName = null;
    SecuritiesAccount securitiesAccount =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(subscription.getSecuritiesAccountId())
            .orElse(null);
    if (securitiesAccount != null) {
      SecuritiesFirm securitiesFirm =
          securitiesFirmRepository
              .findByIdAndDeletedAtIsNull(securitiesAccount.getSecuritiesFirmId())
              .orElse(null);
      if (securitiesFirm != null) {
        securitiesFirmName = securitiesFirm.getName();
      }
    }

    // h. Assembler로 DTO 조합
    return assembler.toDto(
        subscription, allocation, activeSales, ipoMap, investorName, securitiesFirmName);
  }

  /**
   * 투자자·증권계좌·공모주 조건으로 투자 이력을 페이지 조회한다.
   */
  public Page<InvestmentHistoryDto> getHistory(
      Long investorId, Long securitiesAccountId, Long ipoId, int page, int size) {

    final Long currentAccountId = securityUtils.getCurrentAccountId();

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
    Page<Subscription> subscriptionPage =
        subscriptionRepository.findByInvestorAccountIdWithFilters(
            currentAccountId,
            investorId,
            securitiesAccountId,
            ipoId,
            Pageable.ofSize(size).withPage(page));

    List<Subscription> subscriptionList = subscriptionPage.getContent();

    // Step 3: N+1 방지를 위한 배치 조회
    List<Long> subscriptionIds =
        subscriptionList.stream().map(Subscription::getId).collect(Collectors.toList());

    // Allocation 배치 조회: subscriptionId -> Allocation 매핑
    Map<Long, Allocation> allocationMap;
    if (!subscriptionIds.isEmpty()) {
      allocationMap =
          allocationRepository.findBySubscriptionIdIn(subscriptionIds).stream()
              .collect(
                  Collectors.toMap(
                      Allocation::getSubscriptionId, java.util.function.Function.identity()));
    } else {
      allocationMap = Map.of();
    }

    // Sale 배치 조회: allocation.getId() 목록으로 조회 (allocationMap의 키는 subscriptionId이므로 혼동 금지)
    List<Long> allocationIds =
        allocationMap.values().stream().map(Allocation::getId).collect(Collectors.toList());
    Map<Long, List<Sale>> saleMapByAllocationId;
    if (!allocationIds.isEmpty()) {
      saleMapByAllocationId =
          saleRepository.findByAllocationIdInAndStatus(allocationIds, RecordStatus.ACTIVE).stream()
              .collect(Collectors.groupingBy(Sale::getAllocationId));
    } else {
      saleMapByAllocationId = Map.of();
    }

    // Assembler는 subscriptionId 기준으로 sales를 조회하므로, allocationId 기준 결과를 subscriptionId 기준으로 재매핑한다.
    final Map<Long, List<Sale>> saleMap =
        allocationMap.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> saleMapByAllocationId.getOrDefault(e.getValue().getId(), List.of())));

    // Ipo 매핑 준비: ipoId -> Ipo 매핑 (DTO에 ipoName, ipoFixedPrice 채우기)
    Map<Long, Ipo> ipoMap;
    if (!subscriptionIds.isEmpty()) {
      // 조회된 Subscription들에 연결된 IPO만 조회
      List<Long> distinctIpoIds =
          subscriptionList.stream().map(s -> s.getIpoId()).distinct().collect(Collectors.toList());
      ipoMap =
          ipoRepository.findAllById(distinctIpoIds).stream()
              .collect(Collectors.toMap(Ipo::getId, java.util.function.Function.identity()));
    } else {
      ipoMap = Map.of();
    }

    // Step 3b: Investor 배치 조회 (investorName용)
    Map<Long, String> investorNameMap = Map.of();
    if (!subscriptionIds.isEmpty()) {
      Set<Long> investorIds =
          subscriptionList.stream().map(Subscription::getInvestorId).collect(Collectors.toSet());
      Map<Long, Investor> investorMap =
          investorRepository.findAllById(investorIds).stream()
              .collect(Collectors.toMap(Investor::getId, java.util.function.Function.identity()));
      investorNameMap =
          investorMap.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDisplayName()));
    }

    // Step 3c: SecuritiesAccount 배치 조회 -> SecuritiesFirm 배치 조회 (securitiesFirmName용)
    Map<Long, String> securitiesFirmNameMap = Map.of();
    if (!subscriptionIds.isEmpty()) {
      Set<Long> securitiesAccountIds =
          subscriptionList.stream()
              .map(Subscription::getSecuritiesAccountId)
              .collect(Collectors.toSet());
      Map<Long, SecuritiesAccount> securitiesAccountMap =
          securitiesAccountRepository.findAllById(securitiesAccountIds).stream()
              .collect(
                  Collectors.toMap(
                      SecuritiesAccount::getId, java.util.function.Function.identity()));

      Set<Long> securitiesFirmIds =
          securitiesAccountMap.values().stream()
              .map(SecuritiesAccount::getSecuritiesFirmId)
              .collect(Collectors.toSet());
      Map<Long, SecuritiesFirm> securitiesFirmMap =
          securitiesFirmRepository.findAllById(securitiesFirmIds).stream()
              .collect(
                  Collectors.toMap(SecuritiesFirm::getId, java.util.function.Function.identity()));

      securitiesFirmNameMap = new LinkedHashMap<>();
      for (Map.Entry<Long, SecuritiesAccount> entry : securitiesAccountMap.entrySet()) {
        SecuritiesFirm sf = securitiesFirmMap.get(entry.getValue().getSecuritiesFirmId());
        securitiesFirmNameMap.put(entry.getKey(), sf != null ? sf.getName() : null);
      }
    }

    // Step 4: DTO 조합
    List<InvestmentHistoryDto> dtoList =
        assembler.toDtos(
            subscriptionList,
            allocationMap,
            saleMap,
            ipoMap,
            investorNameMap,
            securitiesFirmNameMap);

    // Step 5: 페이지 메타데이터 및 반환
    return new PageImpl<>(
        dtoList, subscriptionPage.getPageable(), subscriptionPage.getTotalElements());
  }
}
