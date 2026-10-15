package com.ipocalc.accountauthentication.util.security;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 현재 로그인한 계정 정보를 조회하는 유틸리티. */
@Component
public class SecurityUtils {

  private final AccountRepository accountRepository;

  /** 계정 저장소를 주입받아 생성한다. */
  public SecurityUtils(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  /** 인증 정보로 현재 로그인한 계정의 ID를 반환한다. */
  public Long getCurrentAccountId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new IllegalStateException("인증된 계정을 찾을 수 없습니다");
    }
    String email = authentication.getName();
    Account account = accountRepository.findByEmail(email);
    if (account == null) {
      throw new IllegalStateException("인증된 계정을 찾을 수 없습니다");
    }
    return account.getId();
  }
}
