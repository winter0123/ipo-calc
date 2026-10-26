package com.ipocalc.accountauthentication.repository.account;

import static com.ipocalc.accountauthentication.domain.model.account.QAccount.account;

import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** QueryDSL DML 기반 계정 로그인 상태 갱신 구현. */
@Repository
public class AccountMutationRepositoryImpl implements AccountMutationRepository {
  private final JPAQueryFactory queryFactory;

  /** QueryDSL 갱신 팩토리를 주입한다. */
  public AccountMutationRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  @Transactional
  public int recordFailedLogin(
      Long id, LocalDateTime now, int maxAttempts, LocalDateTime lockUntil) {
    var expired = account.lockedUntil.isNotNull().and(account.lockedUntil.loe(now));
    var nextAttempts =
        new CaseBuilder().when(expired).then(1).otherwise(account.failedLoginAttempts.add(1));
    var nextLockedUntil =
        new CaseBuilder()
            .when(nextAttempts.goe(maxAttempts))
            .then(Expressions.asDateTime(lockUntil))
            .when(expired)
            .then(Expressions.nullExpression(LocalDateTime.class))
            .otherwise(account.lockedUntil);
    return Math.toIntExact(
        queryFactory
            .update(account)
            .set(account.failedLoginAttempts, nextAttempts)
            .set(account.lockedUntil, nextLockedUntil)
            .set(account.updatedAt, now)
            .where(account.id.eq(id))
            .execute());
  }

  @Override
  @Transactional
  public int recordLoginSuccess(Long id, LocalDateTime now) {
    return Math.toIntExact(
        queryFactory
            .update(account)
            .set(account.failedLoginAttempts, 0)
            .set(account.lockedUntil, (LocalDateTime) null)
            .set(account.lastLoginAt, now)
            .set(account.updatedAt, now)
            .where(account.id.eq(id))
            .execute());
  }
}
