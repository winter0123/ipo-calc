package com.vibeaiteam.accountauthentication.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * AC 인용: docs/work-log/investor-management.md (D-I-007) - SecurityUtils.getCurrentAccountId()는 인증된
 * 사용자의 email(username)로 AccountRepository.findByEmail → Account.getId()를 반환한다 -
 * CustomUserDetailsService가 username = email로 설정하므로, SecurityContextHolder의
 * Authentication.getName()이 email이다 - Account가 null이면 IllegalStateException("인증된 계정을 찾을 수 없습니다")을
 * 던진다 - 인증 정보가 없는 상태에서 호출하면 예외가 발생해야 한다 (방어 코드)
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityUtilsTest {

  @Autowired private SecurityUtils securityUtils;

  @MockBean private AccountRepository accountRepository;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getCurrentAccountIdReturnsAccountIdWhenAuthenticatedAccountExists() {
    // Given: 인증된 사용자의 username(email)으로 DB에 Account가 존재
    Account account =
        Account.builder()
            .id(42L)
            .email("user@example.com")
            .passwordHash("encodedPasswordHash")
            .name("김철수")
            .failedLoginAttempts(0)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    when(accountRepository.findByEmail("user@example.com")).thenReturn(account);

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            "user@example.com", "encodedPasswordHash", List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);

    // When: getCurrentAccountId() 호출
    Long accountId = securityUtils.getCurrentAccountId();

    // Then: Account의 id가 반환된다
    assertThat(accountId).isEqualTo(42L);
  }

  @Test
  void getCurrentAccountIdThrowsIllegalStateExceptionWhenAccountDoesNotExist() {
    // Given: 인증된 사용자의 username(email)에 해당하는 Account가 DB에 없음
    when(accountRepository.findByEmail("orphan@example.com")).thenReturn(null);

    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("orphan@example.com", "pw", List.of()));

    // When & Then: IllegalStateException("인증된 계정을 찾을 수 없습니다") 발생
    assertThatThrownBy(() -> securityUtils.getCurrentAccountId())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("인증된 계정을 찾을 수 없습니다");
  }

  @Test
  void getCurrentAccountIdThrowsIllegalStateExceptionWhenAuthenticationIsMissing() {
    // Given: SecurityContextHolder에 인증 정보가 설정되어 있지 않음 (clearContext)

    // When & Then: 예외 발생
    assertThatThrownBy(() -> securityUtils.getCurrentAccountId())
        .isInstanceOf(IllegalStateException.class);
  }
}
