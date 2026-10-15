package com.ipocalc.accountauthentication.repository.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * AC 인용: docs/breakdown/account-authentication.md "2. Account 도메인 및 보안 기초 설정" - AccountRepository가
 * JpaRepository를 상속하여 생성된다 - V1__init_account.sql이 계정 테이블을 생성하고 인덱스를 적용한다 (UNIQUE INDEX
 * uk_account_email (email)) "3. 회원가입 API" - 계정 생성 시 이메일 중복 검증 (AccountRepository.existsByEmail)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AccountRepositoryTest {

  @Autowired private AccountRepository accountRepository;

  private Account savedAccount;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();
    savedAccount =
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
  void saveAccountFindByEmailReturnsAccount() {
    // Given: 저장할 Account이 존재함
    // - architecture.md: email VARCHAR(254) NOT NULL UNIQUE
    // - breakdown AC-2: AccountRepository가 JpaRepository를 상속한다

    // When: Account 저장 후 findByEmail로 조회
    Account saved = accountRepository.save(savedAccount);
    Account found = accountRepository.findByEmail("user@example.com");

    // Then: 저장된 계정이 이메일로 정상 조회된다
    assertThat(found).isNotNull();
    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getEmail()).isEqualTo("user@example.com");
    assertThat(found.getPasswordHash()).isEqualTo("encodedPasswordHash");
    assertThat(found.getName()).isEqualTo("김철수");
  }

  @Test
  void saveDuplicateEmailThrowsDataIntegrityViolationException() {
    // Given: 동일 이메일로 계정이 하나 저장되어 있음
    // - architecture.md: UNIQUE INDEX uk_account_email (email)
    // - breakdown AC-2: V1__init_account.sql이 UNIQUE 제약을 적용한다

    accountRepository.save(savedAccount);

    Account duplicateAccount =
        Account.builder()
            .email("user@example.com")
            .passwordHash("differentPasswordHash")
            .name("김영희")
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When & Then: 동일 이메일로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          accountRepository.saveAndFlush(duplicateAccount);
        });
  }

  @Test
  void existsByEmailReturnsTrueWhenAccountExists() {
    // Given: 이메일로 저장된 계정이 존재함
    // - breakdown AC-3: 회원가입 시 AccountRepository.existsByEmail로 중복 확인
    // - breakdown AC-2: AccountRepository가 JpaRepository를 상속한다

    accountRepository.save(savedAccount);

    // When: 존재하는 이메일로 existsByEmail 호출
    boolean exists = accountRepository.existsByEmail("user@example.com");

    // Then: true를 반환한다
    assertThat(exists).isTrue();
  }

  @Test
  void existsByEmailReturnsFalseWhenAccountDoesNotExist() {
    // Given: 특정 이메일로 저장된 계정이 없음
    // - breakdown AC-3: 회원가입 시 AccountRepository.existsByEmail로 중복 확인

    // When: 존재하지 않는 이메일로 existsByEmail 호출
    boolean exists = accountRepository.existsByEmail("nonexistent@example.com");

    // Then: false를 반환한다
    assertThat(exists).isFalse();
  }
}
