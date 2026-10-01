package com.vibeaiteam.brokerintegration.adapter.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitDetail;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfitSummary;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** {@link KisPeriodTradeProfitClient} 테스트. 네트워크 호출 없이 {@code MockRestServiceServer}로만 검증한다. */
class KisPeriodTradeProfitClientTest {

  private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
  private static final String PROFIT_URL =
      BASE_URL + "/uapi/domestic-stock/v1/trading/inquire-period-trade-profit";
  private static final String DUMMY_APP_KEY = "test-app-key";
  private static final String DUMMY_APP_SECRET = "test-app-secret";
  private static final String DUMMY_TOKEN = "test-access-token";
  private static final String DUMMY_CANO = "12345678";
  private static final String DUMMY_PRODT_CD = "01";

  private MockRestServiceServer mockServer;
  private KisPeriodTradeProfitClient client;
  private KisCredentials credentials;
  private KisAccessToken token;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    RestClient restClient =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    client = new KisPeriodTradeProfitClient(restClient);
    credentials = new KisCredentials(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_CANO, DUMMY_PRODT_CD);
    token = new KisAccessToken(DUMMY_TOKEN, LocalDateTime.of(2026, 12, 31, 23, 59, 59));
  }

  @Test
  void queryPeriodTradeProfit_success_parsesOutput1AndOutput2() {
    expectProfitRequest("20260101", "20260131", successBody());

    KisPeriodTradeProfit result = query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    mockServer.verify();
    assertThat(result.details()).hasSize(1);
    KisPeriodTradeProfitDetail detail = result.details().get(0);
    assertThat(detail.tradDt()).isEqualTo("20260115");
    assertThat(detail.pdno()).isEqualTo("005930");
    assertThat(detail.prdtName()).isEqualTo("삼성전자");
    assertThat(detail.fee()).isEqualTo("500");
    assertThat(detail.tlTax()).isEqualTo("1000");
    assertThat(result.summary()).isNotNull();
    assertThat(result.summary().totRlztPfls()).isEqualTo("50000");
    assertThat(result.summary().prsmTlexSmtl()).isEqualTo("1500");
  }

  @Test
  void queryPeriodTradeProfit_usesGetAndRequiredHeadersAndQueryParams() {
    expectProfitRequest("20260101", "20260131", successBody());

    query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    // HTTP 메서드가 GET이고 필수 헤더·쿼리 파라미터가 정확한지 expect로 강제한다.
    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_formatsDatesAsYyyyMMdd() {
    expectProfitRequest("20260115", "20260120", successBody());

    query(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20));

    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_ignoresUnknownFields() {
    expectProfitRequest(
        "20260101",
        "20260131",
        """
        {
          "rt_cd": "0",
          "msg_cd": "00000",
          "msg1": "정상",
          "output1": [
            {
              "trad_dt": "20260115",
              "pdno": "005930",
              "unknown_field": "must be ignored"
            }
          ],
          "output2": { "unknown_summary_field": "must be ignored" }
        }
        """);

    KisPeriodTradeProfit result = query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    mockServer.verify();
    assertThat(result.details()).hasSize(1);
    assertThat(result.details().get(0).pdno()).isEqualTo("005930");
    assertThat(result.summary()).isNotNull();
  }

  @Test
  void queryPeriodTradeProfit_emptyOutput1_returnsEmptyDetails() {
    expectProfitRequest(
        "20260101", "20260131", "{\"rt_cd\":\"0\",\"msg_cd\":\"00000\",\"msg1\":\"정상\"}");

    KisPeriodTradeProfit result = query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    mockServer.verify();
    assertThat(result.details()).isEmpty();
    assertThat(result.summary()).isNull();
  }

  @Test
  void queryPeriodTradeProfit_rtCdNotZero_throwsBrokerApiExceptionWithBrokerMessage() {
    expectProfitRequest(
        "20260101",
        "20260131",
        "{\"rt_cd\":\"1\",\"msg_cd\":\"EGW00123\",\"msg1\":\"조회 조건 오류\"}");

    assertThatThrownBy(
            () -> query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("조회 조건 오류")
        .hasMessageContaining("EGW00123");
    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_http500_throwsBrokerApiException() {
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());

    assertThatThrownBy(
            () -> query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("KIS 기간별 매매손익 조회");
    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_timeoutLikeFailure_throwsBrokerApiException() {
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

    assertThatThrownBy(
            () -> query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOf(BrokerApiException.class);
    mockServer.verify();
  }

  private KisPeriodTradeProfit query(LocalDate from, LocalDate to) {
    return client.queryPeriodTradeProfit(1L, credentials, token, from, to);
  }

  @Test
  void queryPeriodTradeProfit_continuesWithHeaderAndContextKeysAndMergesPages() {
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andExpect(headerDoesNotExist("tr_cont"))
        .andExpect(queryParam("CTX_AREA_FK100", ""))
        .andExpect(queryParam("CTX_AREA_NK100", ""))
        .andRespond(
            withSuccess(pageBody("20260115", "FK-1", "NK-1"), MediaType.APPLICATION_JSON)
                .header("tr_cont", "M"));
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andExpect(header("tr_cont", "N"))
        .andExpect(queryParam("CTX_AREA_FK100", "FK-1"))
        .andExpect(queryParam("CTX_AREA_NK100", "NK-1"))
        .andRespond(
            withSuccess(pageBody("20260116", "", ""), MediaType.APPLICATION_JSON)
                .header("tr_cont", "D"));

    KisPeriodTradeProfit result =
        client.queryPeriodTradeProfit(
            1L, credentials, token, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertThat(result.details()).extracting("tradDt").containsExactly("20260115", "20260116");
    assertThat(result.summary()).isNotNull();
    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_stopsWhenContinuationKeyIsBlankEvenIfHeaderSaysMore() {
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andRespond(
            withSuccess(pageBody("20260115", "", ""), MediaType.APPLICATION_JSON)
                .header("tr_cont", "M"));

    KisPeriodTradeProfit result =
        client.queryPeriodTradeProfit(
            1L, credentials, token, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertThat(result.details()).hasSize(1);
    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_failsInsteadOfSilentlyTruncatingAfterMaxPages() {
    mockServer
        .expect(times(10), requestTo(containsString("inquire-period-trade-profit")))
        .andRespond(
            withSuccess(pageBody("20260115", "FK", "NK"), MediaType.APPLICATION_JSON)
                .header("tr_cont", "M"));

    assertThatThrownBy(
            () ->
                client.queryPeriodTradeProfit(
                    1L, credentials, token, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOfSatisfying(
            BrokerApiException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo("too_many_pages"));
    mockServer.verify();
  }

  @Test
  void queryPeriodTradeProfit_secondPageFailureIsReportedNotSwallowed() {
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andRespond(
            withSuccess(pageBody("20260115", "FK", "NK"), MediaType.APPLICATION_JSON)
                .header("tr_cont", "F"));
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andRespond(withServerError());

    assertThatThrownBy(
            () ->
                client.queryPeriodTradeProfit(
                    1L, credentials, token, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        .isInstanceOf(BrokerApiException.class);
  }

  private String pageBody(String tradeDate, String fk100, String nk100) {
    return """
        {
          "rt_cd": "0",
          "msg_cd": "KIOK0510",
          "msg1": "조회가 완료되었습니다",
          "ctx_area_fk100": "%s",
          "ctx_area_nk100": "%s",
          "output1": [ {
            "trad_dt": "%s",
            "pdno": "0123A4",
            "sll_qty": "1",
            "sll_amt": "1000",
            "fee": "0",
            "tl_tax": "0"
          } ],
          "output2": { "sll_qty_smtl": "1" }
        }
        """
        .formatted(fk100, nk100, tradeDate);
  }

  private void expectProfitRequest(String startDate, String endDate, String responseBody) {
    mockServer
        .expect(requestTo(containsString("inquire-period-trade-profit")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("authorization", "Bearer " + DUMMY_TOKEN))
        .andExpect(header("appkey", DUMMY_APP_KEY))
        .andExpect(header("appsecret", DUMMY_APP_SECRET))
        .andExpect(header("tr_id", "TTTC8715R"))
        .andExpect(header("custtype", "P"))
        .andExpect(queryParam("CANO", DUMMY_CANO))
        .andExpect(queryParam("ACNT_PRDT_CD", DUMMY_PRODT_CD))
        .andExpect(queryParam("SORT_DVSN", "00"))
        .andExpect(queryParam("INQR_STRT_DT", startDate))
        .andExpect(queryParam("INQR_END_DT", endDate))
        .andExpect(queryParam("PDNO", ""))
        .andExpect(queryParam("CBLC_DVSN", "00"))
        .andExpect(queryParam("CTX_AREA_FK100", ""))
        .andExpect(queryParam("CTX_AREA_NK100", ""))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  private String successBody() {
    return """
        {
          "rt_cd": "0",
          "msg_cd": "00000",
          "msg1": "정상",
          "output1": [ {
            "trad_dt": "20260115",
            "pdno": "005930",
            "prdt_name": "삼성전자",
            "trad_dvsn_name": "매도",
            "buy_qty": "10",
            "buy_amt": "700000",
            "sll_pric": "75000",
            "sll_qty": "10",
            "sll_amt": "750000",
            "rlzt_pfls": "50000",
            "pfls_rt": "7.14",
            "fee": "500",
            "tl_tax": "1000"
          } ],
          "output2": {
            "sll_qty_smtl": "10",
            "sll_tr_amt_smtl": "750000",
            "sll_fee_smtl": "500",
            "sll_tltx_smtl": "1000",
            "tot_rlzt_pfls": "50000",
            "prsm_tlex_smtl": "1500"
          }
        }
        """;
  }
}
