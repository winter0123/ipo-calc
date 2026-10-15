package com.ipocalc.brokerintegration.adapter.kiwoom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipocalc.brokerintegration.adapter.BrokerFetchContext;
import com.ipocalc.brokerintegration.adapter.BrokerHttp;
import com.ipocalc.brokerintegration.adapter.BrokerSaleSource;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import com.ipocalc.brokerintegration.service.matching.BrokerSaleSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 키움증권 일자별종목별실현손익(기간, {@code ka10073}) 조회 클라이언트.
 *
 * <p>TR 조회가 POST라서 이 클래스도 POST를 쓰지만 허용 경로는 {@link #ACCOUNT_PATH} 하나, api-id는 {@link #API_ID} 하나뿐이다.
 * 주문 경로·api-id는 존재하지 않는다(ArchUnit이 소스 문자열까지 검사한다). 종목코드가 필수라서 우리 배정의 종목마다 한 번씩 순차 호출한다.
 *
 * <p>ponytail: 실호출로 확인하지 못한 것 — 응답 행 필드 의미(cntr_pric이 평균 체결가인지), 수수료·세금 부호, 연속조회 응답 헤더 대소문자. 확인되면 이
 * 주석을 지운다.
 */
public class KiwoomTradeProfitClient implements BrokerSaleSource {

  private static final Logger log = LoggerFactory.getLogger(KiwoomTradeProfitClient.class);
  private static final String ACCOUNT_PATH = "/api/dostk/acnt";
  private static final String API_ID = "ka10073";
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int MAX_PAGES = 10;

  /** 키움 호출 제한(초당 5회)을 넘지 않도록 호출 사이에 쉰다. */
  private static final long CALL_INTERVAL_MS = 250;

  private final RestClient restClient;

  /**
   * 생성자.
   *
   * @param restClient base-url이 설정된 RestClient
   */
  public KiwoomTradeProfitClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public String provider() {
    return KiwoomApiProperties.PROVIDER;
  }

  @Override
  public List<BrokerSaleSummary> fetchSales(
      BrokerFetchContext context, LocalDate from, LocalDate to) {
    List<BrokerSaleSummary> rows = new ArrayList<>();
    boolean first = true;
    for (String code : context.stockCodes()) {
      if (!first) {
        pause();
      }
      first = false;
      rows.addAll(fetchOneStock(context, BrokerHttp.stockCode(code), from, to));
    }
    return BrokerSaleSummary.mergeByDateAndCode(rows);
  }

  private List<BrokerSaleSummary> fetchOneStock(
      BrokerFetchContext context, String code, LocalDate from, LocalDate to) {
    List<BrokerSaleSummary> rows = new ArrayList<>();
    String contYn = null;
    String nextKey = null;
    try {
      for (int page = 1; page <= MAX_PAGES; page++) {
        ResponseEntity<Response> entity = fetchPage(context, code, from, to, contYn, nextKey);
        Response body = entity.getBody();
        log.info(
            "키움 브로커 조회 감사: securitiesAccountId={}, method=POST, path={}, apiId={}, status={}",
            context.securitiesAccountId(),
            ACCOUNT_PATH,
            API_ID,
            entity.getStatusCode().value());
        if (body == null) {
          throw new BrokerApiException(
              "null_response", "키움 실현손익 조회 응답 본문이 비어 있습니다. " + KiwoomAuthClient.IP_HINT);
        }
        if (!"0".equals(body.returnCode())) {
          throw new BrokerApiException(
              "kiwoom_api_error",
              "키움 실현손익 조회 실패: return_code="
                  + body.returnCode()
                  + ", return_msg="
                  + body.returnMsg()
                  + ". "
                  + KiwoomAuthClient.IP_HINT);
        }
        if (body.rows() != null) {
          for (Row r : body.rows()) {
            toSummary(r).ifPresent(rows::add);
          }
        }
        if (!"Y".equalsIgnoreCase(entity.getHeaders().getFirst("cont-yn"))) {
          return rows;
        }
        contYn = "Y";
        nextKey = entity.getHeaders().getFirst("next-key");
        pause();
      }
      // 조용히 잘라내면 매도 내역이 빠진 채 채워지므로 실패로 알린다.
      throw new BrokerApiException(
          "too_many_pages", "키움 실현손익 조회가 " + MAX_PAGES + "페이지를 넘어 중단했습니다.");
    } catch (BrokerApiException e) {
      throw e;
    } catch (RestClientResponseException e) {
      throw new BrokerApiException(
          "query_failed",
          "키움 실현손익 조회가 거절되었습니다: HTTP "
              + e.getStatusCode().value()
              + ". "
              + KiwoomAuthClient.IP_HINT,
          e);
    } catch (RuntimeException e) {
      // 예외 메시지에 앱키/시크릿/토큰이 실리지 않도록 원인 예외의 타입만 남긴다.
      throw new BrokerApiException(
          "query_failed",
          "키움 실현손익 조회 중 오류 발생: " + e.getClass().getSimpleName() + ". " + KiwoomAuthClient.IP_HINT,
          e);
    }
  }

  private ResponseEntity<Response> fetchPage(
      BrokerFetchContext context,
      String code,
      LocalDate from,
      LocalDate to,
      String contYn,
      String nextKey) {
    return restClient
        .post()
        .uri(ACCOUNT_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(
            headers -> {
              headers.set("api-id", API_ID);
              headers.set("authorization", "Bearer " + context.token().token());
              if (contYn != null) {
                headers.set("cont-yn", contYn);
                headers.set("next-key", nextKey == null ? "" : nextKey);
              }
            })
        .body(new Request(from.format(DATE), to.format(DATE), code))
        .retrieve()
        .toEntity(Response.class);
  }

  /** 체결수량이 0인 행은 건너뛰고, 금액은 체결수량×체결가다. */
  private static Optional<BrokerSaleSummary> toSummary(Row r) {
    BigDecimal qty = BrokerHttp.number(r.qty(), "cntr_qty");
    if (qty.signum() == 0) {
      return Optional.empty();
    }
    LocalDate date;
    try {
      date = LocalDate.parse(r.date() == null ? "" : r.date().trim(), DATE);
    } catch (DateTimeParseException e) {
      throw new BrokerApiException("invalid_response", "dt 값을 해석할 수 없다");
    }
    BigDecimal amount = qty.multiply(BrokerHttp.number(r.price(), "cntr_pric"));
    return Optional.of(
        new BrokerSaleSummary(
            date,
            BrokerHttp.stockCode(r.code()),
            BrokerHttp.round(qty, "cntr_qty"),
            BrokerHttp.round(amount, "cntr_pric"),
            BrokerHttp.whole(r.fee(), "tdy_trde_cmsn"),
            BrokerHttp.whole(r.tax(), "tdy_trde_tax")));
  }

  private void pause() {
    try {
      Thread.sleep(CALL_INTERVAL_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BrokerApiException("interrupted", "키움 연속조회 대기 중 중단되었습니다.", e);
    }
  }

  private record Request(
      @JsonProperty("strt_dt") String startDate,
      @JsonProperty("end_dt") String endDate,
      @JsonProperty("stk_cd") String stockCode) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Response(
      @JsonProperty("dt_stk_rlzt_pl") List<Row> rows,
      @JsonProperty("return_code") String returnCode,
      @JsonProperty("return_msg") String returnMsg) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Row(
      @JsonProperty("dt") String date,
      @JsonProperty("stk_cd") String code,
      @JsonProperty("cntr_qty") String qty,
      @JsonProperty("cntr_pric") String price,
      @JsonProperty("tdy_trde_cmsn") String fee,
      @JsonProperty("tdy_trde_tax") String tax) {}
}
