package com.vibeaiteam.ipocatalog.service.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link MarketHolidayStartupSync} 단위 테스트 (스프링 컨텍스트 없이). */
@ExtendWith(MockitoExtension.class)
class MarketHolidayStartupSyncTest {

  @Mock private MarketHolidaySyncService marketHolidaySyncService;

  @Mock private MarketHolidayRepository marketHolidayRepository;

  private Clock fixedClock;

  private MarketHolidayStartupSync startupSync;

  @BeforeEach
  void setUp() {
    // 2026-07-15 고정 시계 (올해=2026, 내년=2027)
    fixedClock =
        Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    startupSync =
        new MarketHolidayStartupSync(marketHolidaySyncService, marketHolidayRepository, fixedClock);
  }

  @Test
  void syncOnStartup_bothYearsMissingKasiApi_callsSyncForBothYears() {
    // given: 올해(2026), 내년(2027) 모두 KASI_API 데이터 없음
    when(marketHolidayRepository.findByYear(2026)).thenReturn(List.of(seedHoliday(2026)));
    when(marketHolidayRepository.findByYear(2027)).thenReturn(List.of(seedHoliday(2027)));

    // when
    startupSync.syncOnStartup();

    // then: 두 연도 모두 동기화 호출
    verify(marketHolidaySyncService).syncYears(List.of(2026, 2027));
  }

  @Test
  void syncOnStartup_thisYearHasKasiApi_nextYearMissing_callsSyncOnlyForNextYear() {
    // given: 올해 KASI_API 있음, 내년 없음
    when(marketHolidayRepository.findByYear(2026)).thenReturn(List.of(kasiApiHoliday(2026)));
    when(marketHolidayRepository.findByYear(2027)).thenReturn(List.of(seedHoliday(2027)));

    // when
    startupSync.syncOnStartup();

    // then: 내년만 동기화 호출
    verify(marketHolidaySyncService).syncYears(List.of(2027));
  }

  @Test
  void syncOnStartup_bothYearsHaveKasiApi_doesNotCallSync() {
    // given: 올해, 내년 모두 KASI_API 있음
    when(marketHolidayRepository.findByYear(2026)).thenReturn(List.of(kasiApiHoliday(2026)));
    when(marketHolidayRepository.findByYear(2027)).thenReturn(List.of(kasiApiHoliday(2027)));

    // when
    startupSync.syncOnStartup();

    // then: 동기화 호출 안 함
    verify(marketHolidaySyncService, never()).syncYears(anyList());
  }

  @Test
  void syncOnStartup_syncServiceThrowsException_doesNotPropagate() {
    // given: 올해 KASI_API 없음, 동기화 서비스에서 예외 발생
    when(marketHolidayRepository.findByYear(2026)).thenReturn(List.of(seedHoliday(2026)));
    when(marketHolidayRepository.findByYear(2027)).thenReturn(List.of(kasiApiHoliday(2027)));
    doThrow(new RuntimeException("KASI API 호출 실패"))
        .when(marketHolidaySyncService)
        .syncYears(List.of(2026));

    // when & then: 예외가 밖으로 나가지 않음 (메서드 완료)
    startupSync.syncOnStartup();

    // 동기화는 호출되었으나 예외는 삼킴
    verify(marketHolidaySyncService).syncYears(List.of(2026));
  }

  private MarketHoliday seedHoliday(int year) {
    return MarketHoliday.builder()
        .holidayDate(LocalDate.of(year, 1, 1))
        .name("신정")
        .source("SEED")
        .build();
  }

  private MarketHoliday kasiApiHoliday(int year) {
    return MarketHoliday.builder()
        .holidayDate(LocalDate.of(year, 1, 1))
        .name("신정")
        .source("KASI_API")
        .build();
  }
}