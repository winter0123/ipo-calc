package com.vibeaiteam.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** {@link BusinessDayCalculator} 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class BusinessDayCalculatorTest {

  @Autowired private BusinessDayCalculator calculator;

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
}
