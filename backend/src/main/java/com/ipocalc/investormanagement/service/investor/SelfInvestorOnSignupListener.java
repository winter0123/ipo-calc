package com.ipocalc.investormanagement.service.investor;

import com.ipocalc.accountauthentication.domain.event.AccountSignedUpEvent;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입 직후 본인 투자자 한 명을 자동으로 만든다. */
@Component
@RequiredArgsConstructor
public class SelfInvestorOnSignupListener {

  private final InvestorRepository investorRepository;
  private final Clock clock;

  /** 가입 계정에 표시 이름과 본인 관계로 ACTIVE 투자자를 저장한다. */
  @EventListener
  @Transactional
  public void onAccountSignedUp(AccountSignedUpEvent event) {
    LocalDateTime now = LocalDateTime.now(clock);
    investorRepository.save(
        Investor.builder()
            .accountId(event.accountId())
            .displayName(event.displayName())
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }
}
