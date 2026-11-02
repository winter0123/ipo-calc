package com.ipocalc.accountauthentication.service.impersonation;

import com.ipocalc.accountauthentication.audit.AuditLogService;
import com.ipocalc.accountauthentication.config.security.SpringSessionMarkerCleaner;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.domain.model.impersonation.ImpersonationSession;
import com.ipocalc.accountauthentication.exception.AccountNotFoundException;
import com.ipocalc.accountauthentication.exception.ValidationFailedException;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.accountauthentication.repository.impersonation.ImpersonationSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 임퍼소네이션 세션 관리 서비스. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImpersonationService {

  /** HTTP 세션에 저장되는 임퍼소네이션 세션 ID 속성명. */
  public static final String SESSION_ATTRIBUTE = "IMPERSONATION_SESSION_ID";

  /** 임퍼소네이션 세션 최대 유지 시간 (30분). */
  private static final long IMPERSONATION_TIMEOUT_MINUTES = 30;

  private final ImpersonationSessionRepository impersonationRepository;
  private final AccountRepository accountRepository;
  private final AuditLogService auditLogService;
  private final Clock clock;
  private final EntityManager entityManager;
  private final SpringSessionMarkerCleaner springSessionMarkerCleaner;

  /**
   * 임퍼소네이션 시작.
   *
   * @param adminId 관리자 계정 ID
   * @param targetAccountId 대상 회원 계정 ID
   * @param httpSession HTTP 세션 (null 불가)
   * @return 생성된 임퍼소네이션 세션 정보
   */
  @Transactional
  public ImpersonationSession startImpersonation(
      Long adminId, Long targetAccountId, HttpSession httpSession) {
    if (adminId.equals(targetAccountId)) {
      throw new ValidationFailedException("accountId", "자기 자신은 임퍼소네이션할 수 없습니다");
    }

    Account actor =
        accountRepository
            .findByIdAndApprovedTrue(adminId)
            .orElseThrow(() -> new AccountNotFoundException("관리자 계정을 찾을 수 없습니다"));
    if (actor.getRole() != AccountRole.ADMIN) {
      throw new ValidationFailedException("accountId", "관리자만 임퍼소네이션할 수 있습니다");
    }

    Account target =
        accountRepository
            .findByIdAndApprovedTrue(targetAccountId)
            .orElseThrow(() -> new AccountNotFoundException("대상 회원을 찾을 수 없습니다"));

    endCurrentImpersonation(adminId, "REPLACED");
    entityManager.flush();

    LocalDateTime now = LocalDateTime.now(clock);
    String sessionId = UUID.randomUUID().toString();
    var session =
        ImpersonationSession.builder()
            .actorAdminId(adminId)
            .subjectAccountId(targetAccountId)
            .sessionId(sessionId)
            .springSessionId(httpSession == null ? null : httpSession.getId())
            .startedAt(now)
            .expiresAt(now.plusMinutes(IMPERSONATION_TIMEOUT_MINUTES))
            .readOnly(true)
            .build();

    var savedSession = impersonationRepository.save(session);

    if (httpSession != null) {
      httpSession.setAttribute(SESSION_ATTRIBUTE, savedSession.getSessionId());
    }

    auditLogService.logImpersonationStart(adminId, targetAccountId, sessionId, "started");

    log.debug("임퍼소네이션 시작: adminId={}, targetAccountId={}", adminId, targetAccountId);

    return savedSession;
  }

  /**
   * 임퍼소네이션 상태 조회.
   *
   * @param request HTTP 요청
   * @return 임퍼소네이션 상태 응답
   */
  @Transactional(readOnly = true)
  public com.ipocalc.accountauthentication.controller.dto.ImpersonationStatusResponse getStatus(
      jakarta.servlet.http.HttpServletRequest request) {
    jakarta.servlet.http.HttpSession session = request.getSession(false);
    String sessionId = session != null ? (String) session.getAttribute(SESSION_ATTRIBUTE) : null;

    if (sessionId == null) {
      return new com.ipocalc.accountauthentication.controller.dto.ImpersonationStatusResponse(
          false, null, null);
    }

    var sessionOpt = findActiveSessionById(sessionId);
    if (sessionOpt.isEmpty()) {
      if (session != null) {
        session.removeAttribute(SESSION_ATTRIBUTE);
      }
      return new com.ipocalc.accountauthentication.controller.dto.ImpersonationStatusResponse(
          false, null, null);
    }

    var impersonationSession = sessionOpt.get();

    if (impersonationSession.isExpired(clock)) {
      endImpersonation(sessionId, "EXPIRED");
      if (session != null) {
        session.removeAttribute(SESSION_ATTRIBUTE);
      }
      return new com.ipocalc.accountauthentication.controller.dto.ImpersonationStatusResponse(
          false, null, null);
    }

    return new com.ipocalc.accountauthentication.controller.dto.ImpersonationStatusResponse(
        true, impersonationSession.getSubjectAccountId(), impersonationSession.getExpiresAt());
  }

  /** 임퍼소네이션 종료 (세션 ID 기반, 멱등). */
  @Transactional
  public void endImpersonation(String sessionId, String reason) {
    Optional<ImpersonationSession> found = impersonationRepository.findBySessionId(sessionId);
    if (found.isPresent()) {
      ImpersonationSession session = found.get();
      if (session.getEndedAt() == null) {
        LocalDateTime now = LocalDateTime.now(clock);
        session.end(reason, now);
        impersonationRepository.save(session);
        springSessionMarkerCleaner.removeMarker(session.getSpringSessionId());
        auditLogService.logImpersonationEnd(
            session.getActorAdminId(), session.getSubjectAccountId(), sessionId, reason);
        log.debug("임퍼소네이션 종료: sessionId={}, reason={}", sessionId, reason);
      }
    }
  }

  /** 현재 관리자의 활성(종료되지 않은) 임퍼소네이션 종료. */
  @Transactional
  public void endCurrentImpersonation(Long adminId, String reason) {
    Optional<ImpersonationSession> active =
        impersonationRepository.findOpenByActorAdminIdForUpdate(adminId);
    active.ifPresent(
        session -> {
          LocalDateTime now = LocalDateTime.now(clock);
          session.end(reason, now);
          impersonationRepository.save(session);
          springSessionMarkerCleaner.removeMarker(session.getSpringSessionId());
          auditLogService.logImpersonationEnd(
              session.getActorAdminId(),
              session.getSubjectAccountId(),
              session.getSessionId(),
              reason);
          log.debug(
              "임퍼소네이션 교체/종료: adminId={}, sessionId={}, reason={}",
              adminId,
              session.getSessionId(),
              reason);
        });
  }

  /** 세션 ID로 활성(종료·만료되지 않은) 임퍼소네이션 세션 조회. */
  public Optional<ImpersonationSession> findActiveSessionById(String sessionId) {
    return impersonationRepository.findActiveBySessionId(sessionId, LocalDateTime.now(clock));
  }

  /** 세션 ID로 임퍼소네이션 세션 조회 (종료 여부 무관). */
  public Optional<ImpersonationSession> findSessionById(String sessionId) {
    return impersonationRepository.findBySessionId(sessionId);
  }

  /** 만료된 임퍼소네이션 세션 정리 (스케줄러용, 1분마다 실행). */
  @Scheduled(fixedRate = 60_000)
  @Transactional
  public void expireSessions() {
    LocalDateTime now = LocalDateTime.now(clock);
    List<ImpersonationSession> expired = impersonationRepository.findExpiredOpen(now);

    for (ImpersonationSession session : expired) {
      session.end("EXPIRED", now);
      impersonationRepository.save(session);
      springSessionMarkerCleaner.removeMarker(session.getSpringSessionId());

      auditLogService.logImpersonationExpired(
          session.getActorAdminId(), session.getSubjectAccountId(), session.getSessionId());

      log.debug(
          "임퍼소네이션 만료: adminId={}, subjectId={}",
          session.getActorAdminId(),
          session.getSubjectAccountId());
    }
  }
}
