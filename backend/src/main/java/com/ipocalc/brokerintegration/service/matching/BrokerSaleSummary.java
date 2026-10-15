package com.ipocalc.brokerintegration.service.matching;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 브로커가 알려준 일자×종목 매도 집계. */
public record BrokerSaleSummary(
    LocalDate tradeDate, String stockCode, long quantity, long amount, long fee, long tax) {

  /**
   * 같은 (일자, 종목) 행을 합산해 일자, 종목코드 오름차순으로 돌려준다.
   *
   * @param rows 합산할 행
   * @return 일자·종목별 합산 목록
   */
  public static List<BrokerSaleSummary> mergeByDateAndCode(List<BrokerSaleSummary> rows) {
    Map<String, BrokerSaleSummary> merged = new TreeMap<>();
    for (BrokerSaleSummary r : rows) {
      merged.merge(
          r.tradeDate().format(DateTimeFormatter.BASIC_ISO_DATE) + r.stockCode(),
          r,
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
}
