package com.vibeaiteam.accountauthentication.util.security;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

  private final AccountRepository accountRepository;

  public SecurityUtils(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

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
