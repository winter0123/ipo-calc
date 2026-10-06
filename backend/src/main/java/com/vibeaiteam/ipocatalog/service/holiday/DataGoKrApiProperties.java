package com.vibeaiteam.ipocatalog.service.holiday;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털(데이터.go.kr) API 설정 프로퍼티.
 *
 * <p>기존 {@code OpenDartApiProperties}와 동일한 {@code @Value("${...:}")} 패턴을 따른다. 한국천문연구원 특일 정보
 * API({@code B090041}) 호출에 사용한다.
 */
@Component
public class DataGoKrApiProperties {

  private final String serviceKey;
  private final String baseUrl;

  /** 서비스 키와 기본 URL 등 공공데이터포털 API 설정을 주입받는다. */
  public DataGoKrApiProperties(
      @Value("${app.datagokr.service-key:}") String serviceKey,
      @Value("${app.datagokr.base-url:https://apis.data.go.kr}") String baseUrl) {
    this.serviceKey = serviceKey;
    this.baseUrl = baseUrl;
  }

  public String getServiceKey() {
    return serviceKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  /** 서비스 키가 설정되지 않았는지 확인 (빈 문자열 또는 null). */
  public boolean isServiceKeyMissing() {
    return serviceKey == null || serviceKey.isBlank();
  }
}
