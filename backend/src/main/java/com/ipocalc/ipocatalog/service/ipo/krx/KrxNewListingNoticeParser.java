package com.ipocalc.ipocatalog.service.ipo.krx;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 공식 KIND 70760 신규상장 공고의 보통주 종목코드와 매매개시일만 파싱한다. */
public class KrxNewListingNoticeParser {
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern SPACE = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern STOCK_SECTION =
      Pattern.compile("2\\.\\s*주식의\\s*종류와\\s*수(.*?)3\\.\\s*액면가", Pattern.DOTALL);
  private static final Pattern COMMON_STOCK =
      Pattern.compile("보통주\\s+KR[0-9A-Z]{10}\\s+A([0-9][0-9A-Z]{5})\\s+[0-9,]+");
  private static final Pattern LISTING_DATE =
      Pattern.compile(
          "10\\.\\s*상장일\\s*\\(매매개시일\\)\\s*(20\\d{2})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");

  /** 신규상장 공고에서 발행회차 추론 없이 공통주권 종목코드·상장일을 추출한다. */
  public Evidence parse(String html) {
    if (html == null || html.isBlank()) {
      throw new IllegalArgumentException("KIND 신규상장 공고가 비어 있습니다");
    }
    String text = SPACE.matcher(TAG.matcher(html).replaceAll(" ")).replaceAll(" ").trim();
    if (!text.startsWith("신규상장")) {
      throw new IllegalArgumentException("KIND 신규상장 원문이 아닙니다");
    }
    Matcher section = STOCK_SECTION.matcher(text);
    if (!section.find()) {
      throw new IllegalArgumentException("KIND 신규상장 보통주 목록이 없습니다");
    }
    Matcher stock = COMMON_STOCK.matcher(section.group(1));
    if (!stock.find()) {
      throw new IllegalArgumentException("KIND 신규상장 보통주 종목코드가 없습니다");
    }
    String code = stock.group(1);
    if (stock.find()) {
      throw new IllegalArgumentException("KIND 신규상장 보통주 후보가 둘 이상입니다");
    }
    Matcher listing = LISTING_DATE.matcher(text);
    if (!listing.find()) {
      throw new IllegalArgumentException("KIND 신규상장 매매개시일이 없습니다");
    }
    LocalDate date =
        LocalDate.of(
            Integer.parseInt(listing.group(1)),
            Integer.parseInt(listing.group(2)),
            Integer.parseInt(listing.group(3)));
    if (listing.find()) {
      throw new IllegalArgumentException("KIND 신규상장 매매개시일이 중복됩니다");
    }
    return new Evidence(code, date);
  }

  /** 공식 신규상장 공고의 종목코드와 매매개시일. */
  public record Evidence(String stockCode, LocalDate listingDate) {}
}
