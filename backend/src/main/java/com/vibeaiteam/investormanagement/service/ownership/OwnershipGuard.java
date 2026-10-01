package com.vibeaiteam.investormanagement.service.ownership;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 투자자가 현재 로그인 계정 소유인지 검증하는 공통 뼈대.
 * 예외 타입과 메시지는 Epic마다 다르므로 호출부가 Supplier로 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class OwnershipGuard {

  private final SecurityUtils securityUtils;
  private final InvestorRepository investorRepository;

  /**
   * 투자자가 현재 계정 소유가 아니면 forbidden이 만든 예외를 던진다.
   *
   * @return 현재 로그인 계정 ID
   */
  public Long requireOwner(Investor investor, Supplier<? extends RuntimeException> forbidden) {
    Long currentAccountId = securityUtils.getCurrentAccountId();
    if (!currentAccountId.equals(investor.getAccountId())) {
      throw forbidden.get();
    }
    return currentAccountId;
  }

  /**
   * 투자자를 조회한 뒤 현재 계정 소유인지 검증한다. 표준 메시지로 도메인 예외를 던진다.
   *
   * @param investorId 투자자 ID
   * @param errors 도메인 예외(없음 404, 타 계정 403)
   * @return 소유가 확인된 투자자
   */
  public Investor requireOwnedInvestor(Long investorId, OwnershipErrors errors) {
    return requireOwnedInvestor(
        investorId,
        () -> errors.notFound(OwnershipErrors.INVESTOR_NOT_FOUND),
        () -> errors.forbidden(OwnershipErrors.NOT_OWNER));
  }

  /**
   * 투자자를 조회한 뒤 현재 계정 소유인지 검증한다. 없으면 notFound, 타 계정 소유면 forbidden 예외.
   */
  public Investor requireOwnedInvestor(
      Long investorId,
      Supplier<? extends RuntimeException> notFound,
      Supplier<? extends RuntimeException> forbidden) {
    Long currentAccountId = securityUtils.getCurrentAccountId();
    Investor investor = investorRepository.findById(investorId).orElseThrow(notFound);
    if (!currentAccountId.equals(investor.getAccountId())) {
      throw forbidden.get();
    }
    return investor;
  }
}
