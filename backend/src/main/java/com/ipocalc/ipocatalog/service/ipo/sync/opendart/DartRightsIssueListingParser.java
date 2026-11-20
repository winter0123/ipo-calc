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
          "(?:신주|주권).{0,35}(?:상장|유통).{0,30}(?:예정일|개시일|예정)"
              + "|(?:상장|유통).{0,24}(?:신주|주권).{0,25}(?:예정일|개시일)");
  private static final Pattern PAID = Pattern.compile("(?:납입일|주금납입일|납입기일)");
  private static final Pattern ISSUED = Pattern.compile("(?:발행주식수|발행\\s*주식\\s*총수|신주\\s*발행\\s*수)");
  private static final Pattern EXCLUDED =
      Pattern.compile("(?:무상증자|무상신주|신주인수권증서|신주인수권\\s*증서|채권|전환사채)");

  /** 표에서 추출한 회차 증거. 원신고번호 또는 납입일·유상 신주 수로 매칭한다. */
  public record Evidence(
      LocalDate listingDate, String originalReceipt, LocalDate paymentDate, Long issuedShares) {}

  /** 원 증권신고서 또는 발행실적보고서의 납입일·신주 수 증거. */
  public record IssueIdentity(LocalDate paymentDate, Long issuedShares) {}

  /** 원신고 연결번호가 없는 경우에만 사용하는 보조 동일 회차 판별 근거. */
  public IssueIdentity issueIdentity(String xml) {
    List<List<String>> parsed = rows(xml == null ? "" : xml);
    return new IssueIdentity(firstDate(parsed, PAID), firstShares(parsed));
  }

  /** 상충 여부를 놓치지 않도록 보고서 내 모든 명시적 유상 신주 상장일을 반환한다. */
  public List<Evidence> parse(String xml) {
    if (xml == null || xml.isBlank()) {
      return List.of();
    }
    String document = xml.replace("&nbsp;", " ").replace("&amp;", "&");
    Matcher originalMatcher = ORIGINAL.matcher(plain(document));
    String original = originalMatcher.find() ? originalMatcher.group(1) : null;
    List<List<String>> rows = rows(document);
    LocalDate payment = firstDate(rows, PAID);
    Long shares = firstShares(rows);
    List<LocalDate> dates = new ArrayList<>();
    for (List<String> row : rows) {
      if (row.size() < 2) {
        continue;
      }
      String label = row.get(0).replace(" ", "");
      if (LISTING.matcher(label).find() && !EXCLUDED.matcher(label).find()) {
        for (int i = 1; i < row.size(); i++) {
          String value = row.get(i);
          if (EXCLUDED.matcher(value).find()) {
            continue;
          }
          LocalDate date = parseDate(value);
          if (date != null && !dates.contains(date)) {
            dates.add(date);
          }
        }
      }
    }
    return dates.stream().map(date -> new Evidence(date, original, payment, shares)).toList();
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

  private static LocalDate firstDate(List<List<String>> rows, Pattern label) {
    for (List<String> row : rows) {
      if (row.size() > 1 && label.matcher(row.get(0).replace(" ", "")).find()) {
        LocalDate date = parseDate(row.get(1));
        if (date != null) {
          return date;
        }
      }
    }
    return null;
  }

  private static Long firstShares(List<List<String>> rows) {
    for (List<String> row : rows) {
      if (row.size() < 2 || !ISSUED.matcher(row.get(0).replace(" ", "")).find()) {
        continue;
      }
      Matcher shares = SHARES.matcher(row.get(1));
      if (shares.find()) {
        try {
          return Long.parseLong(shares.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
    }
    return null;
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
