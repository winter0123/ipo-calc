package com.vibeaiteam.ipocatalog.repository.holiday;

import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 시장 휴장일 레포지토리.
 *
 * <p>연도 범위 조회와 특정 날짜 존재 여부 확인 기능을 제공한다.
 */
@Repository
public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, Long> {

  /** 특정 연도의 모든 휴장일 조회. */
  @Query("SELECT m FROM MarketHoliday m WHERE YEAR(m.holidayDate) = :year ORDER BY m.holidayDate")
  List<MarketHoliday> findByYear(@Param("year") int year);

  /** 여러 연도의 휴장일 일괄 조회. */
  @Query("SELECT m FROM MarketHoliday m WHERE YEAR(m.holidayDate) IN :years ORDER BY m.holidayDate")
  List<MarketHoliday> findByYears(@Param("years") List<Integer> years);

  /** 특정 날짜가 휴장일인지 확인. */
  Optional<MarketHoliday> findByHolidayDate(LocalDate holidayDate);

  /** 특정 날짜가 휴장일인지 존재 여부만 확인. */
  boolean existsByHolidayDate(LocalDate holidayDate);

  /** SEED 소스 휴장일 개수 조회 (검증용). */
  @Query("SELECT COUNT(m) FROM MarketHoliday m WHERE m.source = 'SEED'")
  long countBySourceSeed();

  /** 특정 연도의 휴장일 개수 조회. */
  @Query("SELECT COUNT(m) FROM MarketHoliday m WHERE YEAR(m.holidayDate) = :year")
  long countByYear(@Param("year") int year);

  /** 소스별로 휴장일 삭제 (테스트 정리용). */
  @Modifying
  @Query("DELETE FROM MarketHoliday m WHERE m.source IN :sources")
  void deleteBySourceIn(@Param("sources") List<String> sources);
}
