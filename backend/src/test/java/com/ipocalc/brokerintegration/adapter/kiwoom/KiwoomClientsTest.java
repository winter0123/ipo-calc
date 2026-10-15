package com.ipocalc.brokerintegration.adapter.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipocalc.brokerintegration.adapter.BrokerFetchContext;
import com.ipocalc.brokerintegration.adapter.kis.KisAccessToken;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import com.ipocalc.brokerintegration.service.matching.BrokerSaleSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** 키움 토큰 발급·ka10073 조회 테스트. 네트워크 없이 {@code MockRestServiceServer}만 쓴다. */
class KiwoomClientsTest {

  private static final String BASE = "https://api.kiwoom.com";
  private static final String ACNT_URL = BASE + "/api/dostk/acnt";

  private MockRestServiceServer server;
  private KiwoomAuthClient authClient;
  private KiwoomTradeProfitClient tradeClient;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    RestClient restClient =
        RestClient.builder().baseUrl(BASE).requestFactory(restTemplate.getRequestFactory()).build();
    authClient = new KiwoomAuthClient(restClient);
    tradeClient = new KiwoomTradeProfitClient(restClient);
  }

  @Test
  void issueToken_success_sendsClientCredentialsAndParsesExpiry() {
    server
        .expect(requestTo(BASE + "/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.grant_type").value("client_credentials"))
        .andExpect(jsonPath("$.appkey").value("dummy-key"))
        .andExpect(jsonPath("$.secretkey").value("dummy-secret"))
        .andRespond(
            withSuccess(
                "{\"token\":\"dummy-token\",\"expires_dt\":\"20261015083000\","
                    + "\"return_code\":0,\"return_msg\":\"정상\"}",
                MediaType.APPLICATION_JSON));

    KisAccessToken token = authClient.issueToken("dummy-key", "dummy-secret");

    server.verify();
    assertThat(token.token()).isEqualTo("dummy-token");
    assertThat(token.expiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 8, 30, 0));
  }

  @Test
  void issueToken_returnCodeNotZero_throwsWithIpHintAndNoSecrets() {
    server
        .expect(requestTo(BASE + "/oauth2/token"))
        .andRespond(
            withSuccess(
                "{\"return_code\":3,\"return_msg\":\"인증 실패\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> authClient.issueToken("dummy-key", "dummy-secret"))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("서버 IP 등록")
        .hasMessageNotContaining("dummy-key")
        .hasMessageNotContaining("dummy-secret");
  }

  @Test
  void issueToken_http401_throwsAuthFailedWithIpHint() {
    server
        .expect(requestTo(BASE + "/oauth2/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"secret\":\"leak\"}"));

    assertThatThrownBy(() -> authClient.issueToken("dummy-key", "dummy-secret"))
        .isInstanceOfSatisfying(
            BrokerApiException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo("auth_failed");
              assertThat(e.getMessage()).contains("서버 IP 등록").doesNotContain("leak");
            });
  }

  @Test
  void fetchSales_callsOncePerStockCode_parsesSignsAndPrefixAndMergesSameDay() {
    expectCall(
        "005930",
        rows(
            "{\"dt\":\"20261002\",\"stk_cd\":\"A005930\",\"cntr_qty\":\"+0000010\","
                + "\"cntr_pric\":\"-70000\",\"tdy_trde_cmsn\":\"+000500\","
                + "\"tdy_trde_tax\":\"00001000\"},"
                + "{\"dt\":\"20261002\",\"stk_cd\":\"005930\",\"cntr_qty\":\"5\","
                + "\"cntr_pric\":\"71000\",\"tdy_trde_cmsn\":\"250\",\"tdy_trde_tax\":\"500\"},"
                + "{\"dt\":\"20261003\",\"stk_cd\":\"005930\",\"cntr_qty\":\"0\","
                + "\"cntr_pric\":\"0\",\"tdy_trde_cmsn\":\"0\",\"tdy_trde_tax\":\"0\"}"),
        null);
    expectCall(
        "0123A4",
        rows(
            "{\"dt\":\"20261001\",\"stk_cd\":\"0123A4\",\"cntr_qty\":\"3\","
                + "\"cntr_pric\":\"10000\",\"tdy_trde_cmsn\":\"10\",\"tdy_trde_tax\":\"20\"}"),
        null);

    List<BrokerSaleSummary> result = fetch("005930", "0123A4");

    server.verify();
    assertThat(result)
        .containsExactly(
            new BrokerSaleSummary(LocalDate.of(2026, 10, 1), "0123A4", 3, 30_000, 10, 20),
            new BrokerSaleSummary(
                LocalDate.of(2026, 10, 2), "005930", 15, 700_000 + 355_000, 750, 1_500));
  }

  @Test
  void fetchSales_followsContinuationHeaders() {
    expectCall("005930", rows(row("20261001", "1")), "next-1");
    server
        .expect(requestTo(ACNT_URL))
        .andExpect(header("cont-yn", "Y"))
        .andExpect(header("next-key", "next-1"))
        .andRespond(withSuccess(rows(row("20261002", "2")), MediaType.APPLICATION_JSON));

    List<BrokerSaleSummary> result = fetch("005930");

    server.verify();
    assertThat(result).extracting(BrokerSaleSummary::quantity).containsExactly(1L, 2L);
  }

  @Test
  void fetchSales_returnCodeNotZero_throwsBrokerApiException() {
    server
        .expect(requestTo(ACNT_URL))
        .andRespond(
            withSuccess(
                "{\"return_code\":1,\"return_msg\":\"조회 실패\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fetch("005930"))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("return_code=1")
        .hasMessageContaining("서버 IP 등록");
  }

  @Test
  void fetchSales_unparsableNumber_failsAsInvalidResponse() {
    server
        .expect(requestTo(ACNT_URL))
        .andRespond(
            withSuccess(
                rows(
                    "{\"dt\":\"20261001\",\"stk_cd\":\"005930\",\"cntr_qty\":\"abc\","
                        + "\"cntr_pric\":\"1\"}"),
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fetch("005930"))
        .isInstanceOfSatisfying(
            BrokerApiException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo("invalid_response"));
  }

  @Test
  void fetchSales_sendsReadOnlyApiIdAndBearerToken() {
    server
        .expect(requestTo(ACNT_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-id", "ka10073"))
        .andExpect(header("authorization", "Bearer dummy-token"))
        .andExpect(headerDoesNotExist("cont-yn"))
        .andExpect(
            content()
                .json("{\"strt_dt\":\"20261001\",\"end_dt\":\"20261031\",\"stk_cd\":\"005930\"}"))
        .andRespond(withSuccess(rows(), MediaType.APPLICATION_JSON));

    assertThat(fetch("005930")).isEmpty();
    server.verify();
  }

  @Test
  void fetchSales_noStockCodes_makesNoCalls() {
    assertThat(fetch()).isEmpty();
    server.verify();
  }

  @Test
  void fetchSales_httpError_doesNotLeakToken() {
    server.expect(requestTo(ACNT_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> fetch("005930"))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("HTTP 403")
        .hasMessageNotContaining("dummy-token");
  }

  private List<BrokerSaleSummary> fetch(String... codes) {
    Set<String> stockCodes = new LinkedHashSet<>(List.of(codes));
    return tradeClient.fetchSales(
        new BrokerFetchContext(
            1L,
            "dummy-key",
            "dummy-secret",
            "1234567801",
            new KisAccessToken("dummy-token", LocalDateTime.of(2026, 12, 31, 0, 0)),
            stockCodes),
        LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 10, 31));
  }

  private void expectCall(String code, String body, String nextKey) {
    var response = withSuccess(body, MediaType.APPLICATION_JSON);
    if (nextKey != null) {
      response = response.header("cont-yn", "Y").header("next-key", nextKey);
    }
    server
        .expect(requestTo(ACNT_URL))
        .andExpect(jsonPath("$.stk_cd").value(code))
        .andRespond(response);
  }

  private static String row(String date, String qty) {
    return "{\"dt\":\""
        + date
        + "\",\"stk_cd\":\"005930\",\"cntr_qty\":\""
        + qty
        + "\",\"cntr_pric\":\"100\",\"tdy_trde_cmsn\":\"1\",\"tdy_trde_tax\":\"2\"}";
  }

  private static String rows(String... rowJson) {
    return "{\"dt_stk_rlzt_pl\":["
        + String.join(",", rowJson)
        + "],\"return_code\":0,\"return_msg\":\"정상\"}";
  }
}
