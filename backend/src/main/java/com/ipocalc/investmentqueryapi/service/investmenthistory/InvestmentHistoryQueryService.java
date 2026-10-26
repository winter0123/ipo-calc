package com.ipocalc.investmentqueryapi.service.investmenthistory;

import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.ipocalc.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** 투자 이력 조회 서비스. */
@Service
@RequiredArgsConstructor
public class InvestmentHistoryQueryService {

  private final SecurityUtils securityUtils;
  private final InvestmentHistoryOwnershipVerifier ownershipVerifier;
  private final InvestmentHistoryReadModelLoader readModelLoader;
  private final SubscriptionRepository subscriptionRepository;
  private final InvestmentHistoryAssembler assembler;

  /** 청약 ID로 투자 이력 상세를 조회한다. 청약이 없으면 404를 반환한다. */
  public InvestmentHistoryDto getHistoryDetail(Long subscriptionId) {
    // a. Subscription 조회 (없으면 404)
    Subscription subscription =
        subscriptionRepository
            .findByIdAndDeletedAtIsNull(subscriptionId)
            .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 청약을 찾을 수 없습니다."));

    // b. 소유권 검증 (타 계정 소유면 403)
    ownershipVerifier.verifySubscriptionOwnership(subscription.getInvestorId());

    // c. 참조 데이터 조회와 DTO 조립 입력 구성을 전담 컴포넌트에 위임한다.
    InvestmentHistoryReadModelLoader.DetailReadModel readModel =
        readModelLoader.loadDetail(subscription);

    // d. Assembler로 DTO 조합
    return assembler.toDto(
        subscription,
        readModel.allocation(),
        readModel.activeSales(),
        readModel.ipoMap(),
        readModel.investorName(),
        readModel.securitiesFirmName(),
        readModel.securitiesAccountId(),
        readModel.securitiesAccountName());
  }

  /** 투자자·증권계좌·공모주 조건으로 투자 이력을 페이지 조회한다. */
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

    // Step 3: N+1 방지를 위해 페이지 범위의 참조 데이터를 배치 조회한다.
    InvestmentHistoryReadModelLoader.ReadModel readModel = readModelLoader.load(subscriptionList);

    // Step 4: DTO 조합
    List<InvestmentHistoryDto> dtoList =
        assembler.toDtos(
            subscriptionList,
            readModel.allocationMap(),
            readModel.saleMap(),
            readModel.ipoMap(),
            readModel.investorNameMap(),
            readModel.securitiesFirmNameMap(),
            readModel.securitiesAccountNameMap());

    // Step 5: 페이지 메타데이터 및 반환
    return new PageImpl<>(
        dtoList, subscriptionPage.getPageable(), subscriptionPage.getTotalElements());
  }
}
