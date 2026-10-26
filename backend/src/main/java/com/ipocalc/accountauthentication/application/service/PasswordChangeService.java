package com.ipocalc.accountauthentication.application.service;

import com.ipocalc.accountauthentication.controller.dto.PasswordChangeRequest;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.exception.InvalidCurrentPasswordException;
import com.ipocalc.accountauthentication.exception.ValidationFailedException;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 변경 서비스. */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordChangeService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRegistry sessionRegistry;
  private final Clock clock;

  /** 현재 비밀번호를 확인한 뒤 새 비밀번호로 변경한다. */
  public void changePassword(
      PasswordChangeRequest request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    UserDetails userDetails =
        (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = userDetails.getUsername();

    Account account = accountRepository.findByEmail(email);
    if (account == null) {
      throw new IllegalStateException("인증된 사용자를 찾을 수 없습니다");
    }

    validateCurrentPassword(request.getCurrentPassword(), account);
    validateNewPassword(request.getNewPassword());
    validateConfirmPassword(request.getNewPassword(), request.getConfirmPassword());

    String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
    account.changePassword(newPasswordHash, LocalDateTime.now(clock));
    accountRepository.saveAndFlush(account);

    invalidateOtherSessions(httpServletRequest);
  }

  private void validateCurrentPassword(String currentPassword, Account account) {
    if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
      throw new InvalidCurrentPasswordException("현재 비밀번호가 올바르지 않습니다");
    }
  }

  private void validateNewPassword(String newPassword) {
    PasswordPolicy.validate("newPassword", newPassword);
  }

  private void validateConfirmPassword(String newPassword, String confirmPassword) {
    if (!newPassword.equals(confirmPassword)) {
      throw new ValidationFailedException("confirmPassword", "새 비밀번호와 확인이 일치하지 않습니다");
    }
  }

  private void invalidateOtherSessions(HttpServletRequest httpServletRequest) {
    String sessionId =
        httpServletRequest.getSession(false) != null
            ? httpServletRequest.getSession(false).getId()
            : null;
    if (sessionId != null) {
      Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      if (principal instanceof UserDetails) {
        sessionRegistry.getAllSessions(principal, false).stream()
            .filter(session -> !session.getSessionId().equals(sessionId))
            .forEach(session -> session.expireNow());
      }
    }
  }
}
