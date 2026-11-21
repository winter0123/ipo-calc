package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 증권발행실적보고서 원문에서 유상증자 신주의 상장예정일만 추출한다. 신주인수권증서·무상증자 관련 날짜나 문맥이 불분명한 날짜는 사용하지 않는다. */
public class DartRightsIssueListingParser {
  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  private static final Pattern SPACE = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern PARAGRAPH =
      Pattern.compile("<P\\b[^>]*>(.*?)</P>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern TABLE =
      Pattern.compile("<TABLE\\b[^>]*>(.*?)</TABLE>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern ROW =
      Pattern.compile("<TR\\b[^>]*>(.*?)</TR>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern CELL =
      Pattern.compile(
          "<(?:TD|TH|TE|TU)\\b[^>]*>(.*?)</(?:TD|TH|TE|TU)>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern DATE =
      Pattern.compile("(20\\d{2})[.년/\\-]\\s*(\\d{1,2})[.월/\\-]\\s*(\\d{1,2})(?:일)?");
  private static final Pattern ORIGINAL =
      Pattern.compile("(?:원\\s*신고서|최초\\s*신고서|기준\\s*신고서)[^0-9]{0,80}(20\\d{12})");
  private static final Pattern SHARES = Pattern.compile("([\\d,]+)\\s*주");
  private static final Pattern LISTING =
      Pattern.compile(
          "(?:신주|주권).{0,35}?(?:상장|유통).{0,30}?(?:예정일|개시일|상장일|예정)"
              + "|(?:상장|유통).{0,24}(?:신주|주권).{0,25}(?:예정일|개시일)");
  private static final Pattern PAID = Pattern.compile("(?:납입일|주금납입일|납입기일)");
  private static final Pattern ISSUED =
      Pattern.compile(
          "(?:유상증자.{0,20}(?:신주|발행.{0,8}주식)|신주\\s*발행\\s*(?:주식)?\\s*수"
              + "|금번\\s*발행\\s*주식\\s*수|발행할\\s*신주"
              + "|신주\\s*(?:의)?\\s*(?:종류.{0,6})?수)");
  private static final Pattern OLD_SHARES =
      Pattern.compile("(?:기발행|기존\\s*발행|현재\\s*발행|발행주식총수|총발행주식수)");
  private static final Pattern EXCLUDED =
      Pattern.compile("(?:무상증자|무상신주|신주인수권증서|신주인수권\\s*증서|채권|전환사채)");

  /** 표에서 추출한 회차 증거. 원신고번호 또는 납입일·유상 신주 수로 매칭한다. */
  public record Evidence(
      LocalDate listingDate, String originalReceipt, LocalDate paymentDate, Long issuedShares) {}

  /** 발행 회차의 납입일과 이번 유상증자로 발행하는 신주 수. */
  public record IssueIdentity(LocalDate paymentDate, Long issuedShares) {}

  /** 원공시와 결과보고서에 동일한 규칙을 적용한다. 환불일이나 기존 발행주식총수는 쓰지 않는다. */
  public IssueIdentity issueIdentity(String xml) {
    String document = xml == null ? "" : xml;
    List<List<String>> allRows = rows(document);
    List<String> paragraphs = paragraphs(document);
    return new IssueIdentity(
        paymentDate(document, allRows, paragraphs), firstShares(allRows, paragraphs));
  }

  /** 표뿐 아니라 P 문단의 상장·유통개시 예정일을 읽고, 무상증자·신주인수권 일정은 제외한다. */
  public List<Evidence> parse(String xml) {
    if (xml == null || xml.isBlank()) {
      return List.of();
    }
    String document = xml.replace("&nbsp;", " ").replace("&amp;", "&");
    Matcher originalMatcher = ORIGINAL.matcher(plain(document));
    String original = originalMatcher.find() ? originalMatcher.group(1) : null;
    List<List<String>> allRows = rows(document);
    List<String> paragraphs = paragraphs(document);
    IssueIdentity identity = issueIdentity(document);
    List<LocalDate> dates = new ArrayList<>();
    for (List<String> row : allRows) {
      if (row.size() >= 2 && isListingLabel(row.get(0))) {
        for (int i = 1; i < row.size(); i++) {
          addDate(dates, row.get(i));
        }
      }
    }
    for (int i = 0; i < paragraphs.size(); i++) {
      String paragraph = paragraphs.get(i);
      if (!LISTING.matcher(paragraph).find()) {
        continue;
      }
      int before = dates.size();
      addListingDates(dates, paragraph);
      // 보고서의 <P>상장예정일</P><P>2026.03.10</P> 형태도 지원한다.
      if (dates.size() == before
          && !EXCLUDED.matcher(paragraph).find()
          && i + 1 < paragraphs.size()) {
        String following = paragraphs.get(i + 1);
        if (!EXCLUDED.matcher(following).find() && !LISTING.matcher(following).find()) {
          addDate(dates, following);
        }
      }
    }
    return dates.stream()
        .map(date -> new Evidence(date, original, identity.paymentDate(), identity.issuedShares()))
        .toList();
  }

  /** 동일 문단에 등기예정일과 상장예정일이 있으면 상장 표제 뒤의 날짜만 추출한다. 다음 일정 표제가 시작되기 전에 있는 첫 날짜만 사용한다. */
  private static void addListingDates(List<LocalDate> dates, String paragraph) {
    Matcher label = LISTING.matcher(paragraph);
    while (label.find()) {
      int start = label.end();
      int nextLabel = paragraph.length();
      Matcher later = LISTING.matcher(paragraph);
      if (later.find(start)) {
        nextLabel = later.start();
      }
      String prefix = paragraph.substring(Math.max(0, label.start() - 24), label.start());
      String tail = paragraph.substring(start, nextLabel);
      Matcher excluded = EXCLUDED.matcher(tail);
      if (excluded.find()) {
        tail = tail.substring(0, excluded.start());
      }
      // 신주인수권증서·무상증자 표제의 날짜는 유상 신주 날짜가 아니다.
      if (EXCLUDED.matcher(prefix).find() || EXCLUDED.matcher(label.group()).find()) {
        continue;
      }
      addDate(dates, tail);
    }
  }

  /** '청약 및 납입일정' 표에서 납입기일 열을 우선 읽는다. 종전 발행내역의 납입일(예: 2019년)은 현재 회차 증거로 채택하지 않는다. */
  private static LocalDate paymentDate(
      String document, List<List<String>> allRows, List<String> paragraphs) {
    Matcher tables = TABLE.matcher(document);
    while (tables.find()) {
      String tableText = tables.group(1);
      int leadStart = Math.max(0, tables.start() - 240);
      String context = plain(document.substring(leadStart, tables.start()));
      if (!(context.contains("청약") && context.contains("납입"))
          && !(plain(tableText).contains("청약") && plain(tableText).contains("납입"))) {
        continue;
      }
      List<List<String>> tableRows = rows(tableText);
      for (int headerIndex = 0; headerIndex < tableRows.size(); headerIndex++) {
        List<String> header = tableRows.get(headerIndex);
        for (int col = 0; col < header.size(); col++) {
          String name = header.get(col).replace(" ", "");
          if (!PAID.matcher(name).find()) {
            continue;
          }
          // 납입기일이 TH 헤더인 경우 대응하는 세로 열의 값을 사용한다.
          for (int next = headerIndex + 1; next < tableRows.size(); next++) {
            List<String> values = tableRows.get(next);
            if (col < values.size()) {
              LocalDate date = parseDate(values.get(col));
              if (date != null) {
                return date;
              }
            }
          }
        }
      }
    }
    // 명시적 현재 발행 일정에 국한된 행만 읽는다.
    for (List<String> row : allRows) {
      if (row.size() >= 2 && PAID.matcher(row.get(0)).find()) {
        LocalDate date = parseDate(row.get(1));
        if (date != null) {
          return date;
        }
      }
    }
    // 문단은 첫 출현 일자를 쓰지 않고 청약/납입 문맥으로 좁힌다.
    for (int i = 0; i < paragraphs.size(); i++) {
      String p = paragraphs.get(i);
      if (!PAID.matcher(p).find()) {
        continue;
      }
      boolean currentIssue = p.contains("청약") || p.contains("유상증자");
      if (!currentIssue) {
        for (int nearby = Math.max(0, i - 3); nearby < i; nearby++) {
          if (isNewRightsSharesLabel(paragraphs.get(nearby))
              && (paragraphs.get(nearby).contains("유상증자")
                  || paragraphs.get(nearby).contains("금번")
                  || paragraphs.get(nearby).contains("이번"))) {
            currentIssue = true;
            break;
          }
        }
      }
      if (!currentIssue) {
        continue;
      }
      LocalDate date = parseDate(p.substring(p.indexOf("납입")));
      if (date == null && i + 1 < paragraphs.size()) {
        date = parseDate(paragraphs.get(i + 1));
      }
      if (date != null) {
        return date;
      }
    }
    return null;
  }

  private static boolean isListingLabel(String text) {
    return LISTING.matcher(text.replace(" ", "")).find() && !EXCLUDED.matcher(text).find();
  }

  private static void addDate(List<LocalDate> dates, String text) {
    if (EXCLUDED.matcher(text).find()) {
      return;
    }
    LocalDate date = parseDate(text);
    if (date != null && !dates.contains(date)) {
      dates.add(date);
    }
  }

  private static List<String> paragraphs(String xml) {
    List<String> result = new ArrayList<>();
    Matcher p = PARAGRAPH.matcher(xml);
    while (p.find()) {
      String value = plain(p.group(1));
      if (!value.isBlank()) {
        result.add(value);
      }
    }
    return result;
  }

  private static LocalDate firstDate(List<List<String>> rows, List<String> paragraphs) {
    LocalDate payment = firstRowDate(rows, PAID);
    if (payment != null) {
      return payment;
    }
    for (int i = 0; i < paragraphs.size(); i++) {
      String paragraph = paragraphs.get(i);
      if (!PAID.matcher(paragraph).find()) {
        continue;
      }
      payment = parseDate(paragraph);
      if (payment == null && i + 1 < paragraphs.size()) {
        payment = parseDate(paragraphs.get(i + 1));
      }
      if (payment != null) {
        return payment;
      }
    }
    return null;
  }

  private static LocalDate firstRowDate(List<List<String>> rows, Pattern label) {
    for (List<String> row : rows) {
      if (row.size() >= 2 && label.matcher(row.get(0)).find()) {
        LocalDate value = parseDate(row.get(1));
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }

  private static Long firstShares(List<List<String>> rows, List<String> paragraphs) {
    for (List<String> row : rows) {
      if (row.size() >= 2 && isNewRightsSharesLabel(row.get(0))) {
        Long shares = null;
        boolean conflicting = false;
        for (int i = 1; i < row.size(); i++) {
          Long value = extractShares(row.get(i));
          if (value == null) {
            continue;
          }
          if (shares != null && !shares.equals(value)) {
            conflicting = true;
            break;
          }
          shares = value;
        }
        if (!conflicting && shares != null) {
          return shares;
        }
      }
    }
    for (int i = 0; i < paragraphs.size(); i++) {
      String paragraph = paragraphs.get(i);
      if (!isNewRightsSharesLabel(paragraph)) {
        continue;
      }
      // 같은 문단에 기존 주식수도 함께 있으면 수치 위치가 불분명해 사용하지 않는다.
      if (OLD_SHARES.matcher(paragraph).find()) {
        continue;
      }
      Long shares = extractShares(paragraph);
      if (shares == null && i + 1 < paragraphs.size()) {
        String following = paragraphs.get(i + 1);
        if (!OLD_SHARES.matcher(following).find()) {
          shares = extractShares(following);
        }
      }
      if (shares != null) {
        return shares;
      }
    }
    return null;
  }

  private static boolean isNewRightsSharesLabel(String label) {
    return ISSUED.matcher(label.replace(" ", "")).find()
        && !OLD_SHARES.matcher(label).find()
        && !EXCLUDED.matcher(label).find();
  }

  private static Long extractShares(String text) {
    Matcher shares = SHARES.matcher(text);
    if (!shares.find() || shares.find()) {
      // 서로 다른 두 수치가 있는 문단은 임의의 첫 주식수를 선택하지 않는다.
      return null;
    }
    Matcher single = SHARES.matcher(text);
    if (!single.find()) {
      return null;
    }
    try {
      return Long.parseLong(single.group(1).replace(",", ""));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static List<List<String>> rows(String xml) {
    List<List<String>> out = new ArrayList<>();
    Matcher tr = ROW.matcher(xml);
    while (tr.find()) {
      Matcher cell = CELL.matcher(tr.group(1));
      List<String> values = new ArrayList<>();
      while (cell.find()) {
        values.add(plain(cell.group(1)));
      }
      if (!values.isEmpty()) {
        out.add(values);
      }
    }
    return out;
  }

  private static String plain(String xml) {
    return SPACE
        .matcher(TAG.matcher(xml).replaceAll(" ").replace("&nbsp;", " "))
        .replaceAll(" ")
        .trim();
  }

  private static LocalDate parseDate(String s) {
    Matcher m = DATE.matcher(s);
    if (!m.find()) {
      return null;
    }
    try {
      return LocalDate.of(
          Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
