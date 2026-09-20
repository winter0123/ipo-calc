package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.AccountLockedException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FailedLoginService {

    private final AccountRepository accountRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(Account account) {
        // 이미 잠긴 계정인지 확인
        if (account.isAccountLocked()) {
            throw new AccountLockedException("계정이 잠겼습니다. 관리자에게 문의하세요");
        }

        account.incrementFailedLoginAttempts();
        account.setUpdatedAt(LocalDateTime.now());

        if (account.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            account.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }

        accountRepository.saveAndFlush(account);
    }
}