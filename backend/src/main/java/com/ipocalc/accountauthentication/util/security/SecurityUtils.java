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

  /** 임퍼소네이션 중이면 대상 회원 ID, 아니면 관리자 ID 반환. */
  public Long getEffectiveAccountId() {
    if (ImpersonationContextHolder.isImpersonating()) {
      return ImpersonationContextHolder.getSubjectAccountId();
    }
    return getCurrentAccountId();
  }

  /** 임퍼소네이션 중이면 대상 회원 이메일, 아니면 관리자 이메일 반환. */
  public String getEffectiveEmail() {
    if (ImpersonationContextHolder.isImpersonating()) {
      Long subjectId = ImpersonationContextHolder.getSubjectAccountId();
      return accountRepository.findById(subjectId).map(Account::getEmail).orElse(null);
    }
    return getCurrentAccountEmail();
  }

  /** 현재 로그인한 계정의 이메일 반환. */
  public String getCurrentAccountEmail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    return authentication.getName();
  }
}
