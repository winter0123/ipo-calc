package com.ipocalc.announcements.repository;

import static com.ipocalc.accountauthentication.domain.model.account.QAccount.account;
import static com.ipocalc.announcements.domain.QAnnouncementDismissal.announcementDismissal;

import com.ipocalc.announcements.domain.AnnouncementDismissal;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 계정 행 잠금과 JPA insert로 공지 숨김 중복 요청을 직렬화한다. */
@Repository
public class AnnouncementDismissalMutationRepositoryImpl
    implements AnnouncementDismissalMutationRepository {
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  /** QueryDSL 팩토리와 영속성 컨텍스트를 주입한다. */
  public AnnouncementDismissalMutationRepositoryImpl(
      JPAQueryFactory queryFactory, EntityManager entityManager) {
    this.queryFactory = queryFactory;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional
  public int insertIfAbsent(Long accountId, Long announcementId, LocalDateTime dismissedAt) {
    queryFactory
        .select(account.id)
        .from(account)
        .where(account.id.eq(accountId))
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .fetchOne();
    Long existingId =
        queryFactory
            .select(announcementDismissal.announcementId)
            .from(announcementDismissal)
            .where(
                announcementDismissal.accountId.eq(accountId),
                announcementDismissal.announcementId.eq(announcementId))
            .fetchOne();
    if (existingId != null) {
      return 0;
    }
    entityManager.persist(new AnnouncementDismissal(accountId, announcementId, dismissedAt));
    return 1;
  }
}
