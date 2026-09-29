package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.controller.dto.SignupRequest;
import com.vibeaiteam.accountauthentication.controller.dto.SignupResponse;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.DuplicateEmailException;
import com.vibeaiteam.accountauthentication.exception.ValidationFailedException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

@Service
@RequiredArgsConstructor
@Transactional
public class SignupService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  private static final Pattern PASSWORD_PATTERN =
      Pattern.compile(
          "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$");

  public SignupResponse signup(
      SignupRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
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
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    Account savedAccount = accountRepository.saveAndFlush(account);

    createSession(httpServletRequest, httpServletResponse, savedAccount);

    return SignupResponse.builder()
        .accountId(savedAccount.getId())
        .email(savedAccount.getEmail())
        .name(savedAccount.getName())
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
    HttpSession session = httpServletRequest.getSession(true);

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

    // Manually add session cookie for MockMvc compatibility
    // In real servlet container, this is done automatically when session is created
    String sessionId = session.getId();
    jakarta.servlet.http.Cookie sessionCookie =
        new jakarta.servlet.http.Cookie("JSESSIONID", sessionId);
    sessionCookie.setPath("/");
    sessionCookie.setHttpOnly(true);
    sessionCookie.setSecure(false); // Set to true in production with HTTPS
    sessionCookie.setMaxAge(-1); // Session cookie
    httpServletResponse.addCookie(sessionCookie);
  }
}
