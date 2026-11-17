package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DART document.xml 원문에서 공모 조건(희망공모가 범위, 확정공모가, 청약일정, 주식수)을 파싱한다.
 * 형식 이상, 숫자 파싱 실패, 날짜 역전은 예외를 던지지 않고 해당 필드를 null로 둔다.
 */
public class DartOfferingTermsParser {

  private static final Pattern TABLE = Pattern.compile("<TABLE[^>]*>(.*?)</TABLE>", Pattern.DOTALL);
  private static final Pattern ROW = Pattern.compile("<TR[^>]*>(.*?)</TR>", Pattern.DOTALL);
  private static final Pattern CELL = Pattern.compile("<(TD|TH|TE|TU)[^>]*>(.*?)</\\1>", Pattern.DOTALL);
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");
  private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern NUMBER = Pattern.compile("\\d+");
  private static final Pattern PAREN = Pattern.compile("\\([^)]*\\)|（[^）]*）");
  private static final Pattern TILDE = Pattern.compile("[~∼〜～]");
  private static final Pattern WON = Pattern.compile("원");

  // 파싱 중 임시 저장용 필드
  private Long lowerBandPrice;
  private Long upperBandPrice;
  private Long fixedPrice;
  private LocalDate subscriptionStartDate;
  private LocalDate subscriptionEndDate;
  private LocalDate allotmentDate;
  private LocalDate refundDate;
  private Long offeringShareCount;

  /**
   * document.xml 원문을 파싱하여 DartOfferingTerms를 반환한다.
   *
   * @param xml DART document.xml 원문
   * @param sourceReceiptNo 원문 출처 접수번호
   * @param isFinalizedReport 발행조건확정 공시 여부
   * @return 파싱된 공모 조건 (필드 파싱 실패 시 null)
   */
  public DartOfferingTerms parse(String xml, String sourceReceiptNo, boolean isFinalizedReport) {
    // 필드 초기화
    this.lowerBandPrice = null;
    this.upperBandPrice = null;
    this.fixedPrice = null;
    this.subscriptionStartDate = null;
    this.subscriptionEndDate = null;
    this.allotmentDate = null;
    this.refundDate = null;
    this.offeringShareCount = null;

    String s = xml == null ? "" : xml;
    List<List<List<String>>> tables = parseTables(s);

    // 1. 정정 전/정정 후 헤더를 가진 표 찾기 (발행조건확정 공시에서 확정가 추출용)
    // 2. 희망공모가/모집가액 문맥에서 밴드(두 가격) 또는 단일 확정가 찾기
    // 3. 청약기일, 배정공고일, 납입기일 표에서 일정 읽기
    // 4. 증권수량 표에서 공모주식수 읽기

    for (List<List<String>> table : tables) {
      // 표의 모든 행을 확인하여 어떤 표인지 식별 (헤더 행뿐만 아니라 데이터 행도 확인)
      if (table.isEmpty()) continue;
      StringBuilder allText = new StringBuilder();
      for (List<String> row : table) {
        for (String cell : row) {
          if (cell != null) allText.append(cell).append(" ");
        }
      }
      String tableText = allText.toString().replaceAll("\\s+", " ");

      // 정정 전/정정 후 표 식별
      if (tableText.contains("정정전") || tableText.contains("정정후") || tableText.contains("정정 전") || tableText.contains("정정 후")) {
        parseCorrectionTable(table, isFinalizedReport);
      }

      // 희망공모가/모집가액 표 식별
      if (tableText.contains("희망공모가") || tableText.contains("모집가액") || tableText.contains("매출가액")) {
        extractBandPrice(table);
      }

      // 청약기일/배정공고일/납입기일 표 식별
      if (tableText.contains("청약기일") || tableText.contains("청약일") ||
          tableText.contains("배정공고") || tableText.contains("배정일") ||
          tableText.contains("납입기일") || tableText.contains("환불일")) {
        extractDatesFromTable(table, tableText);
      }

      // 증권수량/공모주식수 표 식별
      if (tableText.contains("증권수량") || tableText.contains("공모주식") || tableText.contains("모집주식") || tableText.contains("주식수")) {
        offeringShareCount = extractOfferingShareCount(table);
      }
    }

    // 일반적인 청약기일 파싱 (sbd 필드와 유사한 패턴)
    // 원문 전체에서 청약기일 패턴 찾기
    if (subscriptionStartDate == null) {
      LocalDate[] dates = extractSubscriptionDatesFromText(s);
      if (dates != null) {
        subscriptionStartDate = dates[0];
        subscriptionEndDate = dates[1];
      }
    }

    // 배정공고일, 납입기일 원문 전체에서 찾기
    if (allotmentDate == null) {
      allotmentDate = extractDateFromText(s, "배정공고일");
    }
    if (refundDate == null) {
      refundDate = extractDateFromText(s, "납입기일");
    }

    return new DartOfferingTerms(
        sourceReceiptNo,
        DartOfferingTerms.PARSER_VERSION,
        lowerBandPrice,
        upperBandPrice,
        fixedPrice,
        subscriptionStartDate,
        subscriptionEndDate,
        allotmentDate,
        refundDate,
        offeringShareCount
    );
  }

