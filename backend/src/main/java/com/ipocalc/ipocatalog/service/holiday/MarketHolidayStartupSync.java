package com.ipocalc.ipocatalog.service.holiday;

import com.ipocalc.ipocatalog.domain.model.holiday.MarketHoliday;
import com.ipocalc.ipocatalog.repository.holiday.MarketHolidayRepository;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 시 휴장일 보충 동기화.
 *
 * <p>애플리케이션이 준비되면 올해와 내년 중 {@code source='KASI_API'}인 행이 하나도 없는 연도만 골라 {@link
 * MarketHolidaySyncService#syncYears(List)}를 호출한다. 대상 연도가 없으면 아무 작업도 하지 않는다. 동기화가 예외를 던져도 앱 기동을 막지
 * 않는다(WARN 로그만 남김).
 *
 * <p>기존 {@link MarketHolidaySyncScheduler}와 동일한 활성화 조건({@code app.holiday-sync.enabled=true}, 기본값
 * true)을 사용한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "app.holiday-sync.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MarketHolidayStartupSync {

  private final MarketHolidaySyncService marketHolidaySyncService;
  private final MarketHolidayRepository marketHolidayRepository;
  private final Clock clock;

  /** 애플리케이션 준비 완료 시 실행되어 누락된 연도의 휴장일을 동기화한다. */
  @EventListener(ApplicationReadyEvent.class)
  public void syncOnStartup() {
    int thisYear = Year.now(clock).getValue();
    int nextYear = thisYear + 1;

    List<Integer> yearsToSync =
        List.of(thisYear, nextYear).stream().filter(year -> !hasKasiApiData(year)).toList();

    if (yearsToSync.isEmpty()) {
      log.info("기동 시 휴장일 보충 동기화 건너뜀: 올해({})와 내년({}) 모두 KASI_API 데이터 존재", thisYear, nextYear);
      return;
    }

    log.info("기동 시 휴장일 보충 동기화 시작: targetYears={}", yearsToSync);
    try {
      MarketHolidaySyncService.SyncResult result = marketHolidaySyncService.syncYears(yearsToSync);
      log.info("기동 시 휴장일 보충 동기화 완료: {}", result.summary());
    } catch (Exception e) {
      // 앱 기동을 막지 않음 - WARN만 남기고 계속
      log.warn("기동 시 휴장일 보충 동기화 실패(무시됨): {}", e.getMessage());
    }
  }

  /** 해당 연도에 KASI_API 출처의 휴장일 데이터가 있는지 확인한다. */
  private boolean hasKasiApiData(int year) {
    List<MarketHoliday> holidays = marketHolidayRepository.findByYear(year);
    return holidays.stream().anyMatch(h -> "KASI_API".equals(h.getSource()));
  }
}
