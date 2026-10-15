package com.ipocalc.brokerintegration.adapter.ls;

import com.ipocalc.brokerintegration.adapter.BrokerHttp;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LS증권 OPEN API 설정. base-url은 설정({@code app.ls.base-url})에서만 바뀌며 LS 호스트만 허용한다. */
@Component
@ConfigurationProperties(prefix = "app.ls")
public class LsApiProperties {

  /** 인증정보 {@code apiProvider}에 저장되는 식별자. */
  public static final String PROVIDER = "LS";

  private static final Set<String> ALLOWED_HOSTS = Set.of("openapi.ls-sec.co.kr");

  private String baseUrl = "https://openapi.ls-sec.co.kr:8080";

  /**
   * LS API 기본 URL을 반환한다.
   *
   * @return 설정된 base-url
   */
  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * LS API 기본 URL을 설정한다(설정 바인딩 전용).
   *
   * @param baseUrl LS API 기본 URL
   */
  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /** 기동 시 base-url을 검증한다. */
  @PostConstruct
  public void validateBaseUrl() {
    BrokerHttp.validateBaseUrl("LS", baseUrl, ALLOWED_HOSTS);
  }
}
