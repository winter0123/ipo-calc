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

/**
 * 계정 JPA 엔티티.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "name", length = 50)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  @Builder.Default
  private AccountRole role = AccountRole.USER;

  @Column(name = "failed_login_attempts", nullable = false)
  @Builder.Default
  private Integer failedLoginAttempts = 0;

  @Column(name = "locked_until")
  private LocalDateTime lockedUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  public void setEmail(String email) {
    this.email = email != null ? email.toLowerCase() : null;
  }

  /**
   * 로그인 실패 횟수를 1 증가시킨다.
   */
  public void incrementFailedLoginAttempts() {
    this.failedLoginAttempts = this.failedLoginAttempts + 1;
  }

  /**
   * 로그인 실패 횟수를 0으로 초기화한다.
   */
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

  /**
   * Lombok 빌더 확장. 이메일을 소문자로 정규화한다.
   */
  public static class AccountBuilder {
    private String email;

    /**
     * 이메일을 소문자로 변환해 설정한다. null이면 null을 유지한다.
     */
    public AccountBuilder email(String email) {
      this.email = email != null ? email.toLowerCase() : null;
      return this;
    }
  }
}
