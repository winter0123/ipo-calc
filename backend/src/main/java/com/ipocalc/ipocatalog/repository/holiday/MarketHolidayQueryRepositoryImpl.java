package com.ipocalc.ipocatalog.repository.holiday;

import static com.ipocalc.ipocatalog.domain.model.holiday.QMarketHoliday.marketHoliday;

import com.ipocalc.ipocatalog.domain.model.holiday.MarketHoliday;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** QueryDSL 기반 연도별 휴장일 조회 구현. */
@Repository
public class MarketHolidayQueryRepositoryImpl implements MarketHolidayQueryRepository {
  private final JPAQueryFactory queryFactory;

  /** QueryDSL 조회 팩토리를 주입한다. */
  public MarketHolidayQueryRepositoryImpl(JPAQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Override
  public List<MarketHoliday> findByYear(int year) {
    return queryFactory
        .selectFrom(marketHoliday)
        .where(yearExpression().eq(year))
        .orderBy(marketHoliday.holidayDate.asc())
        .fetch();
  }

  @Override
  public List<MarketHoliday> findByYears(List<Integer> years) {
    if (years.isEmpty()) {
      return List.of();
    }
    return queryFactory
        .selectFrom(marketHoliday)
        .where(yearExpression().in(years))
        .orderBy(marketHoliday.holidayDate.asc())
        .fetch();
  }

  @Override
  public long countByYear(int year) {
    Long count =
        queryFactory
            .select(marketHoliday.count())
            .from(marketHoliday)
            .where(yearExpression().eq(year))
            .fetchOne();
    return count == null ? 0 : count;
  }

  private NumberExpression<Integer> yearExpression() {
    return marketHoliday.holidayDate.year();
  }
}
