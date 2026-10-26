package com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** {@link OpenDartEquitySecurityDetail} DTO 파싱 테스트. */
class OpenDartEquitySecurityDetailTest {

  @Test
  void parseDerivedFields_periodSbd_parsesStartAndEndDate() {
    // given: 기간 문자열 "2026년 11월 05일 ~ 2026년 11월 06일"
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .stkcnt("2,200,000")
            .slprc("10,000")
            .slta("22,000,000,000")
            .build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(detail.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(detail.getOfferingShareCount()).isEqualTo(2_200_000L);
    assertThat(detail.getOfferPrice()).isEqualTo(10_000L);
    assertThat(detail.getTotalOfferingAmount()).isEqualTo(22_000_000_000L);
  }

  @Test
  void parseDerivedFields_singleDateSbd_parsesStartDateOnly_endDateNull() {
    // given: 단일 날짜 "2026년 11월 05일" (종료일은 Service 계층에서 다음 영업일로 계산)
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("15,000")
            .build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(detail.getSubscriptionEndDate()).isNull(); // 단일 날짜는 종료일 null (Service에서 계산)
    assertThat(detail.getOfferingShareCount()).isEqualTo(1_000_000L);
    assertThat(detail.getOfferPrice()).isEqualTo(15_000L);
  }

  @Test
  void parseDerivedFields_dashSbd_returnsNull() {
    // given: 대시 "-" (데이터 없음)
    OpenDartEquitySecurityDetail detail = OpenDartEquitySecurityDetail.builder().sbd("-").build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isNull();
    assertThat(detail.getSubscriptionEndDate()).isNull();
  }

  @Test
  void parseDerivedFields_nullSbd_returnsNull() {
    // given: null
    OpenDartEquitySecurityDetail detail = OpenDartEquitySecurityDetail.builder().sbd(null).build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isNull();
    assertThat(detail.getSubscriptionEndDate()).isNull();
  }

  @Test
  void parseDerivedFields_emptySbd_returnsNull() {
    // given: 빈 문자열
    OpenDartEquitySecurityDetail detail = OpenDartEquitySecurityDetail.builder().sbd("").build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isNull();
    assertThat(detail.getSubscriptionEndDate()).isNull();
  }

  @Test
  void parseDerivedFields_blankSbd_returnsNull() {
    // given: 공백 문자열
    OpenDartEquitySecurityDetail detail = OpenDartEquitySecurityDetail.builder().sbd("   ").build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isNull();
    assertThat(detail.getSubscriptionEndDate()).isNull();
  }

  @Test
  void parseDerivedFields_unparsableSbd_returnsNull() {
    // given: 파싱 불가능한 형식
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder().sbd("알 수 없는 형식").build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getSubscriptionStartDate()).isNull();
    assertThat(detail.getSubscriptionEndDate()).isNull();
  }

  @Test
  void parseDerivedFields_dashNumberFields_returnsNull() {
    // given: 숫자 필드가 대시인 경우
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder().stkcnt("-").slprc("-").slta("-").build();

    // when
    detail.parseDerivedFields();

    // then
    assertThat(detail.getOfferingShareCount()).isNull();
    assertThat(detail.getOfferPrice()).isNull();
    assertThat(detail.getTotalOfferingAmount()).isNull();
  }

  @Test
  void parseDerivedFields_asandAndPymd_mapToAllotmentAndRefundDate() {
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder().asand("2026년 10월 15일").pymd("2026년 10월 15일").build();

    detail.parseDerivedFields();

    assertThat(detail.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(detail.getRefundDate()).isEqualTo(LocalDate.of(2026, 10, 15));
  }

  @Test
  void parseDerivedFields_dashMonthOnlyOrGarbageAllotmentRefund_returnsNull() {
    OpenDartEquitySecurityDetail dash =
        OpenDartEquitySecurityDetail.builder().asand("-").pymd("-").build();
    OpenDartEquitySecurityDetail monthOnly =
        OpenDartEquitySecurityDetail.builder().asand("2026년 10월").pymd("알 수 없음").build();
    OpenDartEquitySecurityDetail missing = OpenDartEquitySecurityDetail.builder().build();

    dash.parseDerivedFields();
    monthOnly.parseDerivedFields();
    missing.parseDerivedFields();

    assertThat(dash.getAllotmentDate()).isNull();
    assertThat(dash.getRefundDate()).isNull();
    assertThat(monthOnly.getAllotmentDate()).isNull(); // 월만 있는 값을 1일로 채우지 않음
    assertThat(monthOnly.getRefundDate()).isNull();
    assertThat(missing.getAllotmentDate()).isNull();
    assertThat(missing.getRefundDate()).isNull();
  }

  private static OpenDartEquitySecurityDetail parsed(String sbd) {
    OpenDartEquitySecurityDetail d = OpenDartEquitySecurityDetail.builder().sbd(sbd).build();
    d.parseDerivedFields();
    return d;
  }

  @Test
  void sbd_endWithoutYear_usesStartYear() {
    OpenDartEquitySecurityDetail d = parsed("2026년 10월 12일 ~ 10월 13일");
    assertThat(d.getSubscriptionEndDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 13));
    assertThat(d.isSubscriptionEndUnreadable()).isFalse();
  }

  @Test
  void sbd_endDayOnly_usesStartYearAndMonth() {
    assertThat(parsed("2026년 10월 12일 ~ 13일").getSubscriptionEndDate())
        .isEqualTo(java.time.LocalDate.of(2026, 10, 13));
  }

  @Test
  void sbd_endMonthBeforeStartMonth_rollsToNextYear() {
    assertThat(parsed("2026년 12월 30일 ~ 1월 2일").getSubscriptionEndDate())
        .isEqualTo(java.time.LocalDate.of(2027, 1, 2));
  }

  @Test
  void sbd_endDash_isUnreadableNotSingleDate() {
    OpenDartEquitySecurityDetail d = parsed("2026년 10월 12일 ~ -");
    assertThat(d.getSubscriptionStartDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 12));
    assertThat(d.getSubscriptionEndDate()).isNull();
    assertThat(d.isSubscriptionEndUnreadable()).isTrue();
  }

  @Test
  void sbd_singleDate_isNotUnreadable() {
    assertThat(parsed("2026년 10월 12일").isSubscriptionEndUnreadable()).isFalse();
  }

  @Test
  void sbd_variantFormats_areParsed() {
    for (String sbd :
        new String[] {
          "2026.10.12 ~ 2026.10.13",
          "2026-10-12 ∼ 2026-10-13",
          "2026/10/12 〜 2026/10/13",
          "2026년 10월 12일(월) ～ 10월 13일(화) (기관 포함)"
        }) {
      OpenDartEquitySecurityDetail d = parsed(sbd);
      assertThat(d.getSubscriptionStartDate())
          .as(sbd)
          .isEqualTo(java.time.LocalDate.of(2026, 10, 12));
      assertThat(d.getSubscriptionEndDate())
          .as(sbd)
          .isEqualTo(java.time.LocalDate.of(2026, 10, 13));
    }
  }

  @Test
  void sbd_monthOnly_isNull() {
    assertThat(parsed("2026년 10월").getSubscriptionStartDate()).isNull();
  }
}
