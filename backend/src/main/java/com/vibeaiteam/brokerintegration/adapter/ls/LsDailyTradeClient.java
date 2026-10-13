package com.vibeaiteam.brokerintegration.adapter.ls;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vibeaiteam.brokerintegration.adapter.BrokerFetchContext;
import com.vibeaiteam.brokerintegration.adapter.BrokerHttp;
import com.vibeaiteam.brokerintegration.adapter.BrokerSaleSource;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import com.vibeaiteam.brokerintegration.service.matching.BrokerSaleSummary;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LS증권 주식당일매매일지/수수료({@code t0151}) 조회 클라이언트.
 *
 * <p>TR 조회가 POST라서 이 클래스도 POST를 쓰지만 허용 경로는 {@link #ACCOUNT_PATH} 하나, TR 코드는 {@link #TR_CD} 하나뿐이다.
 * 주문 경로·TR은 존재하지 않는다. 하루 단위 TR이라 기간의 거래일(주말·휴장일 제외)마다 순차 호출하며, 한 번에 31일까지만 조회한다.
 *
 * <p>ponytail: 실호출로 확인하지 못한 것 — (1) date 파라미터가 과거 임의 일자를 받는지, (2) {@code medosu}가 "매도" 문자열인지
 * 코드값(1)인지(둘 다 처리), (3) {@code amt}/{@code fee}/{@code tax}/{@code argtax} 필드 의미, (4) 연속조회 {@code
 * cts_*} 필드명과 응답 헤더, (5) 성공 응답 코드 {@code rsp_cd}(지금은 HTTP 상태만 본다), (6) 계좌번호가 앱키에 묶이는지(지금은 계좌번호를 보내지
 * 않는다). 확인되면 이 주석을 지운다.
 */
public class LsDailyTradeClient implements BrokerSaleSource {

  /** 한 번에 조회할 수 있는 기간. 시작일~종료일 차이가 이 값을 넘으면 거절한다(31일). */
  static final int MAX_RANGE_DAYS = 30;

  private static final Logger log = LoggerFactory.getLogger(LsDailyTradeClient.class);
  private static final String ACCOUNT_PATH = "/stock/accno";
  private static final String TR_CD = "t0151";
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int MAX_PAGES_PER_DAY = 10;

  /** LS 호출 제한(초당 2회)을 넘지 않도록 호출 사이에 쉰다. */
  private static final long CALL_INTERVAL_MS = 600;

  private final RestClient restClient;
  private final Predicate<LocalDate> isHoliday;

  /**
   * 생성자.
   *
   * @param restClient base-url이 설정된 RestClient
   * @param isHoliday 휴장일 여부(주말은 이 클래스가 따로 제외한다)
   */
  public LsDailyTradeClient(RestClient restClient, Predicate<LocalDate> isHoliday) {
    this.restClient = restClient;
    this.isHoliday = isHoliday;
  }

  @Override
  public String provider() {
    return LsApiProperties.PROVIDER;
  }

  @Override
  public int maxRangeDays() {
    return MAX_RANGE_DAYS;
  }

  @Override
  public String rangeLimitMessage() {
    return "LS증권은 한 번에 31일까지 조회할 수 있습니다.";
  }

  @Override
  public List<BrokerSaleSummary> fetchSales(
      BrokerFetchContext context, LocalDate from, LocalDate to) {
    List<BrokerSaleSummary> rows = new ArrayList<>();
    boolean first = true;
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      if (isWeekend(day) || isHoliday.test(day)) {
        continue;
      }
      if (!first) {
        pause();
      }
      first = false;
      rows.addAll(fetchOneDay(context, day));
    }
    return BrokerSaleSummary.mergeByDateAndCode(rows);
  }

  private static boolean isWeekend(LocalDate day) {
    return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
  }

  private List<BrokerSaleSummary> fetchOneDay(BrokerFetchContext context, LocalDate day) {
    List<BrokerSaleSummary> rows = new ArrayList<>();
    String trCont = "N";
    String trContKey = "";
    Cts cts = new Cts("", "", "", "");
    try {
      for (int page = 1; page <= MAX_PAGES_PER_DAY; page++) {
        ResponseEntity<Response> entity = fetchPage(context, day, trCont, trContKey, cts);
        Response body = entity.getBody();
        log.info(
            "LS 브로커 조회 감사: securitiesAccountId={}, method=POST, path={}, trCd={}, status={}",
            context.securitiesAccountId(),
            ACCOUNT_PATH,
            TR_CD,
            entity.getStatusCode().value());
        if (body == null) {
          throw new BrokerApiException("null_response", "LS 매매일지 조회 응답 본문이 비어 있습니다.");
        }
        if (body.rows() != null) {
          for (Row r : body.rows()) {
            if (isSell(r.sellBuy())) {
              rows.add(toSummary(day, r));
            }
          }
        }
        if (!"Y".equalsIgnoreCase(entity.getHeaders().getFirst("tr_cont"))) {
          return rows;
        }
        trCont = "Y";
        String key = entity.getHeaders().getFirst("tr_cont_key");
        trContKey = key == null ? "" : key;
        cts = body.cts() == null ? cts : body.cts();
        pause();
      }
      // 조용히 잘라내면 매도 내역이 빠진 채 채워지므로 실패로 알린다.
      throw new BrokerApiException(
          "too_many_pages", "LS 매매일지 조회가 하루 " + MAX_PAGES_PER_DAY + "페이지를 넘어 중단했습니다.");
    } catch (BrokerApiException e) {
      throw e;
    } catch (RestClientResponseException e) {
      throw new BrokerApiException(
          "query_failed", "LS 매매일지 조회가 거절되었습니다: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      // 예외 메시지에 앱키/시크릿/토큰이 실리지 않도록 원인 예외의 타입만 남긴다.
      throw new BrokerApiException(
          "query_failed", "LS 매매일지 조회 중 오류 발생: " + e.getClass().getSimpleName(), e);
    }
  }

  private ResponseEntity<Response> fetchPage(
      BrokerFetchContext context, LocalDate day, String trCont, String trContKey, Cts cts) {
    Map<String, Object> inBlock =
        Map.of(
            "date", day.format(DATE),
            "cts_medosu", nullToEmpty(cts.medosu()),
            "cts_expcode", nullToEmpty(cts.expcode()),
            "cts_price", nullToEmpty(cts.price()),
            "cts_middiv", nullToEmpty(cts.middiv()));
    return restClient
        .post()
        .uri(ACCOUNT_PATH)
        .contentType(MediaType.parseMediaType("application/json; charset=UTF-8"))
        .headers(
            headers -> {
              headers.set("tr_cd", TR_CD);
              headers.set("tr_cont", trCont);
              headers.set("tr_cont_key", trContKey);
              headers.set("authorization", "Bearer " + context.token().token());
            })
        .body(Map.of("t0151InBlock", inBlock))
        .retrieve()
        .toEntity(Response.class);
  }

  /** 매도·매수는 문서로 확인되지 않아 "매도"/"1"은 매도, "매수"/"2"는 매수로 보고 그 밖의 값은 실패시킨다. */
  private static boolean isSell(String medosu) {
    String v = medosu == null ? "" : medosu.trim();
    if (v.contains("매도") || v.equals("1")) {
      return true;
    }
    if (v.contains("매수") || v.equals("2")) {
      return false;
    }
    throw new BrokerApiException("invalid_response", "medosu 값을 해석할 수 없다");
  }

  private static BrokerSaleSummary toSummary(LocalDate day, Row r) {
    long qty = BrokerHttp.whole(r.qty(), "qty");
    long amount =
        r.amt() == null || r.amt().isBlank()
            ? BrokerHttp.round(
                BrokerHttp.number(r.qty(), "qty").multiply(BrokerHttp.number(r.price(), "price")),
                "price")
            : BrokerHttp.whole(r.amt(), "amt");
    return new BrokerSaleSummary(
        day,
        BrokerHttp.stockCode(r.expcode()),
        qty,
        amount,
        BrokerHttp.whole(r.fee(), "fee"),
        BrokerHttp.whole(r.tax(), "tax") + BrokerHttp.whole(r.argtax(), "argtax"));
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private void pause() {
    try {
      Thread.sleep(CALL_INTERVAL_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BrokerApiException("interrupted", "LS 연속조회 대기 중 중단되었습니다.", e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Response(
      @JsonProperty("t0151OutBlock") Cts cts, @JsonProperty("t0151OutBlock1") List<Row> rows) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Cts(
      @JsonProperty("cts_medosu") String medosu,
      @JsonProperty("cts_expcode") String expcode,
      @JsonProperty("cts_price") String price,
      @JsonProperty("cts_middiv") String middiv) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Row(
      @JsonProperty("medosu") String sellBuy,
      @JsonProperty("expcode") String expcode,
      @JsonProperty("qty") String qty,
      @JsonProperty("price") String price,
      @JsonProperty("amt") String amt,
      @JsonProperty("fee") String fee,
      @JsonProperty("tax") String tax,
      @JsonProperty("argtax") String argtax) {}
}
