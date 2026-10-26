package com.ipocalc.ipocatalog.repository.holiday;

import com.ipocalc.ipocatalog.domain.model.holiday.MarketHoliday;
import java.util.List;

/** 연도 조건이 필요한 휴장일 조회 저장소. */
public interface MarketHolidayQueryRepository {
  /** 특정 연도의 휴장일을 날짜순으로 조회한다. */
  List<MarketHoliday> findByYear(int year);

  /** 여러 연도의 휴장일을 날짜순으로 조회한다. */
  List<MarketHoliday> findByYears(List<Integer> years);

  /** 특정 연도의 휴장일 개수를 조회한다. */
  long countByYear(int year);
}
