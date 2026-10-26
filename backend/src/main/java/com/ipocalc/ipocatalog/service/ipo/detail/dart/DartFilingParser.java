package com.ipocalc.ipocatalog.service.ipo.detail.dart;

import com.ipocalc.ipocatalog.service.ipo.detail.dart.ParsedFiling.Financial;
import com.ipocalc.ipocatalog.service.ipo.detail.dart.ParsedFiling.FloatStep;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DART document.xml 원문에서 공모주 상세 정보를 뽑는 파서. 정규식만 써서 XML 외부 엔티티를 해석하지 않는다(XXE 없음). 형식이 이상해도 예외를 던지지
 * 않고 못 찾은 항목은 null/빈 리스트로 둔다.
 */
public class DartFilingParser {

  /** 파싱 규칙을 바꾸면 올린다. 저장된 상세보다 높으면 같은 공시도 다시 파싱한다. */
  public static final int VERSION = 1;

  private static final Pattern TABLE = Pattern.compile("<TABLE[^>]*>(.*?)</TABLE>", Pattern.DOTALL);
  private static final Pattern ROW = Pattern.compile("<TR[^>]*>(.*?)</TR>", Pattern.DOTALL);
  private static final Pattern CELL =
      Pattern.compile("<(TD|TH|TE|TU)[^>]*>(.*?)</\\1>", Pattern.DOTALL);
  private static final Pattern TITLE = Pattern.compile("<TITLE[^>]*>(.*?)</TITLE>", Pattern.DOTALL);
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");
  private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern NUM = Pattern.compile("^[(△▲]?-?[\\d,]+(\\.\\d+)?\\)?$");
  private static final Pattern SUMMARY_TITLE = Pattern.compile("요약\\s*재무\\s*정보");
  private static final Pattern PERIOD = Pattern.compile("(제\\s*\\d+\\s*기|20\\d\\d\\s*(년|연도))");
  private static final Pattern UNIT = Pattern.compile("단위\\s*[:：]\\s*(백만원|천원|원)");
  // "Ⅰ. 매출액", "1. 자본금", "ㆍ자본금" 같은 항목 번호·글머리 접두
  private static final Pattern ROW_NO =
      Pattern.compile("^[ㆍ·•]?(?:(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩIVX]+|\\d+)[.．])?");
  // "경쟁률", "경쟁률주2)", "경쟁률(주2)" — 각주 표시가 붙은 라벨도 받는다
  private static final Pattern RATE_LABEL = Pattern.compile("경쟁률(\\(?주\\d+\\)?)?");
  // 최대주주 행을 가리키는 칸: "최대주주", "최대주주 등", "최대주주 본인"
  private static final Set<String> LARGEST = Set.of("최대주주", "최대주주등", "최대주주본인");
  private static final Set<String> NOT_NAMES =
      Set.of("최대주주", "최대주주등", "최대주주본인", "본인", "보통주", "우선주", "-");
  private static final Pattern NUM_SHARES = Pattern.compile("^[\\d,.]+주?$");
  private static final Pattern DECIMAL = Pattern.compile("^\\d+\\.\\d+$");

  /** 원문 한 표. pos는 원문 안의 시작 위치다. */
  private record Table(int pos, List<List<String>> rows) {}

  /**
   * 공시 원문을 파싱한다.
   *
   * @param xml DART document.xml 원문(null 허용)
   * @return 파싱 결과. 못 찾은 항목은 null 또는 빈 리스트
   */
  public ParsedFiling parse(String xml) {
    String s = xml == null ? "" : xml;
    List<Table> tables = safe(() -> tables(s), List.of());
    List<Financial> financials = new ArrayList<>();
    Long[] capital = new Long[1];
    safe(() -> summary(s, tables, financials, capital), null);
    String[] largest = safe(() -> largest(tables), null);
    return new ParsedFiling(
        List.copyOf(financials),
        safe(() -> floatSchedule(tables), List.of()),
        safe(() -> competitionRate(tables), null),
        safe(() -> lockUpCommitment(tables), null),
        largest == null ? null : largest[0],
        largest == null || largest[1] == null ? null : number(largest[1]),
        capital[0]);
  }

  private static <T> T safe(Supplier<T> supplier, T fallback) {
    try {
      T v = supplier.get();
      return v == null ? fallback : v;
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  private static List<Table> tables(String s) {
    List<Table> out = new ArrayList<>();
    Matcher t = TABLE.matcher(s);
    while (t.find()) {
      List<List<String>> rows = new ArrayList<>();
      Matcher r = ROW.matcher(t.group(1));
      while (r.find()) {
        List<String> cells = new ArrayList<>();
        Matcher c = CELL.matcher(r.group(1));
        while (c.find()) {
          cells.add(text(c.group(2)));
        }
        rows.add(cells);
      }
      out.add(new Table(t.start(), rows));
    }
    return out;
  }

  /** 태그 제거, HTML 엔티티 해제, 공백 정리. */
  private static String text(String raw) {
    String plain = TAG.matcher(raw).replaceAll("");
    Matcher m = ENTITY.matcher(plain);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(entity(m.group(1), m.group())));
    }
    m.appendTail(sb);
    return SPACES.matcher(sb).replaceAll(" ").trim();
  }

