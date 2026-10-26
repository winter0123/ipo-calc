package com.ipocalc.salemanagement.repository.sale;

import static com.ipocalc.allocationmanagement.domain.model.allocation.QAllocation.allocation;
import static com.ipocalc.investormanagement.domain.model.investor.QInvestor.investor;
import static com.ipocalc.salemanagement.domain.model.sale.QSale.sale;
import static com.ipocalc.subscriptionmanagement.domain.model.subscription.QSubscription.subscription;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 매도 도메인의 조인·집계 QueryDSL 구현. */
@Repository
public class SaleQueryRepositoryImpl implements SaleQueryRepository {
  private final JPAQueryFactory queryFactory;

  /** QueryDSL 조회 팩토리를 주입한다. */
  public SaleQueryRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public long sumActiveSaleQuantity(Long allocationId) {
    Long quantity =
        queryFactory
            .select(sale.quantity.sumLong())
            .from(sale)
            .where(
                sale.allocationId.eq(allocationId),
                sale.deletedAt.isNull(),
                sale.status.eq(RecordStatus.ACTIVE))
            .fetchOne();
    return quantity == null ? 0 : quantity;
  }

  @Override
  public Long sumActiveQuantityByAllocationId(Long allocationId) {
    return activeQuantityQuery(allocationId).fetchOne();
  }

  @Override
  public Long sumActiveQuantityByAllocationIdExcludingId(Long allocationId, Long excludeId) {
    return activeQuantityQuery(allocationId).where(sale.id.ne(excludeId)).fetchOne();
  }

  private JPAQuery<Long> activeQuantityQuery(Long allocationId) {
    return queryFactory
        .select(sale.quantity.sumLong())
        .from(sale)
        .where(
            sale.allocationId.eq(allocationId),
            sale.deletedAt.isNull(),
            sale.status.eq(RecordStatus.ACTIVE));
  }

  @Override
  public List<Sale> findAllByStatusAndInvestorAccountId(RecordStatus status, Long accountId) {
    return queryFactory
        .selectFrom(sale)
        .join(allocation)
        .on(sale.allocationId.eq(allocation.id))
        .join(subscription)
        .on(allocation.subscriptionId.eq(subscription.id))
        .join(investor)
        .on(subscription.investorId.eq(investor.id))
        .where(sale.deletedAt.isNull(), sale.status.eq(status), investor.accountId.eq(accountId))
        .fetch();
  }

  @Override
  public List<Sale> findActiveWithFilters(
      Long accountId, Long allocationId, Long investorId, String investorName) {
    BooleanBuilder predicate =
        new BooleanBuilder()
            .and(sale.deletedAt.isNull())
            .and(sale.status.eq(RecordStatus.ACTIVE))
            .and(investor.accountId.eq(accountId));
    if (allocationId != null) {
      predicate.and(sale.allocationId.eq(allocationId));
    }
    if (investorId != null) {
      predicate.and(investor.id.eq(investorId));
    }
    if (investorName != null) {
      predicate.and(investor.displayName.eq(investorName));
    }
    return queryFactory
        .selectFrom(sale)
        .join(allocation)
        .on(sale.allocationId.eq(allocation.id))
        .join(subscription)
        .on(allocation.subscriptionId.eq(subscription.id))
        .join(investor)
        .on(subscription.investorId.eq(investor.id))
        .where(predicate)
        .orderBy(sale.saleDate.desc(), sale.createdAt.desc())
        .fetch();
  }
}
