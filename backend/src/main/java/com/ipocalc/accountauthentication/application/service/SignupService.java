package com.ipocalc.accountauthentication.application.service;

import com.ipocalc.accountauthentication.config.SignupMode;
import com.ipocalc.accountauthentication.config.SignupPolicy;
import com.ipocalc.accountauthentication.controller.dto.SignupRequest;
import com.ipocalc.accountauthentication.controller.dto.SignupResponse;
import com.ipocalc.accountauthentication.domain.event.AccountSignedUpEvent;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.exception.DuplicateEmailException;
import com.ipocalc.accountauthentication.exception.SignupClosedException;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입 서비스. */
@Service
@RequiredArgsConstructor
@Transactional
public class SignupService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final SignupPolicy signupPolicy;
  private final ApplicationEventPublisher eventPublisher;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  /** 이메일 중복을 확인하고 계정을 생성한다. 본인 투자자는 가입 이벤트로 같이 만든다. */
  public SignupResponse signup(
      SignupRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    if (signupPolicy.mode() == SignupMode.CLOSED) {
      throw new SignupClosedException("지금은 가입을 받지 않습니다");
    }
    boolean autoApproved = signupPolicy.mode() == SignupMode.OPEN;
    String email = request.getEmail().toLowerCase();

    if (accountRepository.existsByEmail(email)) {
      throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
    }

    PasswordPolicy.validate("password", request.getPassword());

    String passwordHash = passwordEncoder.encode(request.getPassword());

    Account account =
        Account.builder()
            .email(email)
            .passwordHash(passwordHash)
            .name(request.getName())
            .approved(autoApproved)
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .createdAt(LocalDateTime.now(clock))
            .updatedAt(LocalDateTime.now(clock))
            .build();

    Account savedAccount = accountRepository.saveAndFlush(account);

    String displayName = resolveDisplayName(request);
    eventPublisher.publishEvent(
        new AccountSignedUpEvent(savedAccount.getId(), displayName));

    if (autoApproved) {
      createSession(httpServletRequest, httpServletResponse, savedAccount);
    }

    return SignupResponse.builder()
        .accountId(savedAccount.getId())
        .email(savedAccount.getEmail())
        .name(savedAccount.getName())
        .approved(autoApproved)
        .build();
  }

  private static String resolveDisplayName(SignupRequest request) {
    if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
      return request.getDisplayName().trim();
    }
    if (request.getName() != null && !request.getName().isBlank()) {
      return request.getName().trim();
    }
    return "본인";
  }

  private void createSession(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      Account account) {
    // 가입 전에 받은 세션 ID를 그대로 쓰면 세션 고정 공격이 가능하므로 기존 세션이 있으면 ID를 바꾼다.
    final boolean hadSession = httpServletRequest.getSession(false) != null;
    httpServletRequest.getSession(true);
    if (hadSession) {
      httpServletRequest.changeSessionId();
    }

    UserDetails userDetails =
        User.builder()
            .username(account.getEmail())
            .password(account.getPasswordHash())
            .authorities("ROLE_USER")
            .build();

    var authentication =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    // 세션 레지스트리에 등록해 두어야 승인 취소 때 이 세션도 끊긴다.
    sessionAuthenticationStrategy.onAuthentication(
        authentication, httpServletRequest, httpServletResponse);

    securityContextRepository.saveContext(context, httpServletRequest, httpServletResponse);
  }
}
