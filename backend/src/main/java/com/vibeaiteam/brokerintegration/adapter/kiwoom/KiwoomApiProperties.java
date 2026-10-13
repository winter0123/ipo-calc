package com.vibeaiteam.brokerintegration.adapter.kiwoom;

import com.vibeaiteam.brokerintegration.adapter.BrokerHttp;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 키움증권 REST API 설정. base-url은 설정({@code app.kiwoom.base-url})에서만 바뀌며 운영·모의 호스트만 허용한다. */
@Component
@ConfigurationProperties(prefix = "app.kiwoom")
public class KiwoomApiProperties {

  /** 인증정보 {@code apiProvider}에 저장되는 식별자. */
  public static final String PROVIDER = "KIWOOM";

  private static final Set<String> ALLOWED_HOSTS = Set.of("api.kiwoom.com", "mockapi.kiwoom.com");

  private String baseUrl = "https://api.kiwoom.com";

  /**
   * 키움 API 기본 URL을 반환한다.
   *
   * @return 설정된 base-url
   */
  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * 키움 API 기본 URL을 설정한다(설정 바인딩 전용).
   *
   * @param baseUrl 키움 API 기본 URL
   */
  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /** 기동 시 base-url을 검증한다. */
  @PostConstruct
  public void validateBaseUrl() {
    BrokerHttp.validateBaseUrl("키움", baseUrl, ALLOWED_HOSTS);
  }
}