  private static String entity(String name, String original) {
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

  /** 쉼표·괄호·음수 부호를 처리해 숫자로. 숫자가 아니면 null. */
  private static BigDecimal number(String cell) {
    if (cell == null) {
      return null;
    }
    String c = cell.replace("%", "").replaceFirst("주$", "").trim();
    if (!NUM.matcher(c).matches()) {
      return null;
    }
    boolean negative =
        c.startsWith("(") || c.startsWith("-") || c.startsWith("△") || c.startsWith("▲");
    BigDecimal v = new BigDecimal(c.replaceAll("[()\\-,△▲]", ""));
    return negative ? v.negate() : v;
  }

  private static Long won(String cell, long multiplier) {
    BigDecimal v = number(cell);
    return v == null ? null : v.multiply(BigDecimal.valueOf(multiplier)).longValue();
  }

  // 요약재무정보 절 안의 표만 본다(공모가 산정의 비교회사 표를 잘못 집지 않도록).
  private static Void summary(String s, List<Table> tables, List<Financial> out, Long[] capital) {
    List<int[]> titlePos = new ArrayList<>();
    List<String> titleText = new ArrayList<>();
    Matcher m = TITLE.matcher(s);
    while (m.find()) {
      titlePos.add(new int[] {m.start()});
      titleText.add(text(m.group(1)));
    }
    for (int i = 0; i < titlePos.size() && out.isEmpty(); i++) {
      if (!SUMMARY_TITLE.matcher(titleText.get(i)).find()) {
        continue;
      }
      int from = titlePos.get(i)[0];
      int to = i + 1 < titlePos.size() ? titlePos.get(i + 1)[0] : s.length();
      section(s, tables, from, to, out, capital);
    }
    return null;
  }

  private static void section(
      String s, List<Table> tables, int from, int to, List<Financial> out, Long[] capital) {
    List<String> header = null;
    for (Table t : tables) {
      if (t.pos() <= from || t.pos() >= to) {
        continue;
      }
      List<String> own = firstPeriodHeader(t.rows());
      if (own != null) {
        header = own;
      }
      long unit = unit(s.substring(from, t.pos()));
      if (capital[0] == null) {
        capital[0] = capital(t.rows(), header, unit);
      }
      List<String> rev =
          row(
              t.rows(),
              n -> n.startsWith("매출액") || n.startsWith("영업수익") || n.startsWith("수익(매출액)"));
      List<String> op =
          row(t.rows(), n -> n.startsWith("영업이익") || n.startsWith("영업손익") || n.startsWith("영업손실"));
      if (rev == null || op == null || header == null) {
        continue;
      }
      List<String> ni = row(t.rows(), DartFilingParser::isNetIncome);
      for (int c = 0; c < header.size(); c++) {
        if (PERIOD.matcher(header.get(c)).find()) {
          out.add(
              new Financial(
                  header.get(c),
                  cellWon(rev, c, unit),
                  cellWon(op, c, unit),
                  cellWon(ni, c, unit)));
        }
      }
      return;
    }
  }

  private static boolean isNetIncome(String n) {
    boolean named = n.contains("순이익") || n.contains("순손익") || n.contains("순손실");
    return named && !n.contains("차감전") && !n.contains("총포괄") && !n.contains("지배");
  }

  private static Long cellWon(List<String> row, int col, long unit) {
    return row == null || col >= row.size() ? null : won(row.get(col), unit);
  }

  /** 첫 열(공백 제거, 앞의 항목 번호·"연결" 제거)이 조건에 맞는 첫 행. */
  private static List<String> row(List<List<String>> rows, Predicate<String> p) {
    for (List<String> r : rows) {
      if (!r.isEmpty() && p.test(rowName(r.get(0)))) {
        return r;
      }
    }
    return null;
  }

  private static String rowName(String cell) {
    String n = ROW_NO.matcher(cell.replace(" ", "")).replaceFirst("");
    return n.replaceFirst("^연결", "");
  }

  private static List<String> firstPeriodHeader(List<List<String>> rows) {
    for (List<String> r : rows) {
      if (r.stream().filter(c -> PERIOD.matcher(c).find()).count() >= 2) {
        return r;
      }
    }
    return null;
  }

  private static Long capital(List<List<String>> rows, List<String> header, long unit) {
    List<String> r = row(rows, n -> n.replaceAll("[\\[\\]]", "").equals("자본금"));
    if (r == null || header == null) {
      return null;
    }
    for (int c = 0; c < header.size(); c++) {
      if (PERIOD.matcher(header.get(c)).find()) {
        return cellWon(r, c, unit);
      }
    }
    return null;
  }

  /** 표 앞 구간에서 마지막 "(단위 : …)"를 원 배수로. 없으면 원. */
  private static long unit(String before) {
    Matcher m = UNIT.matcher(before);
    String last = null;
    while (m.find()) {
      last = m.group(1);
    }
    if (last == null) {
      return 1L;
    }
    return switch (last) {
      case "천원" -> 1_000L;
      case "백만원" -> 1_000_000L;
      default -> 1L;
    };
  }

  private static List<FloatStep> floatSchedule(List<Table> tables) {
    for (Table t : tables) {
      List<FloatStep> steps = new ArrayList<>();
      for (List<String> r : t.rows()) {
        if (r.size() >= 2
            && r.get(0).replace(" ", "").contains("유통가능")
            && r.get(0).contains("상장")
            && number(r.get(1)) != null) {
          steps.add(
              new FloatStep(
                  r.get(0), number(r.get(1)).longValue(), r.size() > 2 ? number(r.get(2)) : null));
        }
      }
      if (steps.size() >= 2) {
        return cumulative(steps);
      }
    }
    return null;
  }

  /** 주식수·비율 각각, 앞 단계보다 줄어들면 증분으로 적힌 표로 보고 누적으로 바꾼다. */
  private static List<FloatStep> cumulative(List<FloatStep> steps) {
    boolean sharesIncr = false;
    boolean ratioIncr = false;
    for (int i = 1; i < steps.size(); i++) {
      FloatStep a = steps.get(i - 1);
      FloatStep b = steps.get(i);
      sharesIncr |= b.shares() < a.shares();
      ratioIncr |= a.ratio() != null && b.ratio() != null && b.ratio().compareTo(a.ratio()) < 0;
    }
    List<FloatStep> out = new ArrayList<>();
    long shares = 0;
    BigDecimal ratio = BigDecimal.ZERO;
    for (FloatStep st : steps) {
      shares = sharesIncr ? shares + st.shares() : st.shares();
      if (ratioIncr) {
        ratio = st.ratio() == null || ratio == null ? null : ratio.add(st.ratio());
      } else {
        ratio = st.ratio();
      }
      out.add(new FloatStep(st.label(), shares, ratio));
    }
    return out;
  }

  private static BigDecimal competitionRate(List<Table> tables) {
    for (Table t : tables) {
      for (List<String> r : t.rows()) {
        if (!r.isEmpty() && RATE_LABEL.matcher(r.get(0).replace(" ", "")).matches()) {
          BigDecimal last = null;
          for (String c : r) {
            if (NUM.matcher(c).matches()) {
              last = number(c);
            }
          }
          if (last != null) {
            return last;
          }
        }
      }
    }
    return null;
  }

  // 합계 열 수량 기준: (1 - 미확약/합계) × 100.
  private static BigDecimal lockUpCommitment(List<Table> tables) {
    for (Table t : tables) {
      if (t.rows().isEmpty()
          || t.rows().get(0).stream().noneMatch(c -> c.replace(" ", "").equals("합계"))) {
        continue;
      }
      List<String> un = row(t.rows(), n -> n.equals("미확약"));
      List<String> total = row(t.rows(), n -> n.equals("계") || n.startsWith("합계"));
      if (un == null || total == null || un.size() < 2 || total.size() < 2) {
        continue;
      }
      BigDecimal tot = number(total.get(total.size() - 2));
      if (tot == null || tot.signum() == 0) {
        continue;
      }
      BigDecimal unc = number(un.get(un.size() - 2));
      if (unc == null) {
        unc = BigDecimal.ZERO;
      }
      return BigDecimal.ONE
          .subtract(unc.divide(tot, 10, RoundingMode.HALF_UP))
          .multiply(BigDecimal.valueOf(100))
          .setScale(2, RoundingMode.HALF_UP);
    }
    return null;
  }

  private static String[] largest(List<Table> tables) {
    for (Table t : tables) {
      boolean sale =
          t.rows().stream()
              .limit(3)
              .flatMap(List::stream)
              .map(c -> c.replace(" ", ""))
              .anyMatch(
                  c ->
                      c.contains("매각")
                          || c.contains("의무보유")
                          || c.contains("유통")
                          || c.contains("공모전")
                          || c.contains("공모후"));
      if (!sale) {
        continue;
      }
      for (List<String> r : t.rows()) {
        if (r.stream().anyMatch(c -> LARGEST.contains(c.replace(" ", "")))) {
          String name =
              r.stream()
                  .filter(
                      c ->
                          !c.isEmpty()
                              && !NOT_NAMES.contains(c.replace(" ", ""))
                              && !NUM_SHARES.matcher(c).matches()
                              && !c.endsWith("%"))
                  .findFirst()
                  .orElse(null);
          String pct =
              r.stream()
                  .filter(c -> c.endsWith("%"))
                  .findFirst()
                  .orElseGet(
                      () ->
                          r.stream()
                              .filter(c -> DECIMAL.matcher(c).matches())
                              .findFirst()
                              .orElse(null));
          return new String[] {name, pct};
        }
      }
    }
    return null;
  }
}
