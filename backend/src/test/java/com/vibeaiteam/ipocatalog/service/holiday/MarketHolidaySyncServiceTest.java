package com.vibeaiteam.ipocatalog.service.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.BusinessDayCalculator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** {@link MarketHolidaySyncService} 테스트 (실제 Repository 사용). */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
@Transactional
class MarketHolidaySyncServiceTest {

  @Autowired private MarketHolidayRepository marketHolidayRepository;
  @Autowired private BusinessDayCalculator businessDayCalculator;

  @Autowired private MarketHolidaySyncService syncService;

  @MockitoBean private KasiSpecialDayClient kasiClient;
  @MockitoBean private DataGoKrApiProperties dataGoKrApiProperties;

  @BeforeEach
  void setUp() {
    cleanTestData();
  }

  @AfterEach
  void tearDown() {
    cleanTestData();
  }

  private void cleanTestData() {
    // 테스트용으로 삽입된 KASI_API, RULE 소스 데이터만 삭제
    // SEED 데이터(2026년 17건)는 보존
    marketHolidayRepository.deleteBySourceIn(List.of("KASI_API", "RULE"));
    marketHolidayRepository.flush();
  }

  @Test
  void syncYears_newInserts_kasiApiSource() {
    // given: 2027년 KASI API 응답 모킹
    when(kasiClient.getHolidays(2027))
        .thenReturn(
            List.of(
                new KasiSpecialDayClient.HolidayItem(LocalDate.of(2027, 1, 1), "신정"),
                new KasiSpecialDayClient.HolidayItem(LocalDate.of(2027, 5, 5), "어린이날"),
                new KasiSpecialDayClient.HolidayItem(LocalDate.of(2027, 12, 25), "성탄절")));
    when(dataGoKrApiProperties.isServiceKeyMissing()).thenReturn(false);

    // when
    MarketHolidaySyncService.SyncResult result = syncService.syncYears(List.of(2027));

    // then
    assertThat(result.totalInserted()).isEqualTo(5); // KASI 3건 + 근로자의날 1건 + 연말휴장일 1건
    assertThat(result.totalUpdated()).isZero();
    assertThat(result.totalFailed()).isZero();

    // KASI_API 소스 확인
    List<MarketHoliday> kasiHolidays =
        marketHolidayRepository.findByYear(2027).stream()
            .filter(h -> "KASI_API".equals(h.getSource()))
            .toList();
    assertThat(kasiHolidays).hasSize(3);

    // RULE 소스 확인 (근로자의 날, 연말 휴장일)
    List<MarketHoliday> ruleHolidays =
        marketHolidayRepository.findByYear(2027).stream()
            .filter(h -> "RULE".equals(h.getSource()))
            .toList();
    assertThat(ruleHolidays).hasSize(2);
    assertThat(ruleHolidays)
        .extracting(MarketHoliday::getName)
        .containsExactlyInAnyOrder("근로자의 날", "연말 휴장일");
    assertThat(ruleHolidays)
        .extracting(MarketHoliday::getHolidayDate)
        .containsExactlyInAnyOrder(
            LocalDate.of(2027, 5, 1), LocalDate.of(2027, 12, 31)); // 2027-12-31은 금요일
  }

