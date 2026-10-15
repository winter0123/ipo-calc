package com.ipocalc.brokerintegration.adapter.ls;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipocalc.brokerintegration.adapter.BrokerTokenIssuer;
import com.ipocalc.brokerintegration.adapter.kis.KisAccessToken;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LS증권 접근토큰 발급({@code POST /oauth2/token}, form) 클라이언트. 재시도하지 않는다.
 *
 * <p>접근토큰 발급 POST만 호출할 수 있는 클래스다. 응답은 만료 시각이 아니라 유효초({@code expires_in})를 주므로 주입된 시계의 현재 시각에 더한다.
 *
 * <p>ponytail: 실호출로 확인하지 못한 것 — expires_in이 초 단위인지, 실패 응답의 형식.
 */
public class LsAuthClient implements BrokerTokenIssuer {

  private static final Logger log = LoggerFactory.getLogger(LsAuthClient.class);
  private static final String TOKEN_PATH = "/oauth2/token";

  private final RestClient restClient;
  private final Clock clock;

  /**
   * 생성자.
   *
   * @param restClient base-url이 설정된 RestClient
   * @param clock 만료 시각 계산용 시계(KST)
   */
  public LsAuthClient(RestClient restClient, Clock clock) {
    this.restClient = restClient;
    this.clock = clock;
  }

  @Override
  public String provider() {
    return LsApiProperties.PROVIDER;
  }

  @Override
  public KisAccessToken issueToken(String appKey, String appSecret) {
    log.info("LS 접근토큰 발급 요청: method=POST, path={}", TOKEN_PATH);
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("appkey", appKey);
    form.add("appsecretkey", appSecret);
    form.add("scope", "oob");
    try {
      TokenResponse response =
          restClient
              .post()
              .uri(TOKEN_PATH)
              .contentType(
                  new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8))
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
        throw new BrokerApiException("missing_access_token", "LS 토큰 발급 응답에 access_token 필드가 없습니다.");
      }
      long seconds;
      try {
        seconds = Long.parseLong(response.expiresIn().trim());
      } catch (RuntimeException e) {
        throw new BrokerApiException("invalid_expires_format", "LS 토큰 유효기간을 해석할 수 없습니다.", e);
      }
      LocalDateTime expiresAt = LocalDateTime.now(clock).plusSeconds(seconds);
      log.info("LS 접근토큰 발급 성공: path={}, expiresAt={}", TOKEN_PATH, expiresAt);
      return new KisAccessToken(response.accessToken(), expiresAt);
    } catch (BrokerApiException e) {
      log.warn("LS 접근토큰 발급 실패: path={}, reason={}", TOKEN_PATH, e.getStatusCode());
      throw e;
    } catch (RestClientResponseException e) {
      // 응답 본문은 로그·예외 메시지에 싣지 않는다.
      throw new BrokerApiException(
          e.getStatusCode().is4xxClientError() ? "auth_failed" : "token_issue_failed",
          "LS 토큰 발급이 거절되었습니다: HTTP " + e.getStatusCode().value(),
          e);
    } catch (RuntimeException e) {
      throw new BrokerApiException(
          "token_issue_failed", "LS 토큰 발급 중 오류 발생: " + e.getClass().getSimpleName(), e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") String expiresIn) {}
}
