package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenDART API 설정 프로퍼티.
 *
 * <p>기존 {@code AccountNumberEncryptor}의 {@code @Value("${app.encryption.key:}")} 패턴을 따른다.
 * {@code @ConfigurationProperties}는 현재 프로젝트에서 사용되지 않음(grep 0건 확인).
 */
@Component
public class OpenDartApiProperties {

  private final String apiKey;
  private final String baseUrl;

  /** API 키와 기본 URL 등 OpenDART 설정을 주입받는다. */
  public OpenDartApiProperties(
      @Value("${app.opendart.api-key:}") String apiKey,
      @Value("${app.opendart.base-url:https://opendart.fss.or.kr}") String baseUrl) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public boolean isApiKeyMissing() {
    return apiKey == null || apiKey.isBlank();
  }
}
