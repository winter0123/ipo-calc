package com.vibeaiteam.brokerintegration.adapter.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * {@link KisAuthClient} 테스트. 네트워크 호출 없이 {@code MockRestServiceServer}로만 검증한다.
 *
 * <p>AC-1822(토큰 발급 실패 격리), AC-1823(네트워크 없이 통과)를 담당한다.
 */
class KisAuthClientTest {

  private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
  private static final String TOKEN_URL = BASE_URL + "/oauth2/tokenP";
  private static final String DUMMY_APP_KEY = "test-app-key";
  private static final String DUMMY_APP_SECRET = "test-app-secret";
  private static final String SUCCESS_BODY =
      "{\"access_token\":\"test-access-token\","
          + "\"access_token_token_expired\":\"2026-12-31 23:59:59\","
          + "\"token_type\":\"Bearer\",\"expires_in\":\"86400\"}";

  private MockRestServiceServer mockServer;
  private KisAuthClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    RestClient restClient =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    client = new KisAuthClient(restClient);
  }

  @Test
  void issueToken_success_parsesTokenAndExpiry() {
    expectTokenRequest(SUCCESS_BODY);

    KisAccessToken token = client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET);

    mockServer.verify();
    assertThat(token.token()).isEqualTo("test-access-token");
    assertThat(token.expiresAt()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
  }

  @Test
  void issueToken_sendsPostWithClientCredentialsGrantType() {
    expectTokenRequest(SUCCESS_BODY);

    client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET);

    // POST 메서드 + grant_type=client_credentials + appkey/appsecret 본문 강제.
    mockServer.verify();
  }

  @Test
  void issueToken_missingAccessToken_throwsBrokerApiException() {
    expectTokenRequest(
        "{\"access_token_token_expired\":\"2026-12-31 23:59:59\",\"token_type\":\"Bearer\"}");

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("access_token 필드가 없습니다");
    mockServer.verify();
  }

  @Test
  void issueToken_missingExpiry_throwsBrokerApiException() {
    expectTokenRequest("{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\"}");

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("access_token_token_expired 필드가 없습니다");
    mockServer.verify();
  }

  @Test
  void issueToken_unparsableExpiry_throwsBrokerApiException() {
    expectTokenRequest(
        "{\"access_token\":\"test-access-token\","
            + "\"access_token_token_expired\":\"not-a-timestamp\"}");

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("만료 시각 파싱 실패");
    mockServer.verify();
  }

  @Test
  void issueToken_emptyBody_throwsBrokerApiException() {
    expectTokenRequest("");

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class);
    mockServer.verify();
  }

  @Test
  void issueToken_http500_throwsBrokerApiException() {
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("KIS 토큰 발급");
    mockServer.verify();
  }

  @Test
  void issueToken_timeoutLikeFailure_throwsBrokerApiException() {
    // 타임아웃 성격의 전송 실패(IOException)를 흉내 낸다.
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withException(new java.net.SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class);
    mockServer.verify();
  }

  @Test
  void issueToken_failure_neverLeaksAppKeyOrSecretIntoExceptionMessage() {
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .satisfies(
            thrown -> {
              String message = thrown.getMessage() == null ? "" : thrown.getMessage();
              assertThat(message).doesNotContain(DUMMY_APP_KEY, DUMMY_APP_SECRET);
            });
    mockServer.verify();
  }

  @Test
  void issueToken_doesNotRetryOnFailure() {
    // 재시도하지 않는다(재발급은 분당 1회 제한). 요청이 정확히 1건이어야 mockServer.verify()가 통과한다.
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOf(BrokerApiException.class);
    mockServer.verify();
  }

  @Test
  void issueToken_http403InvalidKey_isClassifiedAsAuthFailed() {
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error_code\":\"EGW00103\"}"));

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOfSatisfying(
            BrokerApiException.class, e -> assertThat(e.getStatusCode()).isEqualTo("auth_failed"))
        .hasMessageNotContaining(DUMMY_APP_KEY)
        .hasMessageNotContaining(DUMMY_APP_SECRET);
  }

  @Test
  void issueToken_perMinuteLimit_isClassifiedAsRateLimitedNotAuthFailed() {
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error_code\":\"EGW00133\"}"));

    assertThatThrownBy(() -> client.issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET))
        .isInstanceOfSatisfying(
            BrokerApiException.class, e -> assertThat(e.getStatusCode()).isEqualTo("rate_limited"));
  }

  private void expectTokenRequest(String responseBody) {
    mockServer
        .expect(requestTo(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("\"grant_type\":\"client_credentials\"")))
        .andExpect(content().string(containsString("\"appkey\":\"" + DUMMY_APP_KEY + "\"")))
        .andExpect(content().string(containsString("\"appsecret\":\"" + DUMMY_APP_SECRET + "\"")))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }
}