  private List<List<List<String>>> parseTables(String xml) {
    List<List<List<String>>> tables = new ArrayList<>();
    Matcher t = TABLE.matcher(xml);
    while (t.find()) {
      List<List<String>> rows = new ArrayList<>();
      Matcher r = ROW.matcher(t.group(1));
      while (r.find()) {
        List<String> cells = new ArrayList<>();
        Matcher c = CELL.matcher(r.group(1));
        while (c.find()) {
          cells.add(cleanText(c.group(2)));
        }
        rows.add(cells);
      }
      tables.add(rows);
    }
    return tables;
  }

  private String cleanText(String raw) {
    String plain = TAG.matcher(raw).replaceAll("");
    Matcher m = ENTITY.matcher(plain);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(decodeEntity(m.group(1), m.group())));
    }
    m.appendTail(sb);
    return SPACES.matcher(sb).replaceAll(" ").trim();
  }

  private String decodeEntity(String name, String original) {
    return switch (name) {
      case "amp" -> "&";
      case "lt" -> "<";
      case "gt" -> ">";
      case "quot" -> "\"";
      case "apos" -> "'";
      case "nbsp" -> " ";
      default -> {
        if (name.charAt(0) != '#') yield original;
        boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
        int cp = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
        yield Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : original;
      }
    };
  }

  /** 정정 전/정정 후 표에서 확정가 추출 (발행조건확정 공시일 때만). */
  private void parseCorrectionTable(List<List<String>> table, boolean isFinalizedReport) {
    if (!isFinalizedReport) return;

    for (List<String> row : table) {
      for (int i = 0; i < row.size(); i++) {
        String cell = row.get(i);
        if (cell != null && (cell.contains("모집") || cell.contains("매출")) && cell.contains("가액")) {
          // 같은 행 또는 다음 셀에서 단일 가격 찾기
          String priceCell = (i + 1 < row.size()) ? row.get(i + 1) : null;
          Long price = parsePrice(priceCell != null ? priceCell : cell);
          if (price != null && price > 0) {
            this.fixedPrice = price;
            return;
          }
        }
      }
    }
  }

  /** 표에서 두 개의 가격(밴드) 추출. */
  private void extractBandPrice(List<List<String>> table) {
    for (List<String> row : table) {
      for (String cell : row) {
        // "9,400원 ~ 11,000원" 패턴 먼저 시도
        Long[] rangePrices = extractTwoPricesFromCell(cell);
        if (rangePrices != null && rangePrices[0] != null && rangePrices[1] != null) {
          this.lowerBandPrice = Math.min(rangePrices[0], rangePrices[1]);
          this.upperBandPrice = Math.max(rangePrices[0], rangePrices[1]);
          return;
        }
      }
    }
    // 범위 패턴이 없으면 단일 가격들을 수집해서 두 개가 있으면 밴드로 사용
    List<Long> prices = new ArrayList<>();
    for (List<String> row : table) {
      for (String cell : row) {
        Long price = parsePrice(cell);
        if (price != null && price > 0) {
          prices.add(price);
        }
      }
    }
    if (prices.size() >= 2) {
      this.lowerBandPrice = Math.min(prices.get(0), prices.get(1));
      this.upperBandPrice = Math.max(prices.get(0), prices.get(1));
    }
  }

  /** 셀에서 두 가격(범위) 추출: "9,400원 ~ 11,000원" 패턴. */
  private Long[] extractTwoPricesFromCell(String cell) {
    if (cell == null) return null;
    String cleaned = PAREN.matcher(cell).replaceAll(" ");
    Matcher tildeMatch = TILDE.matcher(cleaned);
    if (!tildeMatch.find()) return null;

    String left = cleaned.substring(0, tildeMatch.start()).trim();
    String right = cleaned.substring(tildeMatch.end()).trim();

    Long leftPrice = parsePrice(left);
    Long rightPrice = parsePrice(right);

    if (leftPrice != null && rightPrice != null) {
      return new Long[]{leftPrice, rightPrice};
    }
    return null;
  }

  /** 셀에서 단일 가격 추출. */
  private Long parsePrice(String cell) {
    if (cell == null) return null;
    String cleaned = PAREN.matcher(cell).replaceAll(" ");
    cleaned = WON.matcher(cleaned).replaceAll("");
    cleaned = cleaned.replaceAll(",", "").trim();
    if (cleaned.isEmpty()) return null;
    try {
      return Long.parseLong(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 표에서 날짜 추출. */
  private void extractDatesFromTable(List<List<String>> table, String headerText) {
    for (List<String> row : table) {
      for (String cell : row) {
        if (cell == null) continue;

        if (headerText.contains("청약")) {
          LocalDate[] dates = parseSubscriptionDateRange(cell);
          if (dates != null) {
            this.subscriptionStartDate = dates[0];
            this.subscriptionEndDate = dates[1];
          }
        } else if (headerText.contains("배정")) {
          LocalDate date = parseDate(cell);
          if (date != null) this.allotmentDate = date;
        } else if (headerText.contains("납입") || headerText.contains("환불")) {
          LocalDate date = parseDate(cell);
          if (date != null) this.refundDate = date;
        }
      }
    }
  }

  /** "2026년 11월 04일 ~ 2026년 11월 05일" 형태 파싱. */
  private LocalDate[] parseSubscriptionDateRange(String text) {
    String cleaned = PAREN.matcher(text).replaceAll(" ");
    Matcher tildeMatch = TILDE.matcher(cleaned);
    if (!tildeMatch.find()) return null;

    String left = cleaned.substring(0, tildeMatch.start()).trim();
    String right = cleaned.substring(tildeMatch.end()).trim();

    LocalDate start = parseDate(left);
    LocalDate end = parseDate(right);

    if (start != null && end != null) {
      return new LocalDate[]{start, end};
    }
    return null;
  }

  /** "2026년 11월 04일" 또는 "2026.11.04" 형태 파싱. */
  private LocalDate parseDate(String text) {
    if (text == null) return null;
    String cleaned = PAREN.matcher(text).replaceAll(" ").trim();
    List<Integer> nums = new ArrayList<>();
    Matcher m = NUMBER.matcher(cleaned);
    while (m.find() && nums.size() < 3) {
      try {
        nums.add(Integer.parseInt(m.group()));
      } catch (NumberFormatException ignored) {}
    }
    if (nums.size() != 3 || nums.get(0) < 1000) return null;
    try {
      return LocalDate.of(nums.get(0), nums.get(1), nums.get(2));
    } catch (Exception e) {
      return null;
    }
  }

  /** 원문 전체에서 청약기일 패턴 찾기. */
  private LocalDate[] extractSubscriptionDatesFromText(String text) {
    // "청약기일" 근처에서 날짜 범위 찾기
    int idx = text.indexOf("청약기일");
    if (idx < 0) idx = text.indexOf("청약일");
    if (idx < 0) return null;

    // 앞뒤 200자 내에서 검색
    int start = Math.max(0, idx - 50);
    int end = Math.min(text.length(), idx + 200);
    String snippet = text.substring(start, end);

    String cleaned = PAREN.matcher(snippet).replaceAll(" ");
    Matcher tildeMatch = TILDE.matcher(cleaned);
    if (!tildeMatch.find()) return null;

    String left = cleaned.substring(0, tildeMatch.start()).trim();
    String right = cleaned.substring(tildeMatch.end()).trim();

    LocalDate startDate = parseDate(left);
    LocalDate endDate = parseDate(right);

    if (startDate != null && endDate != null) {
      return new LocalDate[]{startDate, endDate};
    }
    return null;
  }

  /** 원문 전체에서 특정 라벨의 날짜 찾기. */
  private LocalDate extractDateFromText(String text, String label) {
    int idx = text.indexOf(label);
    if (idx < 0) return null;
    int start = Math.max(0, idx - 20);
    int end = Math.min(text.length(), idx + 100);
    String snippet = text.substring(start, end);
    return parseDate(snippet);
  }

  /** 증권수량에서 공모주식수 추출. */
  private Long extractOfferingShareCount(List<List<String>> table) {
    for (List<String> row : table) {
      for (String cell : row) {
        if (cell == null) continue;
        String cleaned = cell.replaceAll(",", "").trim();
        Matcher m = NUMBER.matcher(cleaned);
        if (m.find()) {
          try {
            long val = Long.parseLong(m.group());
            if (val > 0) return val;
          } catch (NumberFormatException ignored) {}
        }
      }
    }
    return null;
  }
}