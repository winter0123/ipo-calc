package com.vibeaiteam.accountauthentication.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * AC 인용: docs/breakdown/account-authentication.md "2. Account 도메인 및 보안 기초 설정" -
 * CustomUserDetailsService가 Account 엔티티를 기반으로 UserDetails를 반환한다 - architecture.md: service.auth 패키지
 * - 인증/인가 비즈니스 로직 (로그인, 로그아웃 등) "4. 로그인/로그아웃 API" - Service에서 AccountRepository.findByEmail로 계정 조회
 */
@SpringBootTest
class CustomUserDetailsServiceTest {

  @Autowired private CustomUserDetailsService customUserDetailsService;

  @MockitoBean private AccountRepository accountRepository;

  private Account account;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();
    account =
        Account.builder()
            .email("user@example.com")
            .passwordHash("encodedPasswordHash")
            .name("김철수")
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .createdAt(now)
            .updatedAt(now)
            .lastLoginAt(null)
            .build();
  }

  @Test
  void loadUserByUsernameReturnsUserDetailsWhenAccountExists() {
    // Given: Account이 존재하고 findByEmail로 조회 가능
    // - breakdown AC-2: CustomUserDetailsService가 Account 엔티티를 기반으로 UserDetails를 반환한다
    // - architecture.md: 로그인 흐름 - Service에서 AccountRepository.findByEmail로 계정 조회

    when(accountRepository.findByEmail("user@example.com")).thenReturn(account);

    // When: loadUserByUsername 호출
    UserDetails userDetails = customUserDetailsService.loadUserByUsername("user@example.com");

    // Then: UserDetails가 반환되고, 이메일과 비밀번호 해시가 매핑된다
    assertThat(userDetails).isNotNull();
    assertThat(userDetails.getUsername()).isEqualTo("user@example.com");
    assertThat(userDetails.getPassword()).isEqualTo("encodedPasswordHash");
    assertThat(userDetails.isEnabled()).isTrue();
    assertThat(userDetails.isAccountNonExpired()).isTrue();
    assertThat(userDetails.isAccountNonLocked()).isTrue();
    assertThat(userDetails.isCredentialsNonExpired()).isTrue();
  }

  @Test
  void loadUserByUsernameThrowsUsernameNotFoundExceptionWhenAccountDoesNotExist() {
    // Given: Account이 존재하지 않음 (findByEmail returns null)
    // - architecture.md: 로그인 횐율 - 계정이 존재하지 않을 때 동일한 오류 메시지 반환
    // - breakdown AC-4: 존재하지 않는 계정으로 요청 시 동일한 오류 메시지와 함께 HTTP 401 반환

    when(accountRepository.findByEmail("nonexistent@example.com")).thenReturn(null);

    // When & Then: UsernameNotFoundException 발생
    assertThrows(
        UsernameNotFoundException.class,
        () -> {
          customUserDetailsService.loadUserByUsername("nonexistent@example.com");
        });
  }
}
