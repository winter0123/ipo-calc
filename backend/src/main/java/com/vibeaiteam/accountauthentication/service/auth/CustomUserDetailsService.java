package com.vibeaiteam.accountauthentication.service.auth;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final AccountRepository accountRepository;

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
        .disabled(false)
        .accountExpired(false)
        .accountLocked(false)
        .credentialsExpired(false)
        .build();
  }
}
