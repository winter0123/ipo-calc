package com.ipocalc.ipocatalog.service.ipo.krx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** KIND 추가상장 공고 HTML을 행 기준으로 읽어 KRX 후보를 만든다. */
public class KrxListingNoticeParser {

  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  private static final Pattern SPACE = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern TABLE =
      Pattern.compile("<TABLE\\b[^>]*>(.*?)</TABLE>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern ROW =
      Pattern.compile("<TR\\b[^>]*>(.*?)</TR>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern CELL =
      Pattern.compile(
          "<(?:TD|TH|TE|TU)\\b[^>]*>(.*?)</(?:TD|TH|TE|TU)>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern DATE =
      Pattern.compile(
          "'(\\d{2})\\.(\\d{2})\\.(\\d{2})'"
              + "|(20\\d{2})[.년/\\-]\\s*(\\d{1,2})[.월/\\-]\\s*(\\d{1,2})(?:일)?");

  /** HTML만으로 파싱한다. */
  public KrxListingNotice parse(String html) {
    return parse(html, null);
  }

  /** HTML과 공고 URL로 파싱한다. */
  public KrxListingNotice parse(String html, String noticeUrl) {
    if (html == null || html.isBlank()) {
      throw new IllegalArgumentException("KIND 공고 HTML이 비어 있습니다");
    }
    List<List<String>> rows = allRows(html);

    String companyName = null;
    String stockCode = null;
    String stockType = null;
    Long summaryQuantity = null;
    LocalDate listingDate = null;

    for (List<String> row : rows) {
      String first = row.isEmpty() ? "" : row.get(0);
      if (first.contains("회사명")) {
        companyName = lastNonBlank(row);
      }
      if (first.contains("상장일")) {
        listingDate = parseDate(lastNonBlank(row));
      }
    }

    int headerIndex = -1;
    for (int i = 0; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      if (contains(row, "단축코드") && contains(row, "추가주식수")) {
        headerIndex = i;
        break;
      }
    }
    if (headerIndex >= 0 && headerIndex + 1 < rows.size()) {
      List<String> header = rows.get(headerIndex);
      List<String> value = new ArrayList<>(rows.get(headerIndex + 1));
      while (value.size() < header.size()) {
        value.add(0, "");
      }
      for (int c = 0; c < header.size() && c < value.size(); c++) {
        String h = header.get(c);
        String v = value.get(c);
        if (h.contains("주권종류")) {
          stockType = v;
        }
        if (h.contains("단축코드")) {
          stockCode = normalizeStockCode(v);
        }
        if (h.contains("추가주식수")) {
          summaryQuantity = parseLong(v);
        }
      }
    }

    // 발행내역 표 헤더 찾기
    int issueHeaderIndex = -1;
    for (int i = 0; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      if (contains(row, "주권종류") && contains(row, "추가사유") && contains(row, "발행회수")) {
        issueHeaderIndex = i;
        break;
      }
    }

    String reason = null;
    Long issuedShares = null;
    Long issuePrice = null;
    String roundLabel = null;
    int commonPaidInRowCount = 0;
    if (issueHeaderIndex >= 0) {
      for (int i = issueHeaderIndex + 1; i < rows.size(); i++) {
        List<String> row = rows.get(i);
        if (row.size() < 5) {
          break;
        }
        if (contains(row, "주권종류") && contains(row, "추가사유")) {
          break;
        }
        if (plainRow(row).contains("총") && row.size() < 6) {
          break;
        }
        String rowStockType = row.get(0);
        String rowReason = row.size() > 1 ? row.get(1) : "";
        if (!rowStockType.contains("보통주")) {
          continue;
        }
        if (!rowReason.contains("유상")) {
          continue;
        }
        commonPaidInRowCount++;
        if (commonPaidInRowCount == 1) {
          stockType = rowStockType;
          reason = rowReason;
          issuedShares = parseLong(row.get(3));
          issuePrice = parseLong(row.get(4));
          roundLabel = row.size() > 5 ? row.get(5) : null;
        }
      }
    }

    if (issuedShares == null) {
      issuedShares = summaryQuantity;
    }
    LocalDate issueDate = parseIssueDate(roundLabel);

    return new KrxListingNotice(
        noticeUrl,
        companyName,
        stockCode,
        stockType,
        reason,
        roundLabel,
        issuedShares,
        issuePrice,
        listingDate,
        issueDate);
  }

  /** 유상 보통주 발행 행 수를 센다. */
  public int countCommonPaidInRows(String html) {
    if (html == null || html.isBlank()) {
      return 0;
    }
    List<List<String>> rows = allRows(html);
    int issueHeaderIndex = -1;
    for (int i = 0; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      if (contains(row, "주권종류") && contains(row, "추가사유") && contains(row, "발행회수")) {
        issueHeaderIndex = i;
        break;
      }
    }
    if (issueHeaderIndex < 0) {
      return 0;
    }
    int count = 0;
    for (int i = issueHeaderIndex + 1; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      if (row.size() < 5) {
        break;
      }
      if (contains(row, "주권종류") && contains(row, "추가사유")) {
        break;
      }
      if (row.get(0).contains("보통주") && row.size() > 1 && row.get(1).contains("유상")) {
        count++;
      }
    }
    return count;
  }

  private static List<List<String>> allRows(String html) {
    List<List<String>> out = new ArrayList<>();
    Matcher t = TABLE.matcher(html);
    while (t.find()) {
      Matcher r = ROW.matcher(t.group(1));
      while (r.find()) {
        Matcher c = CELL.matcher(r.group(1));
        List<String> values = new ArrayList<>();
        while (c.find()) {
          values.add(plain(c.group(1)));
        }
        if (!values.isEmpty()) {
          out.add(values);
        }
      }
    }
    return out;
  }

  private static boolean contains(List<String> row, String label) {
    for (String cell : row) {
      if (cell.contains(label)) {
        return true;
      }
    }
    return false;
  }

  private static String lastNonBlank(List<String> row) {
    for (int i = row.size() - 1; i >= 0; i--) {
      if (!row.get(i).isBlank()) {
        return row.get(i);
      }
    }
    return row.isEmpty() ? null : row.get(0);
  }

  private static String plainRow(List<String> row) {
    return String.join(" ", row);
  }

  private static String normalizeStockCode(String value) {
    if (value == null) {
      return null;
    }
    String digits = value.replaceAll("[^0-9]", "");
    if (digits.isBlank()) {
      return value.trim();
    }
    if (digits.length() > 6) {
      digits = digits.substring(digits.length() - 6);
    }
    while (digits.length() < 6) {
      digits = "0" + digits;
    }
    return digits;
  }

  private static Long parseLong(String value) {
    if (value == null) {
      return null;
    }
    String digits = value.replaceAll("[^0-9]", "");
    if (digits.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static LocalDate parseDate(String value) {
    if (value == null) {
      return null;
    }
    Matcher m =
        Pattern.compile("(20\\d{2})[.년/\\-]\\s*(\\d{1,2})[.월/\\-]\\s*(\\d{1,2})(?:일)?")
            .matcher(value);
    if (!m.find()) {
      return null;
    }
    try {
      return LocalDate.of(
          Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static LocalDate parseIssueDate(String roundLabel) {
    if (roundLabel == null) {
      return null;
    }
    Matcher m = Pattern.compile("'(\\d{2})\\.(\\d{2})\\.(\\d{2})").matcher(roundLabel);
    if (!m.find()) {
      return null;
    }
    try {
      int yy = Integer.parseInt(m.group(1));
      int mm = Integer.parseInt(m.group(2));
      int dd = Integer.parseInt(m.group(3));
      return LocalDate.of(2000 + yy, mm, dd);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String plain(String html) {
    return SPACE.matcher(TAG.matcher(html).replaceAll(" ")).replaceAll(" ").trim();
  }
}
