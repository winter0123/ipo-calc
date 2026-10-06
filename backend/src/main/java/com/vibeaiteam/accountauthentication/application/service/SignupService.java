package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.config.SignupMode;
import com.vibeaiteam.accountauthentication.config.SignupPolicy;
import com.vibeaiteam.accountauthentication.config.security.SessionCookieWriter;
import com.vibeaiteam.accountauthentication.controller.dto.SignupRequest;
import com.vibeaiteam.accountauthentication.controller.dto.SignupResponse;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.DuplicateEmailException;
import com.vibeaiteam.accountauthentication.exception.SignupClosedException;
import com.vibeaiteam.accountauthentication.exception.ValidationFailedException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
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
  private final SessionCookieWriter sessionCookieWriter;
  private final SignupPolicy signupPolicy;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  private static final Pattern PASSWORD_PATTERN =
      Pattern.compile(
          "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$");

  /** 이메일 중복을 확인하고 계정을 생성한다. */
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

    validatePassword(request.getPassword());

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

  private void validatePassword(String password) {
    if (!PASSWORD_PATTERN.matcher(password).matches()) {
      throw new ValidationFailedException(
          "password", "비밀번호는 최소 8자 이상이며 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다");
    }
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

    securityContextRepository.saveContext(context, httpServletRequest, httpServletResponse);

    sessionCookieWriter.issueIfAbsent(httpServletResponse, session.getId());
  }
}
