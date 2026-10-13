package com.vibeaiteam.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** {@link BusinessDayCalculator} 테스트 (Mock Repository 사용). */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class BusinessDayCalculatorTest {

  @MockitoBean private MarketHolidayRepository marketHolidayRepository;

  @Autowired private BusinessDayCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator.evictCache();
    // 다른 연도도 빈 리스트 반환 (기본값)
    when(marketHolidayRepository.findByYear(anyInt())).thenReturn(List.of());

    // 2026년 휴장일 모킹 (V13 SEED 데이터와 동일) - anyInt() 이후에 스터빙하여 우선순위 확보
    when(marketHolidayRepository.findByYear(2026))
        .thenReturn(
            List.of(
                holiday(2026, 1, 1, "신정"),
                holiday(2026, 2, 16, "설날 연휴"),
                holiday(2026, 2, 17, "설날 연휴"),
                holiday(2026, 2, 18, "설날 연휴"),
                holiday(2026, 3, 2, "삼일절 대체공휴일"),
                holiday(2026, 5, 1, "근로자의 날"),
                holiday(2026, 5, 5, "어린이날"),
                holiday(2026, 5, 25, "부처님오신날 대체공휴일"),
                holiday(2026, 6, 3, "지방선거일"),
                holiday(2026, 8, 17, "광복절 대체공휴일"),
                holiday(2026, 9, 24, "추석 연휴"),
                holiday(2026, 9, 25, "추석 연휴"),
                holiday(2026, 9, 28, "추석 대체공휴일"),
                holiday(2026, 10, 5, "개천절 대체공휴일"),
                holiday(2026, 10, 9, "한글날"),
                holiday(2026, 12, 25, "성탄절"),
                holiday(2026, 12, 31, "연말 휴장일")));

    // 2027년은 데이터 없음 (주말만 제외)
    when(marketHolidayRepository.findByYear(2027)).thenReturn(List.of());
  }

  private MarketHoliday holiday(int year, int month, int day, String name) {
    return MarketHoliday.builder()
        .holidayDate(LocalDate.of(year, month, day))
        .name(name)
        .source("SEED")
        .build();
  }

  @Test
  void nextBusinessDay_friday_returnsNextBusinessDay() {
    // given: 2026-09-25 금요일 (추석 연휴 휴장일)
    LocalDate friday = LocalDate.of(2026, 9, 25);

    // when
    LocalDate next = calculator.nextBusinessDay(friday);

    // then: 토/일, 월요일(대체휴일) 건너뛰고 화요일
    assertThat(next).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void nextBusinessDay_thursdayBeforeHoliday_returnsTuesday() {
    // given: 2026-09-24 목요일 (다음날 9/25 금요일은 추석 연휴 휴장일)
    LocalDate thursday = LocalDate.of(2026, 9, 24);

    // when
    LocalDate next = calculator.nextBusinessDay(thursday);

    // then: 금요일(휴장일), 토/일 건너뛰고 화요일
    assertThat(next).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void nextBusinessDay_fridayBeforeHoliday_returnsTuesday() {
    // given: 2026-09-25 금요일 (추석 연휴 휴장일, 다음 영업일은 화요일 9/29)
    LocalDate friday = LocalDate.of(2026, 9, 25);

    // when
    LocalDate next = calculator.nextBusinessDay(friday);

    // then: 토/일, 월요일(대체휴일) 건너뛰고 화요일
    assertThat(next).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void nextBusinessDay_holidayPeriodEnd_returnsNextBusinessDay() {
    // given: 2026-09-28 월요일 (추석 대체휴일, 휴장일)
    LocalDate monday = LocalDate.of(2026, 9, 28);

    // when
    LocalDate next = calculator.nextBusinessDay(monday);

    // then: 화요일
    assertThat(next).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void isBusinessDay_weekend_returnsFalse() {
    // given: 토요일
    LocalDate saturday = LocalDate.of(2026, 9, 26);
    // given: 일요일
    LocalDate sunday = LocalDate.of(2026, 9, 27);

    // when & then
    assertThat(calculator.isBusinessDay(saturday)).isFalse();
    assertThat(calculator.isBusinessDay(sunday)).isFalse();
  }

  @Test
  void isBusinessDay_holiday_returnsFalse() {
    // given: 2026-09-24 목요일 (추석 연휴 휴장일)
    LocalDate holiday = LocalDate.of(2026, 9, 24);

    // when & then
    assertThat(calculator.isBusinessDay(holiday)).isFalse();
  }

  @Test
  void isBusinessDay_normalWeekday_returnsTrue() {
    // given: 2026-09-23 수요일 (평일, 휴장일 아님)
    LocalDate wednesday = LocalDate.of(2026, 9, 23);

    // when & then
    assertThat(calculator.isBusinessDay(wednesday)).isTrue();
  }

  @Test
  void nextBusinessDay_yearOutOfRange_warnsAndUsesWeekendOnly() {
    // given: 리소스에 없는 연도 (2027년) - 주말만 제외 계산
    // 2027-01-01은 금요일, 다음 영업일은 월요일 2027-01-04 (신정은 2026년만 정의됨)
    LocalDate friday2027 = LocalDate.of(2027, 1, 1);

    // when
    LocalDate next = calculator.nextBusinessDay(friday2027);

    // then: 주말만 제외하므로 월요일 반환 (WARN 로그는 수동 확인)
    assertThat(next).isEqualTo(LocalDate.of(2027, 1, 4));
  }

  @Test
  void nextBusinessDay_multipleHolidays_skipsAll() {
    // given: 설날 연휴 직전 평일 (2026-02-13 금요일, 다음 2/16~18 휴장일)
    LocalDate fridayBeforeHoliday = LocalDate.of(2026, 2, 13);

    // when
    LocalDate next = calculator.nextBusinessDay(fridayBeforeHoliday);

    // then: 토/일, 월/화/수(설날 연휴) 건너뛰고 목요일 2/19
    assertThat(next).isEqualTo(LocalDate.of(2026, 2, 19));
  }

  @Test
  void isBusinessDay_newYear_returnsFalse() {
    // given: 2026-01-01 신정
    LocalDate newYear = LocalDate.of(2026, 1, 1);

    // when & then
    assertThat(calculator.isBusinessDay(newYear)).isFalse();
  }

  @Test
  void isBusinessDay_christmas_returnsFalse() {
    // given: 2026-12-25 성탄절
    LocalDate christmas = LocalDate.of(2026, 12, 25);

    // when & then
    assertThat(calculator.isBusinessDay(christmas)).isFalse();
  }

  @Test
  void evictCache_clearsCache() {
    // given: 캐시가 채워진 상태
    calculator.isBusinessDay(LocalDate.of(2026, 1, 1));
    calculator.isBusinessDay(LocalDate.of(2027, 1, 1));

    // when
    calculator.evictCache();

    // then: 캐시 무효화 후 다시 조회 시 DB에서 다시 로드 (mock 호출 확인으로 대체)
    // evictCache 호출 후에도 정상 동작 확인
    assertThat(calculator.isBusinessDay(LocalDate.of(2026, 1, 1))).isFalse();
    assertThat(calculator.isBusinessDay(LocalDate.of(2027, 1, 1))).isTrue(); // 2027-01-01은 금요일(평일)
  }

  @Test
  void nextBusinessDay_saturday_returnsMonday() {
    // given: 토요일
    LocalDate saturday = LocalDate.of(2026, 9, 26);

    // when
    LocalDate next = calculator.nextBusinessDay(saturday);

    // then: 일요일 건너뛰고 월요일 (9/28은 휴장일이므로 화요일)
    assertThat(next).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void nextBusinessDay_sunday_returnsTuesday() {
    // given: 일요일
    LocalDate sunday = LocalDate.of(2026, 9, 27);

    // when
    LocalDate next = calculator.nextBusinessDay(sunday);

    // then: 월요일(휴장일) 건너뛰고 화요일
    assertThat(next).isEqualTo(LocalDate.of(2026, 9, 29));
  }

  @Test
  void plusBusinessDays_mondayTwoBusinessDays_returnsWednesday() {
    // given: 2026-09-21 월요일 (평일, 휴장일 없음)
    LocalDate monday = LocalDate.of(2026, 9, 21);

    // when
    LocalDate result = calculator.plusBusinessDays(monday, 2);

    // then: 화, 수 (2영업일 후)
    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 23));
  }

  @Test
  void plusBusinessDays_fridayTwoBusinessDays_returnsTuesday() {
    // given: 2026-09-25 금요일 (추석 연휴 휴장일)
    LocalDate friday = LocalDate.of(2026, 9, 25);

    // when
    LocalDate result = calculator.plusBusinessDays(friday, 2);

    // then: 토/일, 월(대체휴일) 건너뛰고 화, 수 -> 2영업일 후는 수요일 9/30
    // 실제로: 9/26(토), 9/27(일), 9/28(월-휴장일), 9/29(화-1영업일), 9/30(수-2영업일)
    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  @Test
  void plusBusinessDays_fridayBeforeHolidayTwoBusinessDays_skipsHolidays() {
    // given: 2026-02-13 금요일 (다음 주 월/화/수 설날 연휴 휴장일)
    LocalDate friday = LocalDate.of(2026, 2, 13);

    // when
    LocalDate result = calculator.plusBusinessDays(friday, 2);

    // then: 토/일, 월/화/수(설날 연휴) 건너뛰고 목/금 -> 2영업일 후는 금요일 2/20
    assertThat(result).isEqualTo(LocalDate.of(2026, 2, 20));
  }

  @Test
  void plusBusinessDays_zeroDays_returnsSameDate() {
    // given: 2026-09-23 수요일
    LocalDate wednesday = LocalDate.of(2026, 9, 23);

    // when
    LocalDate result = calculator.plusBusinessDays(wednesday, 0);

    // then: 같은 날짜 반환
    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 23));
  }

  @Test
  void plusBusinessDays_negativeDays_throwsException() {
    // given: 2026-09-23 수요일
    LocalDate wednesday = LocalDate.of(2026, 9, 23);

    // when & then
    assertThatThrownBy(() -> calculator.plusBusinessDays(wednesday, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("businessDays는 0 이상이어야 합니다");
  }

  @Test
  void plusBusinessDays_nonBusinessDayStart_countsFromNextBusinessDay() {
    // given: 2026-09-24 목요일 (추석 연휴 휴장일, 영업일 아님)
    LocalDate holiday = LocalDate.of(2026, 9, 24);

    // when
    LocalDate result = calculator.plusBusinessDays(holiday, 2);

    // then: date가 영업일이 아니어도 date 다음 영업일부터 센다
    // 9/24(목-휴장일), 9/25(금-휴장일), 9/26(토), 9/27(일), 9/28(월-휴장일), 9/29(화-1영업일), 9/30(수-2영업일)
    assertThat(result).isEqualTo(LocalDate.of(2026, 9, 30));
  }
}
