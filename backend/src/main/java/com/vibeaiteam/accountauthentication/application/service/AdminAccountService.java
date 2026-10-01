package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.controller.dto.AdminAccountResponse;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.AccountNotFoundException;
import com.vibeaiteam.accountauthentication.exception.ValidationFailedException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입 승인 관리(비공개 운영). 호출은 ADMIN 역할로만 열려 있다(SecurityConfig). */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

  private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

  private final AccountRepository accountRepository;
  private final SecurityUtils securityUtils;
  private final SessionRegistry sessionRegistry;
  private final Clock clock;

  /**
   * 계정 목록을 가입 순서대로 돌려준다.
   *
   * @param approved true면 승인된 계정, false면 승인 대기, null이면 전체
   * @return 계정 목록
   */
  @Transactional(readOnly = true)
  public List<AdminAccountResponse> list(Boolean approved) {
    List<Account> accounts =
        approved == null
            ? accountRepository.findAllByOrderByCreatedAtAsc()
            : accountRepository.findByApprovedOrderByCreatedAtAsc(approved);
    return accounts.stream().map(AdminAccountService::toResponse).toList();
  }

  /**
   * 계정을 승인한다. 이미 승인된 계정이면 그대로 둔다.
   *
   * @param accountId 대상 계정 ID
   */
  @Transactional
  public void approve(Long accountId) {
    Account account = find(accountId);
    account.setApproved(true);
    account.setUpdatedAt(LocalDateTime.now(clock));
    accountRepository.save(account);
    log.info("계정 승인: accountId={}", accountId);
  }

  /**
   * 계정의 승인을 취소하고 그 계정의 로그인 세션을 모두 끝낸다. 본인 계정은 취소할 수 없다.
   *
   * @param accountId 대상 계정 ID
   */
  @Transactional
  public void revoke(Long accountId) {
    if (accountId.equals(securityUtils.getCurrentAccountId())) {
      throw new ValidationFailedException("accountId", "본인 계정의 승인은 취소할 수 없습니다");
    }
    Account account = find(accountId);
    account.setApproved(false);
    account.setUpdatedAt(LocalDateTime.now(clock));
    accountRepository.save(account);
    // 세션 레지스트리의 주체는 이메일이 같으면 같은 사용자로 취급된다(User.equals).
    User principal = new User(account.getEmail(), account.getPasswordHash(), List.of());
    sessionRegistry.getAllSessions(principal, false).forEach(session -> session.expireNow());
    log.info("계정 승인 취소: accountId={}", accountId);
  }

  private Account find(Long accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException("계정을 찾을 수 없습니다"));
  }

  private static AdminAccountResponse toResponse(Account account) {
    return new AdminAccountResponse(
        account.getId(),
        account.getEmail(),
        account.getName(),
        account.getRole().name(),
        account.isApproved(),
        account.getCreatedAt());
  }
}
