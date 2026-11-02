package com.ipocalc.accountauthentication.repository.impersonation;

import com.ipocalc.accountauthentication.domain.model.impersonation.ImpersonationSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 임퍼소네이션 세션 리포지토리. */
@Repository
public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSession, Long> {

  /** 세션 ID로 활성(종료되지 않고 만료되지 않은) 임퍼소네이션 세션을 조회한다. */
  @Query(
      "SELECT s FROM ImpersonationSession s"
          + " WHERE s.sessionId = :sessionId AND s.endedAt IS NULL AND s.expiresAt > :now")
  Optional<ImpersonationSession> findActiveBySessionId(
      @Param("sessionId") String sessionId, @Param("now") LocalDateTime now);

  /** 세션 ID로 종료 여부와 무관하게 조회한다. */
  Optional<ImpersonationSession> findBySessionId(String sessionId);

  /** 관리자별로 종료되지 않은 세션(만료 포함)을 잠가서 조회한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT s FROM ImpersonationSession s"
          + " WHERE s.actorAdminId = :adminId AND s.endedAt IS NULL")
  Optional<ImpersonationSession> findOpenByActorAdminIdForUpdate(@Param("adminId") Long adminId);

  /** 관리자별 활성(종료·만료되지 않은) 세션을 조회한다. */
  @Query(
      "SELECT s FROM ImpersonationSession s"
          + " WHERE s.actorAdminId = :adminId AND s.endedAt IS NULL AND s.expiresAt > :now")
  Optional<ImpersonationSession> findActiveByActorAdminId(
      @Param("adminId") Long adminId, @Param("now") LocalDateTime now);

  /** 만료됐지만 아직 종료 처리되지 않은 세션을 조회한다(스케줄러용). */
  @Query(
      "SELECT s FROM ImpersonationSession s" + " WHERE s.endedAt IS NULL AND s.expiresAt <= :now")
  List<ImpersonationSession> findExpiredOpen(@Param("now") LocalDateTime now);

  /** 관리자의 모든 임퍼소네이션 세션을 최신순으로 조회한다. */
  @Query(
      "SELECT s FROM ImpersonationSession s WHERE s.actorAdminId = :adminId "
          + "ORDER BY s.startedAt DESC")
  List<ImpersonationSession> findAllByActorAdminId(@Param("adminId") Long adminId);
}
