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
  private static final Pattern QUANTITY_FIELD =
      Pattern.compile(
          "<(TD|TH|TE|TU)\\b([^>]*?)>(.*?)</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern QUANTITY_CODE =
      Pattern.compile(
          "\\b(?:AUNIT|ACODE|AID|ID|NAME)\\s*=\\s*['\"]"
              + "(STK_CNT|INC_CNT_TOT|INC_SL_CNT_SUM)['\"]",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern QUANTITY_ELEMENT =
      Pattern.compile(
          "<(STK_CNT|INC_CNT_TOT|INC_SL_CNT_SUM)\\b[^>]*>(.*?)</\\1\\s*>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern QUANTITY_VALUE =
      Pattern.compile("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\s*주)?");
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
    return issueIdentity(xml, false);
  }

  /** 결과보고서에는 모집수량 대신 발행실적 합계 코드만 사용한다. */
  private IssueIdentity issueIdentity(String xml, boolean issuanceResult) {
    String document = xml == null ? "" : xml;
    List<List<String>> allRows = rows(document);
    List<String> paragraphs = paragraphs(document);
    return new IssueIdentity(
        paymentDate(document, allRows, paragraphs), structuredShares(document, issuanceResult));
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
    IssueIdentity identity = issueIdentity(document, true);
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

  /** 문서의 실제 절 경계 안에서 현재 유상증자 실권주 처리 근거만 인정한다. */
  public boolean hasVerifiedUnissuedShares(String xml, long planned, long issued) {
    if (xml == null || issued <= 0 || planned <= issued) {
      return false;
    }
    long difference = planned - issued;
    boolean structured = Pattern.compile("(?i)<SECTION-[1-3]\b").matcher(xml).find();
    Pattern tokens =
        Pattern.compile(
            "(?is)</?SECTION-[1-3]\b[^>]*>|<TITLE\b[^>]*>.*?</TITLE>"
                + "|<TABLE\b[^>]*>.*?</TABLE>|<P\b[^>]*>.*?</P>");
    Matcher matcher = tokens.matcher(xml);
    List<String> sections = new ArrayList<>();
    String legacyTitle = "";
    while (matcher.find()) {
      String token = matcher.group();
      if (token.matches("(?is)<SECTION-[1-3]\b[^>]*>")) {
        sections.add("");
      } else if (token.matches("(?is)</SECTION-[1-3]\s*>")) {
        if (!sections.isEmpty()) {
          sections.remove(sections.size() - 1);
        }
      } else if (token.matches("(?is)<TITLE\b.*")) {
        String title = plain(token);
        if (structured && !sections.isEmpty()) {
          sections.set(sections.size() - 1, title);
        } else if (!structured) {
          legacyTitle = title;
        }
      } else if (!structured && token.matches("(?is)<P\b.*")
          && isLegacySectionHeading(plain(token))) {
        legacyTitle = plain(token);
      } else if (eligibleRightsContext(sectionContext(sections, legacyTitle, structured))) {
        if (token.matches("(?is)<TABLE\b.*")) {
          Matcher cells = CELL.matcher(token);
          while (cells.find()) {
            String claim = plain(cells.group(1));
            if (eligibleRightsClaim(claim) && containsConfirmedUnissued(claim, difference)) {
              return true;
            }
          }
        } else if (token.matches("(?is)<P\b.*")) {
          String claim = plain(token);
          if (eligibleRightsClaim(claim) && containsConfirmedUnissued(claim, difference)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isLegacySectionHeading(String text) {
    return text.matches("(?s)^[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+[.．:]?\s+.*")
        || text.matches("(?s)^제\s*\d+\s*차\s+.*");
  }

  private static String sectionContext(
      List<String> sections, String legacyTitle, boolean structured) {
    if (structured) {
      if (sections.isEmpty() || sections.stream().anyMatch(String::isBlank)) {
        return "UNKNOWN";
      }
      return String.join(" / ", sections);
    }
    return legacyTitle.isBlank() ? "UNKNOWN" : legacyTitle;
  }

  private static boolean eligibleRightsContext(String context) {
    if (context.equals("UNKNOWN")
        || context.matches(
            "(?s).*(?:무상증자|다른\s*발행\s*회차|별도\s*발행\s*건"
                + "|제\s*\d+\s*차\s*유상증자).*")) {
      return false;
    }
    return context.contains("실권주 처리내역");
  }

  private static boolean eligibleRightsClaim(String claim) {
    if (claim.matches("(?s).*(?:무상증자|다른\\s*발행\\s*회차|별도\\s*발행\\s*건).*")) {
      return false;
    }
    // 별도 회차와 구분할 수 있는 실권주 처리 근거만 채택한다.
    return claim.contains("실권주");
  }

  private static boolean containsConfirmedUnissued(String fragment, long difference) {
    String text = plain(fragment);
    // 문장 경계를 넘는 수량과 미발행 문구는 결합하지 않는다.
    for (String sentence : text.split("[.!?。]+")) {
      Pattern confirmed =
          Pattern.compile(
              "([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*주(?:는|가|를|의)?"
                  + "\\s*(?:미발행(?:\\s*처리(?:\\s*하였(?:습니)?다|\\s*됨|\\s*완료)?)?"
                  + "|발행하지\\s*않았(?:습니)?다|발행되지\\s*않았(?:습니)?다)"
                  + "\\s*(?:입니다|되었습니다|하였습니다|함|됨|완료)?\\s*$");
      Matcher matcher = confirmed.matcher(sentence.trim());
      while (matcher.find()) {
        try {
          if (Long.parseLong(matcher.group(1).replace(",", "")) == difference) {
            return true;
          }
        } catch (NumberFormatException ignored) {
          // 잘못된 수량은 자동 연결하지 않는다.
        }
      }
    }
    return false;
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

  // 원신고 모집수량과 결과보고서 발행수량은 문단의 숫자로 추정하지 않는다.
  private static Long structuredShares(String document, boolean issuanceResult) {
    List<Long> original = new ArrayList<>();
    List<Long> result = new ArrayList<>();
    Matcher tables = TABLE.matcher(document);
    while (tables.find()) {
      Matcher rows = ROW.matcher(tables.group(1));
      while (rows.find()) {
        String row = rows.group(1);
        if (EXCLUDED.matcher(plain(row)).find() || OLD_SHARES.matcher(plain(row)).find()) {
          continue;
        }
        Matcher cells = QUANTITY_FIELD.matcher(row);
        while (cells.find()) {
          Matcher code = QUANTITY_CODE.matcher(cells.group(2));
          if (code.find()) {
            collectQuantity(code.group(1), cells.group(3), original, result);
          }
        }
      }
    }
    Matcher elements = QUANTITY_ELEMENT.matcher(document);
    while (elements.find()) {
      collectQuantity(elements.group(1), elements.group(2), original, result);
    }
    // 결과보고서는 실적 합계만 사용한다. 원신고서는 모집수량만 사용한다.
    List<Long> candidates = issuanceResult ? result : original;
    List<Long> distinct = candidates.stream().distinct().toList();
    return distinct.size() == 1 && distinct.get(0) > 0 ? distinct.get(0) : null;
  }

  private static void collectQuantity(
      String code, String raw, List<Long> original, List<Long> result) {
    String value = plain(raw);
    if (!QUANTITY_VALUE.matcher(value).matches()) {
      return;
    }
    try {
      long quantity = Long.parseLong(value.replace(",", "").replace("주", "").trim());
      if ("STK_CNT".equalsIgnoreCase(code)) {
        original.add(quantity);
      } else {
        result.add(quantity);
      }
    } catch (NumberFormatException ignored) {
      // 유효하지 않은 수량은 회차 증거로 사용하지 않는다.
    }
  }

  private static boolean isNewRightsSharesLabel(String label) {
    return ISSUED.matcher(label.replace(" ", "")).find()
        && !OLD_SHARES.matcher(label).find()
        && !EXCLUDED.matcher(label).find();
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