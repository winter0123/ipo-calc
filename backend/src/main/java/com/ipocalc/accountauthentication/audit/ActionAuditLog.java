package com.ipocalc.accountauthentication.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 액션 감사 로그 엔티티. */
@Entity
@Table(
    name = "action_audit_logs",
    indexes = {
      @Index(name = "idx_audit_actor_time", columnList = "actor_admin_id, created_at"),
      @Index(name = "idx_audit_subject_time", columnList = "subject_account_id, created_at"),
      @Index(name = "idx_audit_action_time", columnList = "action, created_at")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ActionAuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_admin_id", nullable = false)
  private Long actorAdminId;

  @Column(name = "subject_account_id")
  private Long subjectAccountId;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  @Column(name = "reason", length = 50)
  private String reason;

  @Column(name = "impersonation_session_id", length = 36)
  private String impersonationSessionId;

  @Column(name = "detail", length = 500)
  private String detail;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /**
   * 임퍼소네이션 시작 감사 로그를 생성한다.
   *
   * @param adminId 행위자 관리자 ID
   * @param targetId 대상 회원 ID
   * @param sessionId 임퍼소네이션 세션 ID
   * @param detail 상세 정보
   * @param clock 시간 기준
   * @return 감사 로그
   */
  public static ActionAuditLog impersonationStart(
      Long adminId, Long targetId, String sessionId, String detail, Clock clock) {
    return ActionAuditLog.builder()
        .actorAdminId(adminId)
        .subjectAccountId(targetId)
        .impersonationSessionId(sessionId)
        .action("IMPERSONATION_START")
        .detail(detail)
        .createdAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * 임퍼소네이션 종료 감사 로그를 생성한다.
   *
   * @param adminId 행위자 관리자 ID
   * @param targetId 대상 회원 ID
   * @param sessionId 임퍼소네이션 세션 ID
   * @param reason 종료 사유
   * @param clock 시간 기준
   * @return 감사 로그
   */
  public static ActionAuditLog impersonationEnd(
      Long adminId, Long targetId, String sessionId, String reason, Clock clock) {
    return ActionAuditLog.builder()
        .actorAdminId(adminId)
        .subjectAccountId(targetId)
        .impersonationSessionId(sessionId)
        .action("IMPERSONATION_END")
        .reason(reason)
        .createdAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * 임퍼소네이션 만료 감사 로그를 생성한다.
   *
   * @param adminId 행위자 관리자 ID
   * @param targetId 대상 회원 ID
   * @param sessionId 임퍼소네이션 세션 ID
   * @param clock 시간 기준
   * @return 감사 로그
   */
  public static ActionAuditLog impersonationExpired(
      Long adminId, Long targetId, String sessionId, Clock clock) {
    return ActionAuditLog.builder()
        .actorAdminId(adminId)
        .subjectAccountId(targetId)
        .impersonationSessionId(sessionId)
        .action("IMPERSONATION_EXPIRED")
        .detail("sessionId=" + sessionId)
        .createdAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * 계정 변경 감사 로그를 생성한다.
   *
   * @param adminId 행위자 관리자 ID
   * @param targetId 대상 회원 ID
   * @param action 수행 작업
   * @param detail 상세 정보
   * @param clock 시간 기준
   * @return 감사 로그
   */
  public static ActionAuditLog accountAction(
      Long adminId, Long targetId, String action, String detail, Clock clock) {
    return ActionAuditLog.builder()
        .actorAdminId(adminId)
        .subjectAccountId(targetId)
        .action(action)
        .detail(detail)
        .createdAt(LocalDateTime.now(clock))
        .build();
  }
}
