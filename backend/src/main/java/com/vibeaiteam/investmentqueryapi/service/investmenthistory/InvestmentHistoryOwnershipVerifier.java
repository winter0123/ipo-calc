package com.vibeaiteam.investmentqueryapi.service.investmenthistory;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryForbiddenException;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투자 이력 조회 대상의 소유권을 검증한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentHistoryOwnershipVerifier {

  private final SecurityUtils securityUtils;
  private final InvestorRepository investorRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final SubscriptionRepository subscriptionRepository;

  /**
   * investorId 필터에 대한 소유권 재검증 investorRepository.findById(investorId)로 조회 → Investor.accountId !=
   * currentAccountId면 403.
   */
  public void verifyInvestorIdOwnership(Long investorId) {
    Long currentAccountId = securityUtils.getCurrentAccountId();
    Investor investor =
        investorRepository
            .findById(investorId)
            .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 투자자를 찾을 수 없습니다."));
    if (!investor.getAccountId().equals(currentAccountId)) {
      throw new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다.");
    }
  }

  /**
   * securitiesAccountId 필터에 대한 소유권 재검증 SecuritiesAccount 엔티티는 accountId 컬럼이 없고 investorId만 있으므로 2홉:
   * SecuritiesAccount → Investor(accountId) 비교
   * investorRepository.findById(securitiesAccount.getInvestorId()).accountId와 currentAccountId 비교.
   */
  public void verifySecuritiesAccountIdOwnership(Long securitiesAccountId) {
    Long currentAccountId = securityUtils.getCurrentAccountId();
    SecuritiesAccount securitiesAccount =
        securitiesAccountRepository
            .findById(securitiesAccountId)
            .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 증권 계좌를 찾을 수 없습니다."));
    Investor investor =
        investorRepository
            .findById(securitiesAccount.getInvestorId())
            .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 투자자를 찾을 수 없습니다."));
    if (!investor.getAccountId().equals(currentAccountId)) {
      throw new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다.");
    }
  }

  /**
   * ipoId 필터에 대한 소유권 재검증 existsByIpoIdAndInvestorAccountId(ipoId, currentAccountId)로 "현재 계정 소유 청약 중
   * 해당 IPO 건이 하나라도 있는지" 확인, 없으면 403.
   */
  public void verifyIpoIdOwnership(Long ipoId) {
    Long currentAccountId = securityUtils.getCurrentAccountId();
    boolean exists =
        subscriptionRepository.existsByIpoIdAndInvestorAccountId(ipoId, currentAccountId);
    if (!exists) {
      throw new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다.");
    }
  }

  /** 단건 조회 시 Subscription의 소유권 검증 Subscription의 Investor의 accountId와 비교. */
  public void verifySubscriptionOwnership(Long subscriptionInvestorId) {
    Long currentAccountId = securityUtils.getCurrentAccountId();
    Investor investor =
        investorRepository
            .findById(subscriptionInvestorId)
            .orElseThrow(() -> new InvestmentQueryNotFoundException("해당 투자자를 찾을 수 없습니다."));
    if (!investor.getAccountId().equals(currentAccountId)) {
      throw new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다.");
    }
  }
}
