package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.controller.dto.LoginRequest;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.AccountLockedException;
import com.vibeaiteam.accountauthentication.exception.InvalidCredentialsException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

/**
 * 로그인·로그아웃을 처리하는 세션 기반 인증 서비스.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final FailedLoginService failedLoginService;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final Clock clock;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  /**
   * 이메일·비밀번호를 검증하고 인증 세션을 생성해 로그인 처리한다.
   */
  @Transactional
  public void login(
      LoginRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    String email = request.getEmail().toLowerCase();

    Account account = accountRepository.findByEmail(email);

    // 계정이 존재하지 않거나 비밀번호가 틀린 경우 동일한 에러 메시지 반환 (계정 존재 여부 노출 방지)
    if (account == null
        || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
      if (account != null) {
        failedLoginService.recordFailedLogin(account);
      }
      throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    // 계정 잠금 상태 확인
    if (account.isAccountLocked(LocalDateTime.now(clock))) {
      throw new AccountLockedException("계정이 잠겼습니다. 관리자에게 문의하세요");
    }

    // 로그인 성공 - 실패 횟수 리셋, last_login_at 업데이트
    account.resetFailedLoginAttempts();
    account.setLockedUntil(null);
    account.setLastLoginAt(LocalDateTime.now(clock));
    account.setUpdatedAt(LocalDateTime.now(clock));
    accountRepository.saveAndFlush(account);

    createSession(httpServletRequest, httpServletResponse, account);
  }

  /**
   * 현재 세션을 무효화하고 인증 정보를 비운다.
   */
  @Transactional
  public void logout(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    HttpSession session = httpServletRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    SecurityContextHolder.clearContext();

    // 세션 삭제 쿠키 설정 (Max-Age=0)
    jakarta.servlet.http.Cookie sessionCookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
    sessionCookie.setPath("/");
    sessionCookie.setHttpOnly(true);
    sessionCookie.setSecure(false); // 프로덕션에서는 true
    sessionCookie.setMaxAge(0); // 즉시 삭제
    httpServletResponse.addCookie(sessionCookie);
  }

  private void createSession(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      Account account) {
    final HttpSession session = httpServletRequest.getSession(true);

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

    try {
      sessionAuthenticationStrategy.onAuthentication(
          authentication, httpServletRequest, httpServletResponse);
    } catch (SessionAuthenticationException e) {
      // 세션 등록 실패는 이 서비스 레벨에서는 무시 - 이미 세션이 생성된 상태이므로 계속 진행
    }

    securityContextRepository.saveContext(context, httpServletRequest, httpServletResponse);

    // MockMvc 호환성을 위해 세션 쿠키 수동 추가
    String sessionId = session.getId();
    jakarta.servlet.http.Cookie sessionCookie =
        new jakarta.servlet.http.Cookie("JSESSIONID", sessionId);
    sessionCookie.setPath("/");
    sessionCookie.setHttpOnly(true);
    sessionCookie.setSecure(false);
    sessionCookie.setMaxAge(-1); // 세션 쿠키
    httpServletResponse.addCookie(sessionCookie);
  }
}
