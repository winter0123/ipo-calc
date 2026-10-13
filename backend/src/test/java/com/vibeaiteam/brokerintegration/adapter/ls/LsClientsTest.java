package com.vibeaiteam.brokerintegration.adapter.ls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vibeaiteam.brokerintegration.adapter.BrokerFetchContext;
import com.vibeaiteam.brokerintegration.adapter.kis.KisAccessToken;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import com.vibeaiteam.brokerintegration.service.matching.BrokerSaleSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

/** LS 토큰 발급·t0151 조회 테스트. 네트워크 없이 {@code MockRestServiceServer}만 쓴다. */
class LsClientsTest {

  private static final String BASE = "https://openapi.ls-sec.co.kr:8080";
  private static final String ACCNO_URL = BASE + "/stock/accno";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneId.of("UTC"));

  private MockRestServiceServer server;
  private RestClient restClient;
  private LsAuthClient authClient;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    restClient =
        RestClient.builder().baseUrl(BASE).requestFactory(restTemplate.getRequestFactory()).build();
    authClient = new LsAuthClient(restClient, CLOCK);
  }

  @Test
  void issueToken_success_sendsFormAndAddsExpiresInToNow() {
    server
        .expect(requestTo(BASE + "/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8"))
        .andExpect(
            content()
                .formData(
                    new org.springframework.util.LinkedMultiValueMap<>(
                        java.util.Map.of(
                            "grant_type", List.of("client_credentials"),
                            "appkey", List.of("dummy-key"),
                            "appsecretkey", List.of("dummy-secret"),
                            "scope", List.of("oob")))))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"dummy-token\",\"expires_in\":86400,\"token_type\":\"Bearer\"}",
                MediaType.APPLICATION_JSON));

    KisAccessToken token = authClient.issueToken("dummy-key", "dummy-secret");

    server.verify();
    assertThat(token.token()).isEqualTo("dummy-token");
    assertThat(token.expiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 16, 0, 0, 0));
  }

  @Test
  void issueToken_http400_throwsAuthFailedWithoutSecrets() {
    server
        .expect(requestTo(BASE + "/oauth2/token"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"secret\":\"leak\"}"));

    assertThatThrownBy(() -> authClient.issueToken("dummy-key", "dummy-secret"))
        .isInstanceOfSatisfying(
            BrokerApiException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo("auth_failed");
              assertThat(e.getMessage()).doesNotContain("leak", "dummy-key", "dummy-secret");
            });
  }

  @Test
  void fetchSales_parsesOnlySellRowsAndSumsTaxWithAgrTax() {
    expectDay(
        "20261002",
        body(
            "{\"medosu\":\"매도\",\"expcode\":\"005930\",\"qty\":\"10\",\"price\":\"70000\","
                + "\"amt\":\"700000\",\"fee\":\"100\",\"tax\":\"1000\",\"argtax\":\"200\"},"
                + "{\"medosu\":\"매수\",\"expcode\":\"005930\",\"qty\":\"99\",\"price\":\"1\","
                + "\"amt\":\"99\",\"fee\":\"9\",\"tax\":\"0\",\"argtax\":\"0\"},"
                + "{\"medosu\":\"1\",\"expcode\":\"A005930\",\"qty\":\"5\",\"price\":\"71000\","
                + "\"amt\":\"355000\",\"fee\":\"50\",\"tax\":\"500\",\"argtax\":\"100\"}"),
        null);

    List<BrokerSaleSummary> result =
        client(d -> false).fetchSales(ctx(), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));

    server.verify();
    assertThat(result)
        .containsExactly(
            new BrokerSaleSummary(LocalDate.of(2026, 10, 2), "005930", 15, 1_055_000, 150, 1_800));
  }

  @Test
  void fetchSales_callsOnlyTradingDays_skipsWeekendAndHolidays() {
    // 2026-10-09(금) 휴장, 10-10(토)·10-11(일) 주말 → 10-08(목)과 10-12(월)만 호출한다.
    expectDay("20261008", body(), null);
    expectDay("20261012", body(), null);
    LsDailyTradeClient client = client(d -> d.equals(LocalDate.of(2026, 10, 9)));

    client.fetchSales(ctx(), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12));

    server.verify();
  }

  @Test
  void fetchSales_followsContinuationAndSendsCtsValues() {
    expectDay(
        "20261002",
        body(
            "{\"medosu\":\"매도\",\"expcode\":\"005930\",\"qty\":\"1\",\"price\":\"10\","
                + "\"amt\":\"10\",\"fee\":\"0\",\"tax\":\"0\",\"argtax\":\"0\"}"),
        "key-1");
    server
        .expect(requestTo(ACCNO_URL))
        .andExpect(header("tr_cont", "Y"))
        .andExpect(header("tr_cont_key", "key-1"))
        .andExpect(jsonPath("$.t0151InBlock.cts_expcode").value("005930"))
        .andRespond(
            withSuccess(
                body(
                    "{\"medosu\":\"매도\",\"expcode\":\"005930\",\"qty\":\"2\",\"price\":\"10\","
                        + "\"amt\":\"20\",\"fee\":\"0\",\"tax\":\"0\",\"argtax\":\"0\"}"),
                MediaType.APPLICATION_JSON));

    List<BrokerSaleSummary> result =
        client(d -> false).fetchSales(ctx(), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));

    server.verify();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).quantity()).isEqualTo(3);
  }

  @Test
  void fetchSales_unknownSellBuyValue_failsAsInvalidResponse() {
    expectDay(
        "20261002",
        body(
            "{\"medosu\":\"??\",\"expcode\":\"005930\",\"qty\":\"1\",\"price\":\"10\","
                + "\"amt\":\"10\"}"),
        null);

    assertThatThrownBy(
            () ->
                client(d -> false)
                    .fetchSales(ctx(), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2)))
        .isInstanceOfSatisfying(
            BrokerApiException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo("invalid_response"));
  }

  @Test
  void rangeLimit_is31DaysWithGuidanceMessage() {
    LsDailyTradeClient client = client(d -> false);
    assertThat(client.maxRangeDays()).isEqualTo(30);
    assertThat(client.rangeLimitMessage()).isEqualTo("LS증권은 한 번에 31일까지 조회할 수 있습니다.");
  }

  private LsDailyTradeClient client(java.util.function.Predicate<LocalDate> holiday) {
    return new LsDailyTradeClient(restClient, holiday);
  }

  private void expectDay(String date, String body, String contKey) {
    var response = withSuccess(body, MediaType.APPLICATION_JSON);
    if (contKey != null) {
      response = response.header("tr_cont", "Y").header("tr_cont_key", contKey);
    }
    server
        .expect(requestTo(ACCNO_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("tr_cd", "t0151"))
        .andExpect(header("authorization", "Bearer dummy-token"))
        .andExpect(jsonPath("$.t0151InBlock.date").value(date))
        .andRespond(response);
  }

  private static BrokerFetchContext ctx() {
    return new BrokerFetchContext(
        1L,
        "dummy-key",
        "dummy-secret",
        "1234567801",
        new KisAccessToken("dummy-token", LocalDateTime.of(2026, 12, 31, 0, 0)),
        Set.of());
  }

  private static String body(String... rowJson) {
    return "{\"t0151OutBlock\":{\"cts_medosu\":\"\",\"cts_expcode\":\"005930\","
        + "\"cts_price\":\"\",\"cts_middiv\":\"\"},\"t0151OutBlock1\":["
        + String.join(",", rowJson)
        + "]}";
  }
}
