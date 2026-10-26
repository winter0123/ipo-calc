package com.ipocalc.subscriptionmanagement.repository.subscription;

import static com.ipocalc.investormanagement.domain.model.investor.QInvestor.investor;
import static com.ipocalc.subscriptionmanagement.domain.model.subscription.QSubscription.subscription;

import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** 청약 도메인의 조인·집계 QueryDSL 구현. */
@Repository
public class SubscriptionQueryRepositoryImpl implements SubscriptionQueryRepository {
  private final JPAQueryFactory queryFactory;
  private final ActiveAllocationReader activeAllocationReader;

  /** QueryDSL 조회 팩토리를 주입한다. */
  public SubscriptionQueryRepositoryImpl(
      JPAQueryFactory queryFactory, ActiveAllocationReader activeAllocationReader) {
    this.queryFactory = queryFactory;
    this.activeAllocationReader = activeAllocationReader;
  }

  @Override
  public Optional<Subscription> findByIdForUpdate(Long id) {
    return Optional.ofNullable(
        queryFactory
            .selectFrom(subscription)
            .where(subscription.id.eq(id))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  @Override
  public boolean hasActiveAllocation(Long subscriptionId) {
    return activeAllocationReader.hasActiveAllocation(subscriptionId);
  }

  @Override
  public long activeAllocatedQuantity(Long subscriptionId) {
    return activeAllocationReader.activeAllocatedQuantity(subscriptionId);
  }

  @Override
  public List<Subscription> findAllByAccountWithFilters(
      Long accountId, Long investorId, Long securitiesAccountId) {
    BooleanBuilder predicate =
        new BooleanBuilder()
            .and(investor.accountId.eq(accountId))
            .and(subscription.deletedAt.isNull());
    if (investorId != null) {
      predicate.and(investor.id.eq(investorId));
    }
    if (securitiesAccountId != null) {
      predicate.and(subscription.securitiesAccountId.eq(securitiesAccountId));
    }
    return queryFactory
        .selectFrom(subscription)
        .join(investor)
        .on(subscription.investorId.eq(investor.id))
        .where(predicate)
        .orderBy(subscription.createdAt.desc())
        .fetch();
  }

  @Override
  public boolean existsByIpoIdAndInvestorAccountId(Long ipoId, Long currentAccountId) {
    return queryFactory
            .selectOne()
            .from(subscription)
            .join(investor)
            .on(subscription.investorId.eq(investor.id))
            .where(
                subscription.ipoId.eq(ipoId),
                investor.accountId.eq(currentAccountId),
                subscription.deletedAt.isNull())
            .fetchFirst()
        != null;
  }

  @Override
  public Page<Subscription> findByInvestorAccountId(Long currentAccountId, Pageable pageable) {
    BooleanBuilder predicate =
        new BooleanBuilder()
            .and(investor.accountId.eq(currentAccountId))
            .and(subscription.deletedAt.isNull());
    List<Subscription> content =
        queryFactory
            .selectFrom(subscription)
            .join(investor)
            .on(subscription.investorId.eq(investor.id))
            .where(predicate)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    Long total =
        queryFactory
            .select(subscription.count())
            .from(subscription)
            .join(investor)
            .on(subscription.investorId.eq(investor.id))
            .where(predicate)
            .fetchOne();
    return new PageImpl<>(content, pageable, total == null ? 0 : total);
  }

  @Override
  public Page<Subscription> findByInvestorAccountIdWithFilters(
      Long currentAccountId,
      Long investorId,
      Long securitiesAccountId,
      Long ipoId,
      Pageable pageable) {
    BooleanBuilder predicate =
        new BooleanBuilder()
            .and(investor.accountId.eq(currentAccountId))
            .and(subscription.deletedAt.isNull());
    if (investorId != null) {
      predicate.and(investor.id.eq(investorId));
    }
    if (securitiesAccountId != null) {
      predicate.and(subscription.securitiesAccountId.eq(securitiesAccountId));
    }
    if (ipoId != null) {
      predicate.and(subscription.ipoId.eq(ipoId));
    }
    List<Subscription> content =
        queryFactory
            .selectFrom(subscription)
            .join(investor)
            .on(subscription.investorId.eq(investor.id))
            .where(predicate)
            .orderBy(subscription.subscribedAt.desc(), subscription.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    Long total =
        queryFactory
            .select(subscription.count())
            .from(subscription)
            .join(investor)
            .on(subscription.investorId.eq(investor.id))
            .where(predicate)
            .fetchOne();
    return new PageImpl<>(content, pageable, total == null ? 0 : total);
  }
}