  @Test
  void syncYears_existingSeedRow_nameUpdatedSourcePreserved() {
    // given: 2026년 SEED 데이터가 이미 존재 (마이그레이션 V13)
    // 2026-01-01 신정(SEED)이 있는데 KASI에서 같은 날짜로 "신정" 반환 -> name만 갱신, source=SEED 유지
    when(kasiClient.getHolidays(2026))
        .thenReturn(
            List.of(
                new KasiSpecialDayClient.HolidayItem(LocalDate.of(2026, 1, 1), "신정"),
                new KasiSpecialDayClient.HolidayItem(LocalDate.of(2026, 5, 1), "근로자의 날")));
    when(dataGoKrApiProperties.isServiceKeyMissing()).thenReturn(false);

    // when
    MarketHolidaySyncService.SyncResult result = syncService.syncYears(List.of(2026));

    // then
    // SEED 행은 source가 유지되어야 함
    MarketHoliday newYear =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2026, 1, 1)).orElseThrow();
    assertThat(newYear.getSource()).isEqualTo("SEED"); // source 유지
    assertThat(newYear.getName()).isEqualTo("신정"); // name 갱신 (같은 값이지만)

    // 근로자의 날은 SEED에 이미 있으므로 RULE upsertIfAbsent로 건너뜀
    MarketHoliday laborDay =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2026, 5, 1)).orElseThrow();
    assertThat(laborDay.getSource()).isEqualTo("SEED"); // SEED 유지
  }

  @Test
  void syncYears_ruleYearEndHoliday_fridayAndSunday() {
    // given: 2027년(12/31 금요일), 2028년(12/31 일요일)
    when(kasiClient.getHolidays(2027)).thenReturn(List.of());
    when(kasiClient.getHolidays(2028)).thenReturn(List.of());
    when(dataGoKrApiProperties.isServiceKeyMissing()).thenReturn(false);

    // when
    MarketHolidaySyncService.SyncResult result = syncService.syncYears(List.of(2027, 2028));

    // then
    // 2027년 연말 휴장일: 12/31(금요일) -> 12/31
    MarketHoliday yearEnd2027 =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2027, 12, 31)).orElseThrow();
    assertThat(yearEnd2027.getName()).isEqualTo("연말 휴장일");
    assertThat(yearEnd2027.getSource()).isEqualTo("RULE");

    // 2028년 연말 휴장일: 12/31(일요일) -> 12/29(금요일)
    MarketHoliday yearEnd2028 =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2028, 12, 29)).orElseThrow();
    assertThat(yearEnd2028.getName()).isEqualTo("연말 휴장일");
    assertThat(yearEnd2028.getSource()).isEqualTo("RULE");
    // 12/31에는 생성되지 않아야 함
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2028, 12, 31))).isFalse();
  }

  @Test
  void syncYears_oneYearFails_continuesToNextYear() {
    // given: 2027년 성공, 2028년 실패
    when(kasiClient.getHolidays(2027))
        .thenReturn(List.of(new KasiSpecialDayClient.HolidayItem(LocalDate.of(2027, 1, 1), "신정")));
    when(kasiClient.getHolidays(2028))
        .thenThrow(new KasiSpecialDayClient.KasiApiException("API 호출 실패"));
    when(dataGoKrApiProperties.isServiceKeyMissing()).thenReturn(false);

    // when
    MarketHolidaySyncService.SyncResult result = syncService.syncYears(List.of(2027, 2028));

    // then
    assertThat(result.getYearResult(2027).isFailed()).isFalse();
    assertThat(result.getYearResult(2028).isFailed()).isTrue();
    assertThat(result.getYearResult(2028).getFailureReason()).contains("API 호출 실패");

    // 2027년 데이터는 정상 저장됨
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2027, 1, 1))).isTrue();
    // 2028년 KASI_API 데이터는 없음 (RULE은 수행됨 - 키가 있으므로)
    MarketHoliday laborDay2028 =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2028, 5, 1)).orElseThrow();
    assertThat(laborDay2028.getSource()).isEqualTo("RULE");
  }

  @Test
  void syncYears_missingServiceKey_skipsKasiAndRule_noException() {
    // given: 서비스 키 없음
    when(dataGoKrApiProperties.isServiceKeyMissing()).thenReturn(true);

    // when
    MarketHolidaySyncService.SyncResult result = syncService.syncYears(List.of(2027));

    // then
    assertThat(result.totalInserted()).isZero();
    assertThat(result.totalUpdated()).isZero();
    assertThat(result.totalFailed()).isZero();
    // KASI 호출도, RULE 보완도 수행되지 않음
    assertThat(marketHolidayRepository.countByYear(2027)).isZero();
  }

  @Test
  void syncYears_noDeletion_preservesExistingData() {
    // given: 2027년 기존 데이터 존재 (KASI_API)
    MarketHoliday existing =
        MarketHoliday.builder()
            .holidayDate(LocalDate.of(2027, 3, 1))
            .name("삼일절")
            .source("KASI_API")
            .createdAt(LocalDateTime.now())
            .build();
    marketHolidayRepository.saveAndFlush(existing);

    when(kasiClient.getHolidays(2027))
        .thenReturn(List.of(new KasiSpecialDayClient.HolidayItem(LocalDate.of(2027, 1, 1), "신정")));
    when(dataGoKrApiProperties.isServiceKeyMissing()).thenReturn(false);

    // when
    syncService.syncYears(List.of(2027));

    // then: 기존 데이터 보존됨 (삭제 안 함)
    assertThat(marketHolidayRepository.existsByHolidayDate(LocalDate.of(2027, 3, 1))).isTrue();
    MarketHoliday preserved =
        marketHolidayRepository.findByHolidayDate(LocalDate.of(2027, 3, 1)).orElseThrow();
    assertThat(preserved.getSource()).isEqualTo("KASI_API");
    assertThat(preserved.getName()).isEqualTo("삼일절");
  }
}
