package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 정정 원문에 명시된 최초제출일과 DS001 원신고를 연결한다. 회사명이나 청약일만으로 회차를 추측하지 않는다. */
public final class DartFilingOrigin {
  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  private static final Pattern WHITE = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern FIRST_SUBMISSION =
      Pattern.compile(
          "(?:정정\\s*대상\\s*(?:공시\\s*서류|신고서|서류)(?:의)?\\s*최초\\s*제출\\s*일)"
              + "[^0-9]{0,100}(\\d{4})\\s*(?:년|[./-])\\s*(\\d{1,2})"
              + "\\s*(?:월|[./-])\\s*(\\d{1,2})\\s*일?");
  private static final Pattern COMPACT_SUBMISSION =
      Pattern.compile(
          "(?:정정\\s*대상\\s*(?:공시\\s*서류|신고서|서류)(?:의)?\\s*최초\\s*제출\\s*일)"
              + "[^0-9]{0,100}(\\d{4})(\\d{2})(\\d{2})");

  private DartFilingOrigin() {}

  /** 명시적 원신고 또는 정정 원문에 있는 최초제출일을 근거로 원공시 접수번호를 찾는다. */
  public static String resolveOriginalReceipt(
      OpenDartDisclosureItem filing, List<OpenDartDisclosureItem> disclosures, String xml) {
    if (filing == null || filing.getRceptNo() == null) {
      return null;
    }
    if (!filing.isCorrectionOrFinalized()) {
      return filing.isEquitySecuritiesReport() ? filing.getRceptNo() : null;
    }
    LocalDate originalDate = originalSubmissionDate(xml);
    if (originalDate == null || disclosures == null) {
      return null;
    }
    String day = originalDate.format(DateTimeFormatter.BASIC_ISO_DATE);
    List<OpenDartDisclosureItem> originals =
        disclosures.stream()
            .filter(OpenDartDisclosureItem::isEquitySecuritiesReport)
            .filter(item -> !item.isCorrectionOrFinalized())
            .filter(item -> day.equals(item.getRceptDt()))
            .filter(
                item ->
                    filing.getCorpCode() != null && filing.getCorpCode().equals(item.getCorpCode()))
            .toList();
    // 원공시가 같은 날 복수 건이면 회차가 불명확하다.
    return originals.size() == 1 ? originals.get(0).getRceptNo() : null;
  }

  /** 정정대상 최초제출일을 찾는다. 해당 헤더가 없거나 날짜가 모호하면 null이다. */
  public static LocalDate originalSubmissionDate(String xml) {
    if (xml == null || xml.isBlank()) {
      return null;
    }
    String plain = WHITE.matcher(TAG.matcher(xml).replaceAll(" ")).replaceAll(" ");
    Matcher match = FIRST_SUBMISSION.matcher(plain);
    if (!match.find()) {
      match = COMPACT_SUBMISSION.matcher(plain);
      if (!match.find()) {
        return null;
      }
    }
    try {
      return LocalDate.of(
          Integer.parseInt(match.group(1)),
          Integer.parseInt(match.group(2)),
          Integer.parseInt(match.group(3)));
    } catch (DateTimeException | NumberFormatException e) {
      return null;
    }
  }
}
