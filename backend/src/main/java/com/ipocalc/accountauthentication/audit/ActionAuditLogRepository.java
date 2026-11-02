package com.ipocalc.accountauthentication.audit;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 감사 로그 리포지토리. */
@Repository
public interface ActionAuditLogRepository extends JpaRepository<ActionAuditLog, Long> {

  /** 행위자 ID로 감사 로그를 최신순 조회한다. */
  List<ActionAuditLog> findByActorAdminIdOrderByCreatedAtDesc(Long actorAdminId);

  /** 대상 회원 ID로 감사 로그를 최신순 조회한다. */
  List<ActionAuditLog> findBySubjectAccountIdOrderByCreatedAtDesc(Long subjectAccountId);

  /** 임퍼소네이션 세션 ID로 감사 로그를 조회한다. */
  List<ActionAuditLog> findByImpersonationSessionId(String impersonationSessionId);

  /** 기간으로 감사 로그를 조회한다. */
  List<ActionAuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
