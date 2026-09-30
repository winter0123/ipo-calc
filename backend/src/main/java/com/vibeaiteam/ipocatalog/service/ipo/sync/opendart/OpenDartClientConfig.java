package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** OpenDART 클라이언트 빈 설정. API 키가 없어도 앱 기동이 실패하지 않도록 런타임 검증 위임. */
@Configuration
public class OpenDartClientConfig {

  @Bean
  public OpenDartCorpDisclosureClient openDartCorpDisclosureClient(
      OpenDartApiProperties properties) {
    return new OpenDartCorpDisclosureClient(RestClient.builder(), properties);
  }

  @Bean
  public OpenDartEquitySecuritiesClient openDartEquitySecuritiesClient(
      OpenDartApiProperties properties) {
    return new OpenDartEquitySecuritiesClient(RestClient.builder(), properties);
  }
}
