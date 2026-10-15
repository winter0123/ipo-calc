package com.ipocalc.ipocatalog.service.ipo.sync.ustockplus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** ustockplus API 설정. */
@Component
public class UstockPlusApiProperties {

  private final String baseUrl;

  /**
   * 기본 URL을 주입받는다.
   *
   * @param baseUrl ustockplus API 기본 URL
   */
  public UstockPlusApiProperties(
      @Value("${app.ustockplus.base-url:https://api.ustockplus.com}") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /**
   * 기본 URL을 돌려준다.
   *
   * @return 기본 URL
   */
  public String getBaseUrl() {
    return baseUrl;
  }
}
