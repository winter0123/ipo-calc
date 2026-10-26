package com.ipocalc.allocationmanagement.repository.allocation;

import static com.ipocalc.allocationmanagement.domain.model.allocation.QAllocation.allocation;
import static com.ipocalc.investormanagement.domain.model.investor.QInvestor.investor;
import static com.ipocalc.subscriptionmanagement.domain.model.subscription.QSubscription.subscription;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 배정 도메인의 조인·집계 QueryDSL 구현. */
@Repository
public class AllocationQueryRepositoryImpl implements AllocationQueryRepository {
  private final JPAQueryFactory queryFactory;

  /** QueryDSL 조회 팩토리를 주입한다. */
  public AllocationQueryRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public boolean hasActiveAllocation(Long subscriptionId) {
    return queryFactory
            .selectOne()
            .from(allocation)
            .where(
                allocation.subscriptionId.eq(subscriptionId),
                allocation.deletedAt.isNull(),
                allocation.status.eq(RecordStatus.ACTIVE))
            .fetchFirst()
        != null;
  }

  @Override
  public long activeAllocatedQuantity(Long subscriptionId) {
    Long quantity =
        queryFactory
            .select(allocation.allocatedQuantity.max())
            .from(allocation)
            .where(
                allocation.subscriptionId.eq(subscriptionId),
                allocation.deletedAt.isNull(),
                allocation.status.eq(RecordStatus.ACTIVE))
            .fetchOne();
    return quantity == null ? 0 : quantity;
  }

  @Override
  public Optional<Allocation> findByIdForUpdate(Long id) {
    return Optional.ofNullable(
        queryFactory
            .selectFrom(allocation)
            .where(allocation.id.eq(id))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetchOne());
  }

  @Override
  public List<Allocation> findAllByAccountWithFilters(
      Long accountId, Long subscriptionId, Long investorId) {
    BooleanBuilder predicate =
        new BooleanBuilder()
            .and(allocation.deletedAt.isNull())
            .and(investor.accountId.eq(accountId));
    if (subscriptionId != null) {
      predicate.and(allocation.subscriptionId.eq(subscriptionId));
    }
    if (investorId != null) {
      predicate.and(investor.id.eq(investorId));
    }
    return queryFactory
        .selectFrom(allocation)
        .join(subscription)
        .on(allocation.subscriptionId.eq(subscription.id))
        .join(investor)
        .on(subscription.investorId.eq(investor.id))
        .where(predicate)
        .orderBy(allocation.createdAt.desc())
        .fetch();
  }
}
