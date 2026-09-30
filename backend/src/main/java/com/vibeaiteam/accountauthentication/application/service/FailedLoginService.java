package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.AccountLockedException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패 횟수 기록과 계정 잠금을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class FailedLoginService {

  private final AccountRepository accountRepository;
  private final Clock clock;

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCK_DURATION_MINUTES = 30;

  /**
   * 로그인 실패 횟수를 늘리고 5회 이상이면 30분간 계정을 잠근다. 호출 측이 롤백돼도 남도록 새 트랜잭션으로 실행한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailedLogin(Account account) {
    // 이미 잠긴 계정인지 확인
    if (account.isAccountLocked(LocalDateTime.now(clock))) {
      throw new AccountLockedException("계정이 잠겼습니다. 관리자에게 문의하세요");
    }

    account.incrementFailedLoginAttempts();
    account.setUpdatedAt(LocalDateTime.now(clock));

    if (account.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
      account.setLockedUntil(LocalDateTime.now(clock).plusMinutes(LOCK_DURATION_MINUTES));
    }

    accountRepository.saveAndFlush(account);
  }
}
