package com.vibeaiteam.ipocatalog.service.holiday;

import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.BusinessDayCalculator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시장 휴장일 동기화 서비스.
 *
 * <p>한국천문연구원 특일 정보 API에서 법정 공휴일·대체공휴일·임시공휴일·선거일을 조회해 {@code market_holidays} 테이블에 upsert하고, KRX
 * 규칙(근로자의 날, 연말 휴장일)을 보완한다.
 *
 * <ul>
 *   <li>KASI API에서 {@code isHoliday="Y"}인 항목을 {@code source='KASI_API'}로 upsert (이미 있으면 {@code
 *       name}만 갱신, {@code source} 유지)
 *   <li>KRX 규칙: (1) 매년 5월 1일 "근로자의 날", (2) 해당 연도 마지막 평일 "연말 휴장일"을 {@code source='RULE'}로 upsert (이미
 *       있으면 건드리지 않음)
 *   <li>삭제는 절대 하지 않음
 *   <li>한 연도 호출 실패는 ERROR 로그만 남기고 다음 연도로 계속
 *   <li>키가 없으면 WARN 로그만 남기고 KASI 호출 건너뜀 (RULE 보완도 수행하지 않음, 예외 던지지 않음)
 *   <li>완료 후 {@link BusinessDayCalculator#evictCache()} 호출
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketHolidaySyncService {

  private final MarketHolidayRepository marketHolidayRepository;
  private final BusinessDayCalculator businessDayCalculator;
  private final DataGoKrApiProperties dataGoKrApiProperties;
  private final KasiSpecialDayClient kasiClient;
  private final Clock clock;

  /**
   * 지정된 연도들의 휴장일을 동기화한다.
   *
   * @param years 동기화할 연도 목록
   * @return 동기화 결과 요약
   */
  @Transactional
  public SyncResult syncYears(List<Integer> years) {
    SyncResult result = new SyncResult();

    if (years == null || years.isEmpty()) {
      log.warn("동기화할 연도 목록이 비어 있습니다");
      return result;
    }

    // 서비스 키 확인 - 없으면 KASI 호출과 RULE 보완 모두 건너뜀
    if (dataGoKrApiProperties.isServiceKeyMissing()) {
      log.warn("DATA_GO_KR_SERVICE_KEY가 설정되지 않아 KASI API 호출과 KRX 규칙 보완을 건너뜁니다");
      return result;
    }

    for (int year : years) {
      YearSyncResult yearResult = syncYear(year);
      result.addYearResult(year, yearResult);
    }

    // 캐시 무효화
    businessDayCalculator.evictCache();

    log.info("휴장일 동기화 완료: {}", result.summary());
    return result;
  }

  /** 단일 연도의 휴장일을 동기화한다. */
  private YearSyncResult syncYear(int year) {
    YearSyncResult yearResult = new YearSyncResult(year);

    // 1. KASI API 호출
    List<KasiSpecialDayClient.HolidayItem> kasiHolidays = List.of();
    try {
      kasiHolidays = kasiClient.getHolidays(year);
    } catch (KasiSpecialDayClient.KasiApiException e) {
      log.error("KASI API 호출 실패 (연도 {}): {}", year, e.getMessage());
      yearResult.setFailed(true);
      yearResult.setFailureReason(e.getMessage());
      // KASI 실패해도 KRX 규칙(RULE)은 수행
    } catch (IllegalStateException e) {
      // 키 없음 - 상위에서 처리하므로 여기까지 오지 않음
      log.error("서비스 키 없음 (연도 {}): {}", year, e.getMessage());
      yearResult.setFailed(true);
      yearResult.setFailureReason(e.getMessage());
      return yearResult;
    }

    // 2. KASI_API upsert (KASI 호출 성공 시만)
    for (KasiSpecialDayClient.HolidayItem item : kasiHolidays) {
      upsertHoliday(item.date(), item.name(), "KASI_API", yearResult);
    }

    // 3. KRX 규칙 보완 (RULE) - KASI 실패 여부와 관계없이 항상 수행
    // (1) 근로자의 날: 매년 5월 1일
    LocalDate laborDay = LocalDate.of(year, 5, 1);
    upsertHolidayIfAbsent(laborDay, "근로자의 날", "RULE", yearResult);

    // (2) 연말 휴장일: 해당 연도 마지막 평일 (12/31이 평일이면 12/31, 토/일이면 직전 금요일)
    LocalDate yearEnd = calculateYearEndHoliday(year);
    upsertHolidayIfAbsent(yearEnd, "연말 휴장일", "RULE", yearResult);

    return yearResult;
  }

  /**
   * 해당 연도의 마지막 평일을 계산한다.
   *
   * <p>12월 31일이 평일(월~금)이면 12월 31일, 토요일이면 12월 30일, 일요일이면 12월 29일.
   */
  private LocalDate calculateYearEndHoliday(int year) {
    LocalDate dec31 = LocalDate.of(year, 12, 31);
    DayOfWeek dow = dec31.getDayOfWeek();
    if (dow == DayOfWeek.SATURDAY) {
      return dec31.minusDays(1); // 금요일
    } else if (dow == DayOfWeek.SUNDAY) {
      return dec31.minusDays(2); // 금요일
    } else {
      return dec31; // 평일
    }
  }

  /** 휴장일을 upsert한다. 이미 존재하면 name만 갱신하고 source는 유지한다. */
  private void upsertHoliday(
      LocalDate date, String name, String source, YearSyncResult yearResult) {
    marketHolidayRepository
        .findByHolidayDate(date)
        .ifPresentOrElse(
            existing -> {
              // 이미 존재: name만 갱신, source 유지
              if (!existing.getName().equals(name)) {
                existing.setName(name);
                marketHolidayRepository.save(existing);
                yearResult.incrementUpdated();
                log.debug(
                    "휴장일 name 갱신: date={}, name={}, source={} (유지)",
                    date,
                    name,
                    existing.getSource());
              }
            },
            () -> {
              // 신규: 삽입
              insertHoliday(date, name, source, yearResult);
              log.debug("휴장일 신규 삽입: date={}, name={}, source={}", date, name, source);
            });
  }

  /** 휴장일이 없을 때만 삽입한다 (이미 있으면 아무 작업 안 함). */
  private void upsertHolidayIfAbsent(
      LocalDate date, String name, String source, YearSyncResult yearResult) {
    if (!marketHolidayRepository.existsByHolidayDate(date)) {
      insertHoliday(date, name, source, yearResult);
      log.debug("휴장일 신규 삽입(RULE): date={}, name={}, source={}", date, name, source);
    } else {
      log.debug("휴장일 이미 존재 (건너뜀): date={}, source={}", date, source);
    }
  }

  private void insertHoliday(
      LocalDate date, String name, String source, YearSyncResult yearResult) {
    marketHolidayRepository.save(
        MarketHoliday.builder()
            .holidayDate(date)
            .name(name)
            .source(source)
            .createdAt(LocalDateTime.now(clock))
            .build());
    yearResult.incrementInserted();
  }

  /** 동기화 결과 요약 DTO. */
  public static class SyncResult {
    private final Map<Integer, YearSyncResult> yearResults = new LinkedHashMap<>();

    void addYearResult(int year, YearSyncResult yearResult) {
      yearResults.put(year, yearResult);
    }

    /** 연도별 동기화 결과를 반환한다. */
    public YearSyncResult getYearResult(int year) {
      return yearResults.get(year);
    }

    public Map<Integer, YearSyncResult> getAllYearResults() {
      return yearResults;
    }

    /** 전체 연도의 신규 저장 건수 합계를 반환한다. */
    public long totalInserted() {
      return yearResults.values().stream().mapToLong(YearSyncResult::getInserted).sum();
    }

    /** 전체 연도의 갱신 건수 합계를 반환한다. */
    public long totalUpdated() {
      return yearResults.values().stream().mapToLong(YearSyncResult::getUpdated).sum();
    }

    /** 동기화에 실패한 연도 수를 반환한다. */
    public long totalFailed() {
      return yearResults.values().stream().filter(YearSyncResult::isFailed).count();
    }

    /** 동기화 결과 요약 문자열을 반환한다. */
    public String summary() {
      return String.format(
          "inserted=%d, updated=%d, failed_years=%d, details=%s",
          totalInserted(), totalUpdated(), totalFailed(), yearResults);
    }
  }

  /** 연도별 동기화 결과. */
  public static class YearSyncResult {
    private final int year;
    private long inserted = 0;
    private long updated = 0;
    private boolean failed = false;
    private String failureReason;

    /** 연도별 동기화 결과를 생성한다. */
    public YearSyncResult(int year) {
      this.year = year;
    }

    public int getYear() {
      return year;
    }

    public long getInserted() {
      return inserted;
    }

    public long getUpdated() {
      return updated;
    }

    public boolean isFailed() {
      return failed;
    }

    public String getFailureReason() {
      return failureReason;
    }

    void incrementInserted() {
      inserted++;
    }

    void incrementUpdated() {
      updated++;
    }

    void setFailed(boolean failed) {
      this.failed = failed;
    }

    void setFailureReason(String failureReason) {
      this.failureReason = failureReason;
    }

    @Override
    public String toString() {
      return String.format(
          "YearSyncResult{year=%d, inserted=%d, updated=%d, failed=%s, reason='%s'}",
          year, inserted, updated, failed, failureReason);
    }
  }
}
