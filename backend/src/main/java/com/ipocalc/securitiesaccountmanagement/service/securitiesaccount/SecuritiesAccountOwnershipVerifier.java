package com.ipocalc.securitiesaccountmanagement.service.securitiesaccount;

import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.investormanagement.service.ownership.OwnershipGuard;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountForbiddenException;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 증권계좌와 투자자의 계정 소유권을 검증한다. */
@Component
@RequiredArgsConstructor
public class SecuritiesAccountOwnershipVerifier {

  private final InvestorRepository investorRepository;
  private final OwnershipGuard ownershipGuard;

  /** 계좌에 연결된 투자자를 조회해 소유 계정을 확인한다. */
  public Long requireOwner(SecuritiesAccount account) {
    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));
    return ownershipGuard.requireOwner(investor, SecuritiesAccountOwnershipVerifier::forbidden);
  }

  /** 투자자 ID의 존재와 현재 계정 소유 여부를 확인한다. */
  public void requireOwnedInvestor(Long investorId) {
    Investor investor =
        investorRepository
            .findById(investorId)
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("존재하지 않는 투자자입니다"));
    ownershipGuard.requireOwner(investor, SecuritiesAccountOwnershipVerifier::forbidden);
  }

  private static SecuritiesAccountForbiddenException forbidden() {
    return new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
  }
}
