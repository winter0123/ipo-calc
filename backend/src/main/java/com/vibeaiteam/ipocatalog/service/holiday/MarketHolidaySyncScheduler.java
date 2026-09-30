package com.vibeaiteam.ipocatalog.service.holiday;

import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시장 휴장일 배치 동기화 스케줄러.
 *
 * <p>매월 1일 03:00(KST)에 실행되어 올해와 내년 휴장일을 동기화한다. {@code app.holiday-sync.enabled=true}일 때만 활성화된다(기본값
 * true).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "app.holiday-sync.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MarketHolidaySyncScheduler {

  private final MarketHolidaySyncService marketHolidaySyncService;

  /**
   * 매월 1일 03:00(KST) 실행.
   *
   * <p>cron 표현식: 초 분 시 일 월 요일 {@code 0 0 3 1 * *} = 매월 1일 03:00:00 zone: Asia/Seoul (KST)
   */
  @Scheduled(cron = "0 0 3 1 * *", zone = "Asia/Seoul")
  public void syncMonthly() {
    int thisYear = Year.now().getValue();
    int nextYear = thisYear + 1;

    log.info("월간 휴장일 동기화 시작: targetYears=[{}, {}]", thisYear, nextYear);

    MarketHolidaySyncService.SyncResult result =
        marketHolidaySyncService.syncYears(List.of(thisYear, nextYear));

    log.info("월간 휴장일 동기화 완료: {}", result.summary());
  }

  /**
   * 수동 실행용 메서드 (테스트나 운영자가 직접 호출할 때 사용).
   *
   * @param years 동기화할 연도 목록
   * @return 동기화 결과
   */
  public MarketHolidaySyncService.SyncResult syncYearsManual(List<Integer> years) {
    log.info("수동 휴장일 동기화 시작: targetYears={}", years);
    MarketHolidaySyncService.SyncResult result = marketHolidaySyncService.syncYears(years);
    log.info("수동 휴장일 동기화 완료: {}", result.summary());
    return result;
  }
}
