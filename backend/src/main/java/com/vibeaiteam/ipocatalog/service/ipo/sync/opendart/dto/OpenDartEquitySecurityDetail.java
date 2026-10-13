package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  /** 접수번호 (정정 공시 중 최신 건 선택용; 클라이언트가 채움). */
  private String rceptNo;

  /** 회사고유번호. */
  private String corpCode;

  /** 기업명. */
  private String corpName;

  /** 청약기일 (원문 문자열: "2026년 11월 05일 ~ 2026년 11월 06일" 등). */
  private String sbd;

  /** 납입기일 (원문: "2026년 11월 10일" 또는 "-"). */
  private String pymd;

  /** 청약공고일 (원문: "2026년 11월 05일" 또는 "-"). */
  private String sband;

  /** 배정공고일 (원문: "2026년 11월 10일" 또는 "-"). */
  private String asand;

  /** 배정기준일 (원문: "2026년 11월" 또는 "-"). */
  private String asstd;

  /** 증권종류 (예: "보통주"). */
  private String stksen;

  /** 증권수량 (쉼표 포함 문자열: "2,200,000"). */
  private String stkcnt;

  /** 모집(매출)가액 (쉼표 포함 문자열: "10,000"). */
  private String slprc;

  /** 모집총액 (쉼표 포함 문자열: "22,000,000,000" 또는 "-"). */
  private String slta;

  /** 인수인명/주간사 (예: "한국투자증권"). */
  private String actnmn;

  /** 청약기일 시작일 파싱 결과 (파싱 실패 시 {@code null}). */
  private LocalDate subscriptionStartDate;

  /** 청약기일 종료일 파싱 결과 (기간 문자열이면 종료일, 단일 날짜/파싱 실패 시 {@code null}). */
  private LocalDate subscriptionEndDate;

  /** 구분자("~") 뒤 끝일이 있는데 읽지 못한 경우 true (단일 날짜 형식이면 false). */
  private boolean subscriptionEndUnreadable;

  /** 배정공고일(asand) 파싱 결과 (일자까지 읽히지 않으면 {@code null}). */
  private LocalDate allotmentDate;

  /** 납입기일(pymd) 파싱 결과. 공모주 환불일은 보통 납입기일과 같다 (일자까지 읽히지 않으면 {@code null}). */
  private LocalDate refundDate;

  /** 증권수량 파싱 결과 (파싱 실패 시 {@code null}). */
  private Long offeringShareCount;

  /** 모집가액 파싱 결과 (파싱 실패 시 {@code null}). */
  private Long offerPrice;

  /** 모집총액 파싱 결과 (파싱 실패 시 {@code null}). */
  private Long totalOfferingAmount;

  /** 원문 필드에서 파싱하여 보조 필드 채움. DS006 응답 매핑 후 호출. */
  public void parseDerivedFields() {
    this.subscriptionStartDate = parseSbdStartDate(this.sbd);
    this.subscriptionEndDate = parseSbdEndDate(this.sbd);
    this.subscriptionEndUnreadable =
        this.subscriptionStartDate != null
            && splitSbd(this.sbd).length > 1
            && this.subscriptionEndDate == null;
    this.allotmentDate = parseYmd(this.asand);
    this.refundDate = parseYmd(this.pymd);
    this.offeringShareCount = parseNumber(this.stkcnt);
    this.offerPrice = parseNumber(this.slprc);
    this.totalOfferingAmount = parseNumber(this.slta);
  }

  private static final Pattern PAREN = Pattern.compile("\\([^)]*\\)|（[^）]*）");
  private static final Pattern TILDE = Pattern.compile("[~∼〜～]");
  private static final Pattern NUMBER = Pattern.compile("\\d+");

  /** 청약기일(sbd) 시작일: 숫자 3개(연·월·일)를 구분자 무관하게 읽는다. 월만 있거나 못 읽으면 {@code null}. */
  private static LocalDate parseSbdStartDate(String sbd) {
    String[] parts = splitSbd(sbd);
    return parts == null ? null : parseYmd(parts[0]);
  }

  /**
   * 청약기일(sbd) 종료일. 끝일의 연·월이 생략되면("10월 13일", "13일") 시작일 기준으로 보완한다(끝 월이 시작 월보다 작으면 다음 해). 단일 날짜는
   * {@code null}(다음 영업일 보완은 Service), 구분자 뒤를 못 읽으면 {@code null}이면서
   * {@link #subscriptionEndUnreadable}이 true.
   */
  private static LocalDate parseSbdEndDate(String sbd) {
    String[] parts = splitSbd(sbd);
    if (parts == null || parts.length < 2) {
      return null;
    }
    LocalDate start = parseYmd(parts[0]);
    if (start == null) {
      return null;
    }
    List<Integer> n = numbers(parts[1]);
    try {
      return switch (n.size()) {
        case 3 -> n.get(0) >= 1000 ? LocalDate.of(n.get(0), n.get(1), n.get(2)) : null;
        case 2 ->
            LocalDate.of(
                n.get(0) < start.getMonthValue() ? start.getYear() + 1 : start.getYear(),
                n.get(0),
                n.get(1));
        case 1 -> LocalDate.of(start.getYear(), start.getMonthValue(), n.get(0));
        default -> null;
      };
    } catch (DateTimeException e) {
      return null;
    }
  }

  /** 괄호 주석 제거 + 물결 통일 후 분리. 값이 없거나 "-"면 {@code null}. 끝 구간이 비어 있어도 2개로 유지한다. */
  private static String[] splitSbd(String sbd) {
    if (sbd == null || sbd.isBlank() || sbd.trim().equals("-")) {
      return null;
    }
    return TILDE.matcher(PAREN.matcher(sbd).replaceAll(" ")).replaceAll("~").split("~", -1);
  }

  private static List<Integer> numbers(String raw) {
    List<Integer> result = new ArrayList<>();
    Matcher m = NUMBER.matcher(raw);
    while (m.find() && m.group().length() <= 4) {
      result.add(Integer.parseInt(m.group()));
    }
    return result;
  }

  /**
   * 숫자 3개(연·월·일)로 날짜를 만든다. "2026.10.12", "2026-10-12", "2026년 10월 12일 (월)" 등.
   * 월만 있으면 {@code null}.
   */
  private static LocalDate parseYmd(String raw) {
    if (raw == null) {
      return null;
    }
    List<Integer> n = numbers(PAREN.matcher(raw).replaceAll(" "));
    if (n.size() != 3 || n.get(0) < 1000) {
      return null;
    }
    try {
      return LocalDate.of(n.get(0), n.get(1), n.get(2));
    } catch (DateTimeException e) {
      return null;
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
