package com.vibeaiteam.accountauthentication.domain.model.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 계정 JPA 엔티티. */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Account {

  /** PK. DB가 자동으로 매기는 번호. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 로그인 이메일. 소문자로 저장, 중복 불가. */
  @Column(name = "email", nullable = false, unique = true, length = 254)
  private String email;

  /** 비밀번호 해시(BCrypt). 원문 비밀번호는 저장하지 않는다. */
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  /** 사용자 이름(가입 시 입력). */
  @Column(name = "name", length = 50)
  private String name;

  /** 권한. USER(일반) / ADMIN(관리자: 가입 승인·공모주 등록). */
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  @Builder.Default
  private AccountRole role = AccountRole.USER;

  /** 관리자 가입 승인 여부. false면 로그인 불가. */
  @Column(name = "approved", nullable = false)
  @Builder.Default
  private boolean approved = true;

  /** 연속 로그인 실패 횟수. 성공하면 0으로 초기화. */
  @Column(name = "failed_login_attempts", nullable = false)
  @Builder.Default
  private Integer failedLoginAttempts = 0;

  /** 로그인 잠금 해제 시각. 이 시각 전까지 로그인 불가, null이면 잠금 없음. */
  @Column(name = "locked_until")
  private LocalDateTime lockedUntil;

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** 마지막 로그인 성공 시각. */
  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  public void setEmail(String email) {
    this.email = email != null ? email.toLowerCase() : null;
  }

  /** 로그인 실패 횟수를 1 증가시킨다. */
  public void incrementFailedLoginAttempts() {
    this.failedLoginAttempts = this.failedLoginAttempts + 1;
  }

  /** 로그인 실패 횟수를 0으로 초기화한다. */
  public void resetFailedLoginAttempts() {
    this.failedLoginAttempts = 0;
  }

  /**
   * 주어진 시각 기준으로 계정이 잠겨 있는지 반환한다.
   *
   * @param now 현재 시각
   * @return 잠금 만료 전이면 true
   */
  public boolean isAccountLocked(LocalDateTime now) {
    return this.lockedUntil != null && this.lockedUntil.isAfter(now);
  }

  /** Lombok 빌더 확장. 이메일을 소문자로 정규화한다. */
  public static class AccountBuilder {
    private String email;

    /** 이메일을 소문자로 변환해 설정한다. null이면 null을 유지한다. */
    public AccountBuilder email(String email) {
      this.email = email != null ? email.toLowerCase() : null;
      return this;
    }
  }
}
