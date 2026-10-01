package com.vibeaiteam.accountauthentication.service.auth;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 이메일로 계정을 조회해 Spring Security 사용자 정보를 제공한다.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final AccountRepository accountRepository;

  /**
   * 계정 저장소를 주입받아 생성한다.
   */
  public CustomUserDetailsService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    Account account = accountRepository.findByEmail(email);
    if (account == null) {
      throw new UsernameNotFoundException("User not found with email: " + email);
    }

    return User.builder()
        .username(account.getEmail())
        .password(account.getPasswordHash())
        .roles(account.getRole().name())
        .disabled(!account.isApproved())
        .accountExpired(false)
        .accountLocked(false)
        .credentialsExpired(false)
        .build();
  }
}
