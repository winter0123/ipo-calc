package com.ipocalc.brokerintegration.repository.credential;

import static com.ipocalc.brokerintegration.domain.model.credential.QSecuritiesAccountApiCredential.securitiesAccountApiCredential;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** QueryDSL DML 기반 브로커 토큰 조건부 갱신 구현. */
@Repository
public class CredentialTokenMutationRepositoryImpl implements CredentialTokenMutationRepository {
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  /** QueryDSL 팩토리와 영속성 컨텍스트를 주입한다. */
  public CredentialTokenMutationRepositoryImpl(
      JPAQueryFactory queryFactory, EntityManager entityManager) {
    this.queryFactory = queryFactory;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional
  @SuppressWarnings("checkstyle:LineLength")
  public int updateTokenIfKeyUnchanged(
      Long id,
      String appKeyEncrypted,
      String tokenEncrypted,
      LocalDateTime expiresAt,
      LocalDateTime now) {
    entityManager.flush();
    int updated =
        Math.toIntExact(
            queryFactory
                .update(securitiesAccountApiCredential)
                .set(securitiesAccountApiCredential.accessTokenEncrypted, tokenEncrypted)
                .set(securitiesAccountApiCredential.tokenExpiresAt, expiresAt)
                .set(securitiesAccountApiCredential.updatedAt, now)
                .where(
                    securitiesAccountApiCredential.id.eq(id),
                    securitiesAccountApiCredential.appKeyEncrypted.eq(appKeyEncrypted),
                    securitiesAccountApiCredential.deletedAt.isNull())
                .execute());
    entityManager.clear();
    return updated;
  }

  @Override
  @Transactional
  public void deleteBySecuritiesAccountId(Long securitiesAccountId) {
    entityManager.flush();
    queryFactory
        .delete(securitiesAccountApiCredential)
        .where(securitiesAccountApiCredential.securitiesAccountId.eq(securitiesAccountId))
        .execute();
    entityManager.clear();
  }
}
