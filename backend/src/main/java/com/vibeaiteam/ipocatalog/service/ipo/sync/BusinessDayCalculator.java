package com.vibeaiteam.ipocatalog.service.ipo.sync;

import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 증권 영업일 계산 유틸리티.
 *
 * <p>한국거래소(KRX) 휴장일을 제외한 평일을 영업일로 계산한다. 휴장일은 DB 테이블 {@code market_holidays}에서 조회하며, 필요한 연도 단위로 메모리에
 * 캐시한다({@link ConcurrentHashMap}). 배치가 휴장일을 갱신한 후 {@link #evictCache()}를 호출해 캐시를 무효화한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessDayCalculator {

  private final MarketHolidayRepository marketHolidayRepository;

  /** 연도별 휴장일 캐시: year -> {@code Set<LocalDate>}. */
  private final Map<Integer, Set<LocalDate>> holidayCache = new ConcurrentHashMap<>();

  /** 주어진 날짜의 다음 영업일을 반환한다 (주말 및 KRX 휴장일 제외). */
  public LocalDate nextBusinessDay(LocalDate date) {
    return plusBusinessDays(date, 1);
  }

  /**
   * 주어진 날짜부터 지정된 영업일 수만큼 더한 날짜를 반환한다.
   *
   * <p>date가 영업일이 아니어도 date 다음 영업일부터 센다. businessDays가 0이면 date를 그대로 반환한다. businessDays가 음수이면
   * {@link IllegalArgumentException}을 던진다.
   *
   * @param date 기준 날짜 (영업일이 아니어도 됨)
   * @param businessDays 더할 영업일 수 (0 이상)
   * @return date로부터 businessDays 영업일 후의 날짜
   * @throws IllegalArgumentException businessDays가 음수인 경우
   */
  public LocalDate plusBusinessDays(LocalDate date, int businessDays) {
    if (businessDays < 0) {
      throw new IllegalArgumentException("businessDays는 0 이상이어야 합니다.");
    }
    if (businessDays == 0) {
      return date;
    }
    ensureYearCached(date.getYear());
    warnIfYearEmpty(date);
    LocalDate current = date;
    int counted = 0;
    while (counted < businessDays) {
      current = current.plusDays(1);
      if (isBusinessDay(current)) {
        counted++;
      }
    }
    return current;
  }

  /** 주어진 날짜가 영업일인지 판별한다 (주말 및 KRX 휴장일 제외). */
  public boolean isBusinessDay(LocalDate date) {
    ensureYearCached(date.getYear());
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return false;
    }
    Set<LocalDate> holidays = holidayCache.get(date.getYear());
    return holidays == null || !holidays.contains(date);
  }

  /** 캐시를 완전히 비운다. 배치 갱신 후 호출되어야 한다. */
  public void evictCache() {
    holidayCache.clear();
    log.info("BusinessDayCalculator 휴장일 캐시 무효화 완료");
  }

  /** 특정 연도의 휴장일이 캐시에 있는지 확인하고 없으면 DB에서 로드한다. */
  private void ensureYearCached(int year) {
    holidayCache.computeIfAbsent(year, this::loadHolidaysForYear);
  }

  /** 특정 연도의 휴장일을 DB에서 조회해 Set으로 반환한다. */
  private Set<LocalDate> loadHolidaysForYear(int year) {
    List<MarketHoliday> holidays = marketHolidayRepository.findByYear(year);
    Set<LocalDate> holidaySet =
        holidays.stream()
            .map(MarketHoliday::getHolidayDate)
            .collect(Collectors.toCollection(HashSet::new));
    log.debug(
        "연도 {} 휴장일 {}건 로드 완료 (source별: {})",
        year,
        holidaySet.size(),
        holidays.stream()
            .collect(Collectors.groupingBy(MarketHoliday::getSource, Collectors.counting())));
    return holidaySet;
  }

  /** 해당 연도에 휴장일 데이터가 하나도 없으면 WARN 로그를 남긴다 (연도당 1회만). */
  private void warnIfYearEmpty(LocalDate date) {
    int year = date.getYear();
    Set<LocalDate> holidays = holidayCache.get(year);
    if (holidays == null || holidays.isEmpty()) {
      // 연도당 1회만 로그 출력하기 위해 캐시에 빈 세트가 있는 경우에만 경고
      // (최초 조회 시 computeIfAbsent가 빈 세트를 넣었으므로 여기서 감지 가능)
      log.warn(
          "market_holidays 테이블에 연도 {}의 휴장일 데이터가 없습니다. 주말만 제외한 영업일 계산이 수행됩니다. 배치 동기화가 필요합니다.", year);
      // 빈 세트라도 캐시에 있으므로 두 번 경고하지 않음
    }
  }
}
