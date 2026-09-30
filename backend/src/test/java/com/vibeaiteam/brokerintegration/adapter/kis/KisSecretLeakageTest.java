package com.vibeaiteam.brokerintegration.adapter.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 시크릿·토큰·계좌번호가 로그와 {@code toString()}에 남지 않는지 검증한다(AC-1824, research 4.3-D·E).
 *
 * <p>더미 값만 사용한다. 실계좌·실제 앱키·실제 시크릿은 이 저장소 어디에도 쓰지 않는다.
 */
class KisSecretLeakageTest {

  private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
  private static final String DUMMY_APP_KEY = "dummy-app-key-9f3a";
  private static final String DUMMY_APP_SECRET = "dummy-app-secret-7b1c";
  private static final String DUMMY_TOKEN = "dummy-access-token-4d2e";
  private static final String DUMMY_CANO = "12345678";
  private static final String DUMMY_PRODT_CD = "01";

  private MockRestServiceServer mockServer;
  private KisAuthClient authClient;
  private KisPeriodTradeProfitClient queryClient;
  private LoggerContext loggerContext;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    RestClient restClient =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    authClient = new KisAuthClient(restClient);
    queryClient = new KisPeriodTradeProfitClient(restClient);

    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    listAppender = new ListAppender<>();
    listAppender.start();
    loggerContext.getLogger(KisAuthClient.class).addAppender(listAppender);
    loggerContext.getLogger(KisPeriodTradeProfitClient.class).addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    loggerContext.getLogger(KisAuthClient.class).detachAppender(listAppender);
    loggerContext.getLogger(KisPeriodTradeProfitClient.class).detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void tokenIssuanceAuditLog_containsNoSecretOrToken() {
    mockServer
        .expect(requestTo(BASE_URL + "/oauth2/tokenP"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"" + DUMMY_TOKEN + "\","
                    + "\"access_token_token_expired\":\"2026-12-31 23:59:59\","
                    + "\"token_type\":\"Bearer\",\"expires_in\":\"86400\"}",
                MediaType.APPLICATION_JSON));

    authClient.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET);
    mockServer.verify();

    String logs = allCapturedLogs();
    assertThat(logs).isNotEmpty();
    assertThat(logs).doesNotContain(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_TOKEN);
  }

  @Test
  void queryAuditLog_containsNoSecretTokenOrAccountNumber() {
    mockServer
        .expect(requestTo(org.hamcrest.Matchers.containsString("inquire-period-trade-profit")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"rt_cd\":\"0\",\"msg_cd\":\"00000\",\"msg1\":\"정상\","
                    + "\"output1\":[],\"output2\":{}}",
                MediaType.APPLICATION_JSON));

    KisCredentials credentials =
        new KisCredentials(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_CANO, DUMMY_PRODT_CD);
    KisAccessToken token =
        new KisAccessToken(DUMMY_TOKEN, LocalDateTime.of(2026, 12, 31, 23, 59, 59));

    KisPeriodTradeProfit result =
        queryClient.queryPeriodTradeProfit(
            42L, credentials, token, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    mockServer.verify();

    assertThat(result.details()).isEmpty();

    String logs = allCapturedLogs();
    assertThat(logs).isNotEmpty();
    // CANO는 쿼리스트링에 들어가므로 감사 로그에 URI 전체가 남으면 안 된다.
    assertThat(logs).doesNotContain(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_TOKEN, DUMMY_CANO);
    // 감사 로그에 남아야 하는 항목은 남는다.
    assertThat(logs)
        .contains("securitiesAccountId=42")
        .contains("method=GET")
        .contains("status=200");
  }

  @Test
  void credentialsToString_masksEveryValue() {
    KisCredentials credentials =
        new KisCredentials(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_CANO, DUMMY_PRODT_CD);

    String text = credentials.toString();
    assertThat(text).isEqualTo("KisCredentials[***]");
    assertThat(text).doesNotContain(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_CANO, DUMMY_PRODT_CD);
  }

  @Test
  void accessTokenToString_masksToken() {
    KisAccessToken token =
        new KisAccessToken(DUMMY_TOKEN, LocalDateTime.of(2026, 12, 31, 23, 59, 59));

    String text = token.toString();
    assertThat(text).isEqualTo("KisAccessToken[***]");
    assertThat(text).doesNotContain(DUMMY_TOKEN);
  }

  @Test
  void credentialsInLogStatementAreNotLeakedThroughMessageInterpolation() {
    // 값을 로그 메시지에 직접 넣지 않고 레코드 참조를 넘겼을 때 마스킹된 문자열이 출력되는지 확인한다.
    KisCredentials credentials =
        new KisCredentials(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_CANO, DUMMY_PRODT_CD);
    String rendered =
        "KIS 브로커 조회 감사: credentials={}".replace("{}", credentials.toString());

    assertThat(rendered).doesNotContain(DUMMY_APP_KEY, DUMMY_APP_SECRET, DUMMY_CANO);
  }

  private String allCapturedLogs() {
    return listAppender.list.stream()
        .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
        .map(
            event ->
                event.getFormattedMessage()
                    + " "
                    + (event.getThrowableProxy() == null
                        ? ""
                        : event.getThrowableProxy().getMessage()))
        .collect(Collectors.joining("\n"));
  }
}
