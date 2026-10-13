package com.vibeaiteam.brokerintegration.adapter.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vibeaiteam.brokerintegration.adapter.BrokerTokenIssuer;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * KIS 인증 클라이언트 — 접근토큰 발급({@code /oauth2/tokenP}) 전담.
 *
 * <p>{@code brokerintegration.adapter} 패키지에서 {@code RestClient.post()}를 호출할 수 있는 유일한 클래스다(ArchUnit
 * {@code BrokerReadOnlyArchitectureTest}가 강제). 이 클래스도 put/patch/delete/method 호출은 금지된다. 재시도는 하지 않는다
 * — 재발급은 분당 1회로 제한되기 때문이다.
 */
public class KisAuthClient implements BrokerTokenIssuer {

  private static final Logger log = LoggerFactory.getLogger(KisAuthClient.class);
  private static final String TOKEN_PATH = "/oauth2/tokenP";
  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 15_000;
  private static final DateTimeFormatter EXPIRES_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final RestClient restClient;

  /**
   * 생성자 (테스트용 — RestClient를 직접 주입).
   *
   * @param restClient 이미 base-url과 requestFactory가 설정된 RestClient
   */
  public KisAuthClient(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * 생성자 (운영용 — base-url과 타임아웃을 설정한 RestClient를 만든다).
   *
   * @param restClientBuilder RestClient 빌더
   * @param baseUrl KIS API base-url
   */
  public KisAuthClient(RestClient.Builder restClientBuilder, String baseUrl) {
    this(
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(createRequestFactory())
            .build());
  }

  private static SimpleClientHttpRequestFactory createRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout(READ_TIMEOUT_MS);
    return factory;
  }

  @Override
  public String provider() {
    return KisApiProperties.PROVIDER;
  }

  /**
   * 앱키와 앱시크릿으로 접근토큰을 발급받는다.
   *
   * <p>재시도하지 않는다. HTTP 오류·응답 필드 누락·만료 시각 파싱 실패·타임아웃은 모두 {@link BrokerApiException}으로 격리되며
   * 앱키·앱시크릿·토큰 값은 예외 메시지에 담기지 않는다(AC-1822).
   *
   * @param appKey KIS 앱키
   * @param appSecret KIS 앱시크릿
   * @return 발급된 접근토큰과 만료 시각
   * @throws BrokerApiException 토큰 발급이 실패한 경우
   */
  @Override
  public KisAccessToken issueToken(String appKey, String appSecret) {
    log.info("KIS 접근토큰 발급 요청: method=POST, path={}", TOKEN_PATH);
    try {
      TokenResponse response =
          restClient
              .post()
              .uri(TOKEN_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new TokenRequest("client_credentials", appKey, appSecret))
              .retrieve()
              .body(TokenResponse.class);

      if (response == null) {
        throw new BrokerApiException("null_response", "KIS 토큰 발급 응답이 null입니다.");
      }
      if (response.accessToken() == null || response.accessToken().isBlank()) {
        throw new BrokerApiException(
            "missing_access_token", "KIS 토큰 발급 응답에 access_token 필드가 없습니다.");
      }
      if (response.accessTokenTokenExpired() == null
          || response.accessTokenTokenExpired().isBlank()) {
        throw new BrokerApiException(
            "missing_expires", "KIS 토큰 발급 응답에 access_token_token_expired 필드가 없습니다.");
      }

      LocalDateTime expiresAt;
      try {
        // KIS 만료 시각은 KST(한국시간) 기준이므로 변환하지 않고 그대로 LocalDateTime으로 받는다.
        expiresAt = LocalDateTime.parse(response.accessTokenTokenExpired(), EXPIRES_FORMATTER);
      } catch (RuntimeException e) {
        throw new BrokerApiException(
            "invalid_expires_format",
            "KIS 토큰 만료 시각 파싱 실패: " + response.accessTokenTokenExpired(),
            e);
      }

      log.info("KIS 접근토큰 발급 성공: method=POST, path={}, expiresAt={}", TOKEN_PATH, expiresAt);
      return new KisAccessToken(response.accessToken(), expiresAt);
    } catch (BrokerApiException e) {
      log.warn("KIS 접근토큰 발급 실패: method=POST, path={}, reason={}", TOKEN_PATH, e.getMessage());
      throw e;
    } catch (RestClientResponseException e) {
      // 응답 본문은 로그·예외 메시지에 싣지 않는다. 분당 1회 제한(EGW00133)과 인증 거부(4xx)만 구분한다.
      boolean rateLimited = e.getResponseBodyAsString().contains("EGW00133");
      boolean authRejected = e.getStatusCode().is4xxClientError() && !rateLimited;
      log.warn(
          "KIS 접근토큰 발급 거절: method=POST, path={}, status={}, rateLimited={}",
          TOKEN_PATH,
          e.getStatusCode().value(),
          rateLimited);
      throw new BrokerApiException(
          rateLimited ? "rate_limited" : authRejected ? "auth_failed" : "token_issue_failed",
          "KIS 토큰 발급이 거절되었습니다: HTTP " + e.getStatusCode().value(),
          e);
    } catch (RuntimeException e) {
      // 예외 메시지에 앱키/앱시크릿이 실리지 않도록 원인 예외의 타입만 남긴다.
      log.error(
          "KIS 접근토큰 발급 중 오류: method=POST, path={}, cause={}",
          TOKEN_PATH,
          e.getClass().getSimpleName());
      throw new BrokerApiException(
          "token_issue_failed", "KIS 토큰 발급 중 오류 발생: " + e.getClass().getSimpleName(), e);
    }
  }

  /**
   * 토큰 발급 요청 바디.
   *
   * @param grantType 항상 {@code client_credentials}
   * @param appKey 앱키
   * @param appSecret 앱시크릿
   */
  private record TokenRequest(
      @JsonProperty("grant_type") String grantType,
      @JsonProperty("appkey") String appKey,
      @JsonProperty("appsecret") String appSecret) {}

  /**
   * 토큰 발급 응답 바디.
   *
   * @param accessToken 접근토큰
   * @param accessTokenTokenExpired 만료 시각 (yyyy-MM-dd HH:mm:ss)
   * @param tokenType 토큰 타입
   * @param expiresIn 유효기간(초)
   */
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("access_token_token_expired") String accessTokenTokenExpired,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") String expiresIn) {}
}
