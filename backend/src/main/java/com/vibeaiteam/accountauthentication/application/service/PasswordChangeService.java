package com.vibeaiteam.accountauthentication.application.service;

import com.vibeaiteam.accountauthentication.controller.dto.PasswordChangeRequest;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.exception.InvalidCurrentPasswordException;
import com.vibeaiteam.accountauthentication.exception.ValidationFailedException;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordChangeService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRegistry sessionRegistry;

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
    account.setPasswordHash(newPasswordHash);
    account.setUpdatedAt(LocalDateTime.now());
    accountRepository.saveAndFlush(account);

    invalidateOtherSessions(httpServletRequest);
  }

  private void validateCurrentPassword(String currentPassword, Account account) {
    if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
      throw new InvalidCurrentPasswordException("현재 비밀번호가 올바르지 않습니다");
    }
  }

  private void validateNewPassword(String newPassword) {
    if (newPassword == null || newPassword.length() < 8) {
      throw new ValidationFailedException("newPassword", "비밀번호는 최소 8자 이상이어야 합니다");
    }

    boolean hasLetter = false;
    boolean hasDigit = false;
    boolean hasSpecial = false;

    for (char c : newPassword.toCharArray()) {
      if (Character.isLetter(c)) {
        hasLetter = true;
      } else if (Character.isDigit(c)) {
        hasDigit = true;
      } else {
        hasSpecial = true;
      }
    }

    int typesCount = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
    if (typesCount < 2) {
      throw new ValidationFailedException(
          "newPassword", "비밀번호는 최소 8자 이상이며 영문, 숫자, 특수문자 중 2종류 이상을 포함해야 합니다");
    }
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
