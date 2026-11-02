package com.ipocalc.accountauthentication.audit;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 감사 로그 서비스. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

  private final ActionAuditLogRepository auditLogRepository;
  private final Clock clock;

  /** 감사 로그를 기록한다. */
  @Transactional
  public void log(Long actorAdminId, String action, String detail) {
    log(actorAdminId, action, null, null, detail);
  }

  /** 감사 로그를 기록한다 (대상 계정 ID 포함). */
  @Transactional
  public void log(Long actorAdminId, String action, Long subjectAccountId, String detail) {
    log(actorAdminId, action, subjectAccountId, null, detail);
  }

  /** 감사 로그를 기록한다 (임퍼소네이션 세션 ID 포함). */
  @Transactional
  public void log(
      Long actorAdminId,
      String action,
      Long subjectAccountId,
      String impersonationSessionId,
      String detail) {
    var auditLog =
        ActionAuditLog.builder()
            .actorAdminId(actorAdminId)
            .subjectAccountId(subjectAccountId)
            .impersonationSessionId(impersonationSessionId)
            .action(action)
            .detail(detail)
            .createdAt(LocalDateTime.now(clock))
            .build();
    auditLogRepository.save(auditLog);
  }

  /** 임퍼소네이션 시작 로그 기록. */
  @Transactional
  public void logImpersonationStart(
      Long adminId, Long targetAccountId, String sessionId, String detail) {
    auditLogRepository.save(
        ActionAuditLog.impersonationStart(adminId, targetAccountId, sessionId, detail, clock));
  }

  /** 임퍼소네이션 종료 로그 기록. */
  @Transactional
  public void logImpersonationEnd(
      Long adminId, Long targetAccountId, String sessionId, String reason) {
    auditLogRepository.save(
        ActionAuditLog.impersonationEnd(adminId, targetAccountId, sessionId, reason, clock));
  }

  /** 임퍼소네이션 만료 로그 기록. */
  @Transactional
  public void logImpersonationExpired(Long adminId, Long targetAccountId, String sessionId) {
    auditLogRepository.save(
        ActionAuditLog.impersonationExpired(adminId, targetAccountId, sessionId, clock));
  }
}
