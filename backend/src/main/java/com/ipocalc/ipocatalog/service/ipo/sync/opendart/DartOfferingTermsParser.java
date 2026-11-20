package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * DART document.xml 원문에서 공모 조건(희망공모가 범위, 확정공모가, 청약일정, 주식수)을 파싱한다. 형식 이상, 숫자 파싱 실패, 날짜 역전은 예외를 던지지
 * 않고 해당 필드를 null로 둔다.
 *
 * <p>파싱 상태는 호출마다 새로 만들어 지역에 보관하므로 싱글턴 빈으로 공유해도 안전하다.
 */
@Component
public class DartOfferingTermsParser {

  private static final Pattern TABLE = Pattern.compile("<TABLE[^>]*>(.*?)</TABLE>", Pattern.DOTALL);
  private static final Pattern ROW = Pattern.compile("<TR[^>]*>(.*?)</TR>", Pattern.DOTALL);
  private static final Pattern CELL =
      Pattern.compile("<(TD|TH|TE|TU)[^>]*>(.*?)</\\1>", Pattern.DOTALL);
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");
  private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern NUMBER = Pattern.compile("\\d+");
  private static final Pattern PAREN = Pattern.compile("\\([^)]*\\)|（[^）]*）");
  private static final Pattern SUBSCRIPTION_LABEL = Pattern.compile("청약(?:기일|일|기간)");
  private static final String DATE = "\\d{4}\\s*[년./-]\\s*\\d{1,2}\\s*[월./-]\\s*\\d{1,2}\\s*일?";
  private static final Pattern SUBSCRIPTION_RANGE =
      Pattern.compile("(" + DATE + ")\\s*[~∼〜～]\\s*(" + DATE + ")");
  private static final Pattern BREAK = Pattern.compile("<BR\\s*/?>", Pattern.CASE_INSENSITIVE);
  private static final String AMOUNT = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)";
  private static final String BAND_LABEL = "(?:희망\\s*공모\\s*가(?:격|액)?|공모\\s*희망\\s*가(?:격|액)?)";
  private static final String FIXED_LABEL =
      "(?:확정\\s*공모\\s*가(?:격|액)?|(?:주당\\s*|단위당\\s*)?" + "(?:모집(?:[(（]매출[)）])?|매출)\\s*가액)";
  private static final String RANGE = "(" + AMOUNT + ")\\s*원?\\s*[~∼〜～]\\s*(" + AMOUNT + ")\\s*원?";
  private static final Pattern BAND_HEADER = Pattern.compile(BAND_LABEL);
  private static final Pattern INLINE_BAND =
      Pattern.compile(BAND_LABEL + "(?:\\s*인)?\\s*[:：]?\\s*" + RANGE + "(?![0-9,])");
  private static final Pattern BAND_VALUE = Pattern.compile(RANGE);
  private static final Pattern FIXED_HEADER = Pattern.compile(FIXED_LABEL);
  private static final Pattern FIXED_VALUE = Pattern.compile("(" + AMOUNT + ")\\s*원?");
  private static final Pattern INLINE_FIXED =
      Pattern.compile(
          FIXED_LABEL + "\\s*[:：]\\s*(" + AMOUNT + ")\\s*원" + "(?=\\s*(?:$|[-•]\\s*[^0-9~∼〜～]))");

  private record SubscriptionDates(LocalDate start, LocalDate end) {}

  /** 한 번의 파싱 동안만 쓰는 가변 상태. */
  private static final class ParseState {
    private Long lowerBandPrice;
    private Long upperBandPrice;
    private LocalDate allotmentDate;
    private LocalDate refundDate;
    private Long offeringShareCount;
  }

  /**
   * document.xml 원문을 파싱하여 DartOfferingTerms를 반환한다.
   *
   * @param xml DART document.xml 원문
   * @param sourceReceiptNo 원문 출처 접수번호
   * @param isFinalizedReport 발행조건확정 공시 여부
   * @return 파싱된 공모 조건 (필드 파싱 실패 시 null)
   */
  public DartOfferingTerms parse(String xml, String sourceReceiptNo, boolean isFinalizedReport) {
    ParseState state = new ParseState();

    String s = xml == null ? "" : xml;
    List<List<List<String>>> tables = parseTables(s);

    Set<Long> fixedPrices = new HashSet<>();
    Set<Long> verifiedShareCounts = new HashSet<>();
    Set<SubscriptionDates> subscriptionDates = new HashSet<>();
    for (List<List<String>> table : tables) {
      if (table.isEmpty()) {
        continue;
      }
      StringBuilder allText = new StringBuilder();
      for (List<String> row : table) {
        for (String cell : row) {
          allText.append(cell).append(" ");
        }
      }
      String tableText = SPACES.matcher(allText).replaceAll(" ");
      if (isFinalizedReport) {
        collectFixedPrices(table, fixedPrices);
      } else {
        extractBandPrice(table, state);
      }

      collectSubscriptionDates(table, subscriptionDates);

      // 배정공고일/납입기일 표 식별
      if (!tableText.contains("청약")
          && (tableText.contains("배정공고")
              || tableText.contains("배정일")
              || tableText.contains("납입기일")
              || tableText.contains("환불일"))) {
        extractDatesFromTable(table, tableText, state);
      }

      // 증권수량/공모주식수 표 식별
      if (tableText.contains("증권수량")
          || tableText.contains("공모주식")
          || tableText.contains("모집주식")
          || tableText.contains("주식수")) {
        Long shareCount = extractOfferingShareCount(table);
        if (shareCount != null) {
          verifiedShareCounts.add(shareCount);
        }
      }
    }

    // 배정공고일, 납입기일 원문 전체에서 찾기
    if (state.allotmentDate == null) {
      state.allotmentDate = extractDateFromText(s, "배정공고일");
    }
    if (state.refundDate == null) {
      state.refundDate = extractDateFromText(s, "납입기일");
    }

    state.offeringShareCount =
        verifiedShareCounts.size() == 1 ? verifiedShareCounts.iterator().next() : null;

    SubscriptionDates schedule =
        subscriptionDates.size() == 1 ? subscriptionDates.iterator().next() : null;

    return new DartOfferingTerms(
        sourceReceiptNo,
        DartOfferingTerms.PARSER_VERSION,
        state.lowerBandPrice,
        state.upperBandPrice,
        fixedPrices.size() == 1 ? fixedPrices.iterator().next() : null,
        schedule == null ? null : schedule.start(),
        schedule == null ? null : schedule.end(),
        state.allotmentDate,
        state.refundDate,
        state.offeringShareCount);
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
    String plain =
        TAG.matcher(BREAK.matcher(SPACES.matcher(raw).replaceAll(" ")).replaceAll("\n"))
            .replaceAll("");
    Matcher m = ENTITY.matcher(plain);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(decodeEntity(m.group(1), m.group())));
    }
    m.appendTail(sb);
    return sb.toString()
        .lines()
        .map(line -> SPACES.matcher(line).replaceAll(" ").trim())
        .collect(Collectors.joining("\n"))
        .trim();
  }

  private String decodeEntity(String name, String original) {
    switch (name) {
      case "amp":
        return "&";
      case "lt":
        return "<";
      case "gt":
        return ">";
      case "quot":
        return "\"";
      case "apos":
        return "'";
      case "nbsp":
        return " ";
      default:
        if (name.charAt(0) != '#') {
          return original;
        }
        boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
        int cp = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
        return Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : original;
    }
  }

  /** 표마다 정정 후 열을 찾아 주당 확정가 후보를 수집한다. */
  private void collectFixedPrices(List<List<String>> table, Set<Long> fixedPrices) {
    int afterColumn = -1;
    int labelColumn = -1;
    for (List<String> row : table) {
      int nextAfterColumn = -1;
      int nextLabelColumn = -1;
      for (int column = 0; column < row.size(); column++) {
        String cell = SPACES.matcher(row.get(column)).replaceAll("");
        if (cell.equals("정정후")) {
          nextAfterColumn = column;
        } else if (cell.equals("항목") || cell.equals("항목명")) {
          nextLabelColumn = column;
        }
      }
      if (nextAfterColumn >= 0) {
        afterColumn = nextAfterColumn;
        labelColumn = nextLabelColumn;
        continue;
      }
      if (afterColumn < 0 || afterColumn >= row.size()) {
        continue;
      }
      String after = row.get(afterColumn);
      if (labelColumn >= 0
          && labelColumn < row.size()
          && FIXED_HEADER.matcher(row.get(labelColumn)).matches()) {
        Matcher value = FIXED_VALUE.matcher(after);
        if (value.matches()) {
          addFixedPrice(value.group(1), fixedPrices);
        }
      }
      for (String line : after.lines().toList()) {
        Matcher inlinePrice = INLINE_FIXED.matcher(line);
        while (inlinePrice.find()) {
          addFixedPrice(inlinePrice.group(1), fixedPrices);
        }
      }
    }
  }

  private void addFixedPrice(String amount, Set<Long> fixedPrices) {
    Long price = parseAmount(amount);
    if (price != null && price > 0) {
      fixedPrices.add(price);
    }
  }

  /** 희망공모가가 명시된 셀이나 그 행·열에서만 범위를 읽는다. */
  private void extractBandPrice(List<List<String>> table, ParseState state) {
    if (state.lowerBandPrice != null) {
      return;
    }
    for (int index = 0; index < table.size(); index++) {
      List<String> row = table.get(index);
      for (int column = 0; column < row.size(); column++) {
        String cell = row.get(column);
        Matcher inline = INLINE_BAND.matcher(cell);
        if (inline.find() && applyBand(inline, state)) {
          return;
        }
        if (!BAND_HEADER.matcher(cell).matches()) {
          continue;
        }
        if (column + 1 < row.size() && applyBandValue(row.get(column + 1), state)) {
          return;
        }
        if (index + 1 < table.size()
            && column < table.get(index + 1).size()
            && applyBandValue(table.get(index + 1).get(column), state)) {
          return;
        }
      }
    }
  }

  private boolean applyBandValue(String cell, ParseState state) {
    Matcher value = BAND_VALUE.matcher(cell);
    return value.matches() && applyBand(value, state);
  }

  private boolean applyBand(Matcher value, ParseState state) {
    Long low = parseAmount(value.group(1));
    Long high = parseAmount(value.group(2));
    if (low == null || high == null || low <= 0 || low >= high) {
      return false;
    }
    state.lowerBandPrice = low;
    state.upperBandPrice = high;
    return true;
  }

  private Long parseAmount(String amount) {
    try {
      return Long.valueOf(amount.replace(",", ""));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /** 표에서 날짜 추출. */
  private void extractDatesFromTable(
      List<List<String>> table, String headerText, ParseState state) {
    for (List<String> row : table) {
      for (String cell : row) {
        if (cell == null) {
          continue;
        }

        if (headerText.contains("배정")) {
          LocalDate date = parseDate(cell);
          if (date != null) {
            state.allotmentDate = date;
          }
        } else if (headerText.contains("납입") || headerText.contains("환불")) {
          LocalDate date = parseDate(cell);
          if (date != null) {
            state.refundDate = date;
          }
        }
      }
    }
  }

  /** 청약기일 라벨에 대응하는 셀에서만 날짜 범위를 수집한다. */
  private void collectSubscriptionDates(
      List<List<String>> table, Set<SubscriptionDates> candidates) {
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      List<String> row = table.get(rowIndex);
      List<String> nextRow = rowIndex + 1 < table.size() ? table.get(rowIndex + 1) : List.of();
      for (int column = 0; column < row.size(); column++) {
        String label = SPACES.matcher(row.get(column)).replaceAll("");
        if (!SUBSCRIPTION_LABEL.matcher(label).matches()) {
          continue;
        }
        if (row.size() == 2 && column == 0) {
          addSubscriptionDates(row.get(1), candidates);
        }
        if (nextRow.size() == row.size()) {
          addSubscriptionDates(nextRow.get(column), candidates);
        }
      }
    }
  }

  private void addSubscriptionDates(String value, Set<SubscriptionDates> candidates) {
    Matcher range = SUBSCRIPTION_RANGE.matcher(PAREN.matcher(value).replaceAll(" ").trim());
    if (!range.matches()) {
      return;
    }
    LocalDate start = parseDate(range.group(1));
    LocalDate end = parseDate(range.group(2));
    if (start != null && end != null && !end.isBefore(start)) {
      candidates.add(new SubscriptionDates(start, end));
    }
  }

  /** "2026년 11월 04일" 또는 "2026.11.04" 형태 파싱. */
  private LocalDate parseDate(String text) {
    if (text == null) {
      return null;
    }
    String cleaned = PAREN.matcher(text).replaceAll(" ").trim();
    List<Integer> nums = new ArrayList<>();
    Matcher m = NUMBER.matcher(cleaned);
    while (m.find() && nums.size() < 3) {
      try {
        nums.add(Integer.parseInt(m.group()));
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    if (nums.size() != 3 || nums.get(0) < 1000) {
      return null;
    }
    try {
      return LocalDate.of(nums.get(0), nums.get(1), nums.get(2));
    } catch (Exception e) {
      return null;
    }
  }

  /** 원문 전체에서 특정 라벨의 날짜 찾기. */
  private LocalDate extractDateFromText(String text, String label) {
    int idx = text.indexOf(label);
    if (idx < 0) {
      return null;
    }
    int start = Math.max(0, idx - 20);
    int end = Math.min(text.length(), idx + 100);
    String snippet = text.substring(start, end);
    return parseDate(snippet);
  }

  /** 수량 라벨과 대응 셀에서만 공모주식수를 읽는다. 총액·액면가·타 표 숫자는 제외한다. */
  private Long extractOfferingShareCount(List<List<String>> table) {
    Set<Long> candidates = new HashSet<>();
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      List<String> row = table.get(rowIndex);
      for (int column = 0; column < row.size(); column++) {
        String label = SPACES.matcher(row.get(column)).replaceAll("");
        if (!label.matches("(?:증권수량|공모주식수|모집주식수|주식수|발행주식수)")) {
          continue;
        }
        if (column + 1 < row.size()) {
          addShareCount(row.get(column + 1), candidates);
        }
        if (rowIndex + 1 < table.size() && column < table.get(rowIndex + 1).size()) {
          addShareCount(table.get(rowIndex + 1).get(column), candidates);
        }
      }
    }
    return candidates.size() == 1 ? candidates.iterator().next() : null;
  }

  private void addShareCount(String value, Set<Long> candidates) {
    String trimmed = SPACES.matcher(value).replaceAll("").replace("주", "");
    if (!trimmed.matches("[0-9][0-9,]*")) {
      return;
    }
    Long count = parseAmount(trimmed);
    if (count != null && count > 0) {
      candidates.add(count);
    }
  }
}
