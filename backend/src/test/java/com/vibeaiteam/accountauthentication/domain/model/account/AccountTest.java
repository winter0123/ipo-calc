package com.vibeaiteam.accountauthentication.domain.model.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * AC 인용: docs/breakdown/account-authentication.md "2. Account 도메인 및 보안 기초 설정" - Account 엔티티가 정의되어
 * architecture.md의 테이블 구조와 일치한다 (architecture.md: Account 테이블) - architecture.md: id, email,
 * password_hash, name, failed_login_attempts, locked_until, created_at, updated_at, last_login_at
 */
class AccountTest {

  @Test
  void accountBuilderCreatesAccountWithCorrectFields() {
    // Given: architecture.md Account 테이블 컬럼
    // - email VARCHAR(254) NOT NULL UNIQUE
    // - password_hash VARCHAR(255) NOT NULL
    // - name VARCHAR(50) NULLABLE
    // AC: Account 엔티티가 architecture.md의 테이블 구조와 일치한다

    // When: Account 엔티티 빌더로 생성
    LocalDateTime now = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
    Account account =
        Account.builder()
            .email("user@example.com")
            .passwordHash("encodedPassword123")
            .name("김철수")
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .createdAt(now)
            .updatedAt(now)
            .lastLoginAt(null)
            .build();

    // Then: 모든 필드가 올바르게 매핑된다
    assertThat(account.getEmail()).isEqualTo("user@example.com");
    assertThat(account.getPasswordHash()).isEqualTo("encodedPassword123");
    assertThat(account.getName()).isEqualTo("김철수");
    assertThat(account.getFailedLoginAttempts()).isEqualTo(0);
    assertThat(account.getLockedUntil()).isNull();
    assertThat(account.getCreatedAt()).isEqualTo(now);
    assertThat(account.getUpdatedAt()).isEqualTo(now);
    assertThat(account.getLastLoginAt()).isNull();
  }

  @Test
  void failedLoginAttemptsShouldDefaultToZero() {
    // Given: architecture.md Account 테이블
    // - failed_login_attempts INTEGER NOT NULL DEFAULT 0
    // AC: Account 엔티티가 architecture.md의 테이블 구조와 일치한다

    // When: failedLoginAttempts를 명시하지 않고 Account 생성
    Account account =
        Account.builder().email("test@example.com").passwordHash("hashedPassword").build();

    // Then: failedLoginAttempts 기본값이 0이다
    assertThat(account.getFailedLoginAttempts()).isEqualTo(0);
  }

  @Test
  void emailShouldBeNormalizedToLowerCase() {
    // Given: architecture.md Account 테이블
    // - email VARCHAR(254) NOT NULL UNIQUE (소문자 정규화)
    // AC: Account 엔티티가 architecture.md의 테이블 구조와 일치한다

    // When: 대문자가 섞인 이메일로 Account 생성
    Account account =
        Account.builder().email("Test@Example.COM").passwordHash("hashedPassword").build();

    // Then: 이메일이 소문자로 정규화된다
    assertThat(account.getEmail()).isEqualTo("test@example.com");
  }

  @Test
  void incrementFailedLoginAttempts() {
    // Given: 실패 횟수가 0인 Account
    // - architecture.md: failed_login_attempts INTEGER NOT NULL DEFAULT 0
    // - breakdown AC: failed_login_attempts 필드 존재

    Account account =
        Account.builder()
            .email("user@example.com")
            .passwordHash("hashedPassword")
            .failedLoginAttempts(0)
            .build();

    // When: incrementFailedLoginAttempts 호출
    account.incrementFailedLoginAttempts();

    // Then: 실패 횟수가 1 증가한다
    assertThat(account.getFailedLoginAttempts()).isEqualTo(1);
  }

  @Test
  void resetFailedLoginAttempts() {
    // Given: 실패 횟수가 3회 누적된 Account
    // - breakdown AC: failed_login_attempts 관리 (리셋 기능)

    Account account =
        Account.builder()
            .email("user@example.com")
            .passwordHash("hashedPassword")
            .failedLoginAttempts(3)
            .build();

    // When: resetFailedLoginAttempts 호출
    account.resetFailedLoginAttempts();

    // Then: 실패 횟수가 0으로 초기화된다
    assertThat(account.getFailedLoginAttempts()).isEqualTo(0);
  }
}
