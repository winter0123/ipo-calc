package com.ipocalc.accountauthentication.application.service;

import com.ipocalc.accountauthentication.config.security.SessionCookieWriter;
import com.ipocalc.accountauthentication.controller.dto.CurrentAccountResponse;
import com.ipocalc.accountauthentication.controller.dto.LoginRequest;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.exception.AccountLockedException;
import com.ipocalc.accountauthentication.exception.AccountNotFoundException;
import com.ipocalc.accountauthentication.exception.AccountPendingApprovalException;
import com.ipocalc.accountauthentication.exception.InvalidCredentialsException;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인·로그아웃을 처리하는 세션 기반 인증 서비스. */
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCK_DURATION_MINUTES = 30;

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final Clock clock;
  private final SessionCookieWriter sessionCookieWriter;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  /**
   * 이메일·비밀번호를 검증하고 인증 세션을 생성해 로그인 처리한다. 외부 트랜잭션을 두지 않는다: 각 DB 작업이 짧은 자체 트랜잭션이라 요청이
   * 커넥션을 한 번에 하나만 쓰고, bcrypt 검증 동안은 커넥션을 쥐지 않는다(동시 오답 요청으로 풀이 마르지 않는다).
   */
  public void login(
      LoginRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    String email = request.getEmail().toLowerCase();
    LocalDateTime now = LocalDateTime.now(clock);

    Account account = accountRepository.findByEmail(email);

    // 잠긴 계정은 비밀번호를 맞춰도 틀려도 로그인할 수 없다(시도로 잠금이 연장되지도 않는다).
    if (account != null && account.isAccountLocked(now)) {
      throw new AccountLockedException("계정이 잠겼습니다. 관리자에게 문의하세요");
    }

    // 계정이 존재하지 않거나 비밀번호가 틀린 경우 동일한 에러 메시지 반환 (계정 존재 여부 노출 방지)
    if (account == null
        || !passwordMatches(request.getPassword(), account.getPasswordHash())) {
      if (account != null) {
        accountRepository.recordFailedLogin(
            account.getId(), now, MAX_FAILED_ATTEMPTS, now.plusMinutes(LOCK_DURATION_MINUTES));
      }
      throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    // 비밀번호가 맞아도 관리자 승인 전이면 로그인시키지 않는다(실패 횟수에는 넣지 않는다).
    if (!account.isApproved()) {
      throw new AccountPendingApprovalException("관리자 승인 대기 중입니다");
    }

    accountRepository.recordLoginSuccess(account.getId(), now);

    createSession(httpServletRequest, httpServletResponse, account);
  }

  /**
   * 현재 로그인한 계정의 정보를 돌려준다.
   *
   * @return 계정 ID·이메일·이름·역할
   * @throws AccountNotFoundException 세션의 계정이 더 이상 없을 때
   */
  @Transactional(readOnly = true)
  public CurrentAccountResponse currentAccount() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    Account account = accountRepository.findByEmail(authentication.getName());
    if (account == null) {
      throw new AccountNotFoundException("계정을 찾을 수 없습니다");
    }
    return new CurrentAccountResponse(
        account.getId(), account.getEmail(), account.getName(), account.getRole().name());
  }

  /** 현재 세션을 무효화하고 인증 정보를 비운다. */
  @Transactional
  public void logout(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    HttpSession session = httpServletRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    SecurityContextHolder.clearContext();

    sessionCookieWriter.expire(httpServletResponse);
  }

  /** 72바이트를 넘는 입력은 bcrypt에 넘기지 않고 불일치로 본다(가입·변경에서 막으므로 맞을 수 없다). */
  private boolean passwordMatches(String raw, String hash) {
    return raw.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES
        && passwordEncoder.matches(raw, hash);
  }

  private void createSession(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      Account account) {
    // 로그인 전에 받은 세션 ID를 그대로 쓰면 세션 고정 공격이 가능하므로 기존 세션이 있으면 ID를 바꾼다.
    final boolean hadSession = httpServletRequest.getSession(false) != null;
    httpServletRequest.getSession(true);
    if (hadSession) {
      httpServletRequest.changeSessionId();
    }

    UserDetails userDetails =
        User.builder()
            .username(account.getEmail())
            .password(account.getPasswordHash())
            .authorities("ROLE_" + account.getRole().name())
            .build();

    var authentication =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    try {
      sessionAuthenticationStrategy.onAuthentication(
          authentication, httpServletRequest, httpServletResponse);
    } catch (SessionAuthenticationException e) {
      // 세션 등록 실패는 이 서비스 레벨에서는 무시 - 이미 세션이 생성된 상태이므로 계속 진행
    }

    securityContextRepository.saveContext(context, httpServletRequest, httpServletResponse);
  }
}
