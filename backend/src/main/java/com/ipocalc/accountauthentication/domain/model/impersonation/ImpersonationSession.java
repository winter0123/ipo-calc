package com.ipocalc.accountauthentication.domain.model.impersonation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 임퍼소네이션 세션 엔티티. 관리자가 대상 회원으로 가장하여 조회하는 세션을 관리한다. */
@Entity
@Table(name = "impersonation_sessions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ImpersonationSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 실제 행위자(관리자) 계정 ID. */
  @Column(name = "actor_admin_id", nullable = false)
  private Long actorAdminId;

  /** 임퍼소네이션 대상 회원 계정 ID. */
  @Column(name = "subject_account_id", nullable = false)
  private Long subjectAccountId;

  /** 임퍼소네이션 세션 고유 식별자 (UUID). */
  @Column(name = "session_id", nullable = false, unique = true, length = 36)
  private String sessionId;

  /** 마커가 저장된 Spring Session ID. 임퍼소네이션 UUID와 별개다. */
  @Column(name = "spring_session_id", length = 100)
  private String springSessionId;

  /** 시작 시각. */
  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  /** 만료 시각. */
  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  /** 종료 시각 (만료/종료 시 설정). */
  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  /** 종료 사유: MANUAL, EXPIRED, REPLACED. */
  @Column(name = "ended_reason", length = 20)
  private String endedReason;

  /** 읽기 전용 여부 (true: 조회만 허용). */
  @Column(name = "read_only", nullable = false)
  @Builder.Default
  private Boolean readOnly = true;

  /** 생성 시각. */
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /** Initializes creation and update timestamps before persistence. */
  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /** Refreshes the update timestamp before persistence. */
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  /** 세션 만료 여부를 반환한다. */
  public boolean isExpired() {
    return isExpired(Clock.systemDefaultZone());
  }

  /**
   * 주어진 시각 기준으로 세션 만료 여부를 반환한다.
   *
   * @param clock 시간 기준
   * @return 만료됐으면 {@code true}
   */
  public boolean isExpired(Clock clock) {
    return LocalDateTime.now(clock).isAfter(this.expiresAt);
  }

  /**
   * 세션을 종료한다.
   *
   * @param reason 종료 사유
   */
  public void end(String reason) {
    end(reason, LocalDateTime.now());
  }

  /**
   * 지정 시각 기준으로 세션을 종료한다.
   *
   * @param reason 종료 사유
   * @param now 종료 시각
   */
  public void end(String reason, LocalDateTime now) {
    if (this.endedAt == null) {
      this.endedAt = now;
      this.endedReason = reason;
    }
    this.updatedAt = now;
  }

  /** 세션 활성 여부를 반환한다. */
  public boolean isActive() {
    return isActive(Clock.systemDefaultZone());
  }

  /**
   * 주어진 시각 기준으로 세션 활성 여부를 반환한다.
   *
   * @param clock 시간 기준
   * @return 활성 상태면 {@code true}
   */
  public boolean isActive(Clock clock) {
    return this.endedAt == null && !isExpired(clock);
  }

  /** 행위자인 관리자 계정 ID를 반환한다. */
  public Long getActorAdminId() {
    return actorAdminId;
  }

  /** 대상 회원 계정 ID를 반환한다. */
  public Long getSubjectAccountId() {
    return subjectAccountId;
  }

  /** 임퍼소네이션 세션 ID를 반환한다. */
  public String getSessionId() {
    return sessionId;
  }

  /** 시작 시각을 반환한다. */
  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  /** 만료 시각을 반환한다. */
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  /** 종료 시각을 반환한다. */
  public LocalDateTime getEndedAt() {
    return endedAt;
  }

  /** 종료 사유를 반환한다. */
  public String getEndedReason() {
    return endedReason;
  }

  /** 읽기 전용 여부를 반환한다. */
  public Boolean getReadOnly() {
    return readOnly;
  }

  /** 생성 시각을 반환한다. */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
