package com.ipocalc.ipocatalog.repository.holiday;

import com.ipocalc.ipocatalog.domain.model.holiday.MarketHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 시장 휴장일 레포지토리.
 *
 * <p>연도 범위 조회와 특정 날짜 존재 여부 확인 기능을 제공한다.
 */
@Repository
public interface MarketHolidayRepository
    extends JpaRepository<MarketHoliday, Long>, MarketHolidayQueryRepository {

  /** 특정 날짜가 휴장일인지 확인. */
  Optional<MarketHoliday> findByHolidayDate(LocalDate holidayDate);

  /** 특정 날짜가 휴장일인지 존재 여부만 확인. */
  boolean existsByHolidayDate(LocalDate holidayDate);

  /** SEED 소스 휴장일 개수 조회 (검증용). */
  long countBySource(String source);

  /** SEED 출처 휴장일 개수를 조회한다. */
  default long countBySourceSeed() {
    return countBySource("SEED");
  }

  /** 소스별로 휴장일 삭제 (테스트 정리용). */
  void deleteBySourceIn(List<String> sources);
}
