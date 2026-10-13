package com.vibeaiteam.brokerintegration.adapter.kis;

import com.vibeaiteam.brokerintegration.adapter.BrokerHttp;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * KIS API 설정 프로퍼티.
 *
 * <p>base-url은 설정 파일({@code app.kis.base-url})에서만 주입되며 요청 입력으로는 절대 바뀌지 않는다(research 4.3-C 경로
 * allowlist). 기동 시 호스트가 허용 목록에 없으면 예외를 던져 기동을 막는다. 모의투자 호스트는 의도적으로 포함하지 않았다.
 */
@Component
@ConfigurationProperties(prefix = "app.kis")
public class KisApiProperties {

  /** 인증정보 {@code apiProvider}에 저장되는 식별자. */
  public static final String PROVIDER = "KIS";

  /** 허용되는 KIS 실거래 API 호스트. 모의투자 호스트는 포함하지 않는다. */
  private static final Set<String> ALLOWED_HOSTS = Set.of("openapi.koreainvestment.com");

  private String baseUrl = "https://openapi.koreainvestment.com:9443";

  /**
   * KIS API 기본 URL을 반환한다.
   *
   * @return 설정된 base-url
   */
  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * KIS API 기본 URL을 설정한다(설정 바인딩 전용).
   *
   * @param baseUrl KIS API 기본 URL
   */
  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /**
   * 기동 시 base-url의 호스트가 허용 목록에 있고 https인지 검증한다.
   *
   * @throws IllegalStateException 형식이 올바르지 않거나 허용되지 않은 호스트인 경우
   */
  @PostConstruct
  public void validateBaseUrl() {
    BrokerHttp.validateBaseUrl("KIS", baseUrl, ALLOWED_HOSTS);
  }
}
