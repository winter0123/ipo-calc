package com.ipocalc.brokerintegration.service.matching;

import com.ipocalc.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import com.ipocalc.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitDetail;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * KIS 기간별매매손익 응답을 매칭 입력({@link BrokerSaleSummary})으로 바꾼다(D-B-007).
 *
 * <p>매도가 있는 행만 대상이며, 같은 (일자, pdno) 행은 합산한다. 해석할 수 없는 값은 건너뛰지 않고 {@code invalid_response}로 실패한다.
 */
public final class KisTradeProfitMapper {

  private static final String INVALID = "invalid_response";
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private KisTradeProfitMapper() {}

  /**
   * 매도 행을 일자·종목별로 합산해 일자, 종목코드 오름차순으로 돌려준다.
   *
   * @param profit 어댑터가 돌려준 기간별매매손익
   * @return 매도 집계 목록
   * @throws BrokerApiException 매도 행의 일자·종목코드·수량·금액을 해석할 수 없을 때
   */
  public static List<BrokerSaleSummary> toSaleSummaries(KisPeriodTradeProfit profit) {
    if (profit == null || profit.details() == null) {
      return List.of();
    }
    Map<String, BrokerSaleSummary> merged = new TreeMap<>(Comparator.naturalOrder());
    for (KisPeriodTradeProfitDetail d : profit.details()) {
      if (d == null || isZero(d.sllQty())) {
        continue;
      }
      LocalDate date = parseDate(d.tradDt());
      String code = d.pdno() == null ? "" : d.pdno().trim();
      if (code.isEmpty()) {
        throw new BrokerApiException(INVALID, "pdno 값이 비어 있다");
      }
      BrokerSaleSummary row =
          new BrokerSaleSummary(
              date,
              code,
              parseAmount(d.sllQty(), "sll_qty", false),
              parseAmount(d.sllAmt(), "sll_amt", false),
              parseAmount(d.fee(), "fee", true),
              parseAmount(d.tlTax(), "tl_tax", true));
      // 날짜(8자리)+코드 키라 문자열 정렬이 일자 → 코드 순이다.
      merged.merge(
          date.format(DATE) + code,
          row,
          (a, b) ->
              new BrokerSaleSummary(
                  a.tradeDate(),
                  a.stockCode(),
                  a.quantity() + b.quantity(),
                  a.amount() + b.amount(),
                  a.fee() + b.fee(),
                  a.tax() + b.tax()));
    }
    return List.copyOf(merged.values());
  }

  private static boolean isZero(String s) {
    if (s == null || s.isBlank()) {
      return true;
    }
    return s.trim().matches("0+");
  }

  private static LocalDate parseDate(String s) {
    try {
      return LocalDate.parse(s == null ? "" : s.trim(), DATE);
    } catch (DateTimeParseException e) {
      throw new BrokerApiException(INVALID, "trad_dt 값을 해석할 수 없다");
    }
  }

  private static long parseAmount(String s, String field, boolean blankIsZero) {
    String v = s == null ? "" : s.trim();
    if (v.isEmpty() && blankIsZero) {
      return 0;
    }
    if (!v.matches("\\d{1,18}")) {
      throw new BrokerApiException(INVALID, field + " 값을 해석할 수 없다");
    }
    return Long.parseLong(v);
  }
}
