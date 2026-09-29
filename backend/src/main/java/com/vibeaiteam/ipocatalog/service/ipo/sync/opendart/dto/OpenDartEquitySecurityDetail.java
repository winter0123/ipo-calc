package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DS006 증권신고서-지분증권 상세 조회 응답 개별 항목 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenDartEquitySecurityDetail {

  /** 회사고유번호 */
  private String corpCode;

  /** 기업명 */
  private String corpName;

  /** 청약기일 (원문 문자열: "2026년 11월 05일 ~ 2026년 11월 06일" 등) */
  private String sbd;

  /** 납입기일 (원문: "2026년 11월 10일" 또는 "-") */
  private String pymd;

  /** 청약공고일 (원문: "2026년 11월 05일" 또는 "-") */
  private String sband;

  /** 배정공고일 (원문: "2026년 11월 10일" 또는 "-") */
  private String asand;

  /** 배정기준일 (원문: "2026년 11월" 또는 "-") */
  private String asstd;

  /** 증권종류 (예: "보통주") */
  private String stksen;

  /** 증권수량 (쉼표 포함 문자열: "2,200,000") */
  private String stkcnt;

  /** 모집(매출)가액 (쉼표 포함 문자열: "10,000") */
  private String slprc;

  /** 모집총액 (쉼표 포함 문자열: "22,000,000,000" 또는 "-") */
  private String slta;

  /** 인수인명/주간사 (예: "한국투자증권") */
  private String actnmn;

  /** 청약기일 시작일 파싱 결과 (파싱 실패 시 {@code null}) */
  private LocalDate subscriptionStartDate;

  /** 증권수량 파싱 결과 (파싱 실패 시 {@code null}) */
  private Long offeringShareCount;

  /** 모집가액 파싱 결과 (파싱 실패 시 {@code null}) */
  private Long offerPrice;

  /** 모집총액 파싱 결과 (파싱 실패 시 {@code null}) */
  private Long totalOfferingAmount;

  /** 원문 필드에서 파싱하여 보조 필드 채움. DS006 응답 매핑 후 호출. */
  public void parseDerivedFields() {
    this.subscriptionStartDate = parseSbdStartDate(this.sbd);
    this.offeringShareCount = parseNumber(this.stkcnt);
    this.offerPrice = parseNumber(this.slprc);
    this.totalOfferingAmount = parseNumber(this.slta);
  }

  /**
   * 청약기일(sbd) 문자열에서 시작일만 추출하여 {@code LocalDate}로 변환.
   *
   * <p>형식 예: {@code "2026년 11월 05일 ~ 2026년 11월 06일"}, {@code "2026년 11월 05일"}, {@code "-"}, {@code
   * null}
   *
   * @param sbd 원문 청약기일 문자열
   * @return 파싱된 시작일 또는 {@code null}
   */
  private static LocalDate parseSbdStartDate(String sbd) {
    if (sbd == null || sbd.isBlank() || sbd.equals("-")) {
      return null;
    }
    // "2026년 11월 05일 ~ 2026년 11월 06일" 또는 "2026년 11월 05일"에서 첫 번째 날짜만 추출
    String firstDatePart = sbd.split("~")[0].trim();
    return parseKoreanDate(firstDatePart);
  }

  /**
   * 한국어 날짜 문자열("2026년 11월 05일", "2026년 11월" 등)을 {@code LocalDate}로 파싱.
   *
   * @param koreanDate 한국어 날짜 문자열
   * @return 파싱된 {@code LocalDate} 또는 {@code null} (파싱 실패 시)
   */
  private static LocalDate parseKoreanDate(String koreanDate) {
    if (koreanDate == null || koreanDate.isBlank()) {
      return null;
    }
    try {
      // "2026년 11월 05일" 형식
      DateTimeFormatter formatterWithDay =
          DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN);
      return LocalDate.parse(koreanDate.trim(), formatterWithDay);
    } catch (DateTimeParseException e1) {
      try {
        // "2026년 11월" 형식 (일자 없는 경우) → 1일로 가정
        DateTimeFormatter formatterMonthOnly =
            DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);
        return LocalDate.parse(koreanDate.trim(), formatterMonthOnly).withDayOfMonth(1);
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }

  /**
   * 쉼표 포함 숫자 문자열을 {@code Long}으로 파싱.
   *
   * @param raw 쉼표 포함 문자열 (예: "2,200,000") 또는 "-", null
   * @return 파싱된 {@code Long} 또는 {@code null}
   */
  private static Long parseNumber(String raw) {
    if (raw == null || raw.isBlank() || raw.equals("-")) {
      return null;
    }
    try {
      return Long.parseLong(raw.replaceAll(",", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
