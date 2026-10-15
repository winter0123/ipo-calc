package com.ipocalc.brokerintegration.adapter.kiwoom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipocalc.brokerintegration.adapter.BrokerTokenIssuer;
import com.ipocalc.brokerintegration.adapter.kis.KisAccessToken;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 키움증권 접근토큰 발급({@code POST /oauth2/token}) 클라이언트. 재시도하지 않는다.
 *
 * <p>접근토큰 발급 POST만 호출할 수 있는 클래스다(ArchUnit {@code BrokerReadOnlyArchitectureTest}가 강제). 앱키·시크릿·토큰은
 * 로그와 예외 메시지에 담기지 않는다.
 */
public class KiwoomAuthClient implements BrokerTokenIssuer {

  /** 토큰·조회 실패 때 사용자에게 보여 줄 안내. */
  static final String IP_HINT = "키움 REST API는 사용 신청과 서버 IP 등록이 필요합니다.";

  private static final Logger log = LoggerFactory.getLogger(KiwoomAuthClient.class);
  private static final String TOKEN_PATH = "/oauth2/token";
  private static final DateTimeFormatter EXPIRES_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final RestClient restClient;

  /**
   * 생성자.
   *
   * @param restClient base-url이 설정된 RestClient
   */
  public KiwoomAuthClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public String provider() {
    return KiwoomApiProperties.PROVIDER;
  }

  @Override
  public KisAccessToken issueToken(String appKey, String appSecret) {
    log.info("키움 접근토큰 발급 요청: method=POST, path={}", TOKEN_PATH);
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
        throw new BrokerApiException("null_response", "키움 토큰 발급 응답이 비어 있습니다. " + IP_HINT);
      }
      if (!"0".equals(response.returnCode())) {
        throw new BrokerApiException(
            "kiwoom_api_error",
            "키움 토큰 발급 실패: return_code="
                + response.returnCode()
                + ", return_msg="
                + response.returnMsg()
                + ". "
                + IP_HINT);
      }
      if (response.token() == null || response.token().isBlank()) {
        throw new BrokerApiException("missing_access_token", "키움 토큰 발급 응답에 token 필드가 없습니다.");
      }
      LocalDateTime expiresAt;
      try {
        // 만료 시각은 KST 기준이라 변환하지 않는다.
        expiresAt = LocalDateTime.parse(response.expiresDt(), EXPIRES_FORMATTER);
      } catch (RuntimeException e) {
        throw new BrokerApiException("invalid_expires_format", "키움 토큰 만료 시각을 해석할 수 없습니다.", e);
      }
      log.info("키움 접근토큰 발급 성공: path={}, expiresAt={}", TOKEN_PATH, expiresAt);
      return new KisAccessToken(response.token(), expiresAt);
    } catch (BrokerApiException e) {
      log.warn("키움 접근토큰 발급 실패: path={}, reason={}", TOKEN_PATH, e.getStatusCode());
      throw e;
    } catch (RestClientResponseException e) {
      // 응답 본문은 로그·예외 메시지에 싣지 않는다.
      throw new BrokerApiException(
          e.getStatusCode().is4xxClientError() ? "auth_failed" : "token_issue_failed",
          "키움 토큰 발급이 거절되었습니다: HTTP " + e.getStatusCode().value() + ". " + IP_HINT,
          e);
    } catch (RuntimeException e) {
      throw new BrokerApiException(
          "token_issue_failed",
          "키움 토큰 발급 중 오류 발생: " + e.getClass().getSimpleName() + ". " + IP_HINT,
          e);
    }
  }

  private record TokenRequest(
      @JsonProperty("grant_type") String grantType,
      @JsonProperty("appkey") String appKey,
      @JsonProperty("secretkey") String secretKey) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenResponse(
      @JsonProperty("token") String token,
      @JsonProperty("expires_dt") String expiresDt,
      @JsonProperty("return_code") String returnCode,
      @JsonProperty("return_msg") String returnMsg) {}
}
