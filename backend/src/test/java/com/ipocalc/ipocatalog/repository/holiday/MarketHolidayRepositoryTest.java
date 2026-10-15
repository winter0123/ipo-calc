package com.ipocalc.ipocatalog.repository.holiday;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.ipocatalog.domain.model.holiday.MarketHoliday;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** {@link MarketHolidayRepository} 테스트 - V13 SEED 데이터 검증. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class MarketHolidayRepositoryTest {

  @Autowired private MarketHolidayRepository marketHolidayRepository;

  @Test
  void seedData_2026_has17Holidays() {
    // given: V13 마이그레이션으로 2026년 SEED 데이터 17건이 삽입되어 있어야 함

    // when
    long seedCount = marketHolidayRepository.countBySourceSeed();
    long total2026 = marketHolidayRepository.countByYear(2026);
    List<MarketHoliday> holidays2026 = marketHolidayRepository.findByYear(2026);

    // then
    assertThat(seedCount).isEqualTo(17);
    assertThat(total2026).isEqualTo(17);
    assertThat(holidays2026).hasSize(17);

    // 특정 날짜 검증
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 1, 1)))
        .isTrue(); // 신정
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 2, 16)))
        .isTrue(); // 설날 연휴
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 2, 17)))
        .isTrue(); // 설날 연휴
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 2, 18)))
        .isTrue(); // 설날 연휴
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 3, 2)))
        .isTrue(); // 삼일절 대체
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 5, 1)))
        .isTrue(); // 근로자의 날
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 5, 5)))
        .isTrue(); // 어린이날
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 5, 25)))
        .isTrue(); // 부처님오신날 대체
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 6, 3)))
        .isTrue(); // 지방선거일
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 8, 17)))
        .isTrue(); // 광복절 대체
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 9, 24)))
        .isTrue(); // 추석 연휴
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 9, 25)))
        .isTrue(); // 추석 연휴
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 9, 28)))
        .isTrue(); // 추석 대체
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 10, 5)))
        .isTrue(); // 개천절 대체
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 10, 9)))
        .isTrue(); // 한글날
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 12, 25)))
        .isTrue(); // 성탄절
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2026, 12, 31)))
        .isTrue(); // 연말 휴장일

    // 모든 SEED 데이터의 source 확인
    for (MarketHoliday holiday : holidays2026) {
      assertThat(holiday.getSource()).isEqualTo("SEED");
    }
  }

  @Test
  void findByYear_returnsOrderedHolidays() {
    // when
    List<MarketHoliday> holidays = marketHolidayRepository.findByYear(2026);

    // then: 날짜 순 정렬 확인
    assertThat(holidays).hasSize(17);
    assertThat(holidays.get(0).getHolidayDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(holidays.get(16).getHolidayDate()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void findByHolidayDate_returnsCorrectHoliday() {
    // when
    MarketHoliday holiday =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2026, 5, 1)).orElseThrow();

    // then
    assertThat(holiday.getName()).isEqualTo("근로자의 날");
    assertThat(holiday.getSource()).isEqualTo("SEED");
    assertThat(holiday.getCreatedAt()).isNotNull();
  }

  @Test
  void saveAndFind_customHoliday() {
    // given
    MarketHoliday custom =
        MarketHoliday.builder()
            .holidayDate(LocalDate.of(2027, 1, 1))
            .name("테스트 휴일")
            .source("KASI_API")
            .createdAt(LocalDateTime.now())
            .build();

    // when
    MarketHoliday saved = marketHolidayRepository.saveAndFlush(custom);
    MarketHoliday found =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2027, 1, 1)).orElseThrow();

    // then
    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getName()).isEqualTo("테스트 휴일");
    assertThat(found.getSource()).isEqualTo("KASI_API");
  }

  @Test
  void uniqueConstraint_holidayDate_preventsDuplicate() {
    // given: 2027-01-01 이미 존재
    MarketHoliday first =
        MarketHoliday.builder()
            .holidayDate(LocalDate.of(2027, 1, 1))
            .name("첫 번째")
            .source("KASI_API")
            .createdAt(LocalDateTime.now())
            .build();
    marketHolidayRepository.saveAndFlush(first);

    // when & then: 중복 날짜 저장 시 예외 발생
    MarketHoliday duplicate =
        MarketHoliday.builder()
            .holidayDate(LocalDate.of(2027, 1, 1))
            .name("두 번째")
            .source("RULE")
            .createdAt(LocalDateTime.now())
            .build();

    assertThat(
            org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> marketHolidayRepository.saveAndFlush(duplicate)))
        .isNotNull();
  }

  @Test
  void countByYear_nonExistentYear_returnsZero() {
    // when
    long count = marketHolidayRepository.countByYear(2030);

    // then
    assertThat(count).isZero();
  }
}
