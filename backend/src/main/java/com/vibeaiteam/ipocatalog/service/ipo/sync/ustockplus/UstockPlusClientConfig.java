package com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** ustockplus 클라이언트 빈 설정. */
@Configuration
public class UstockPlusClientConfig {

  /**
   * ustockplus 캘린더 조회 클라이언트 빈. 연결 5초, 읽기 15초 타임아웃.
   *
   * @param properties ustockplus 설정
   * @return 캘린더 클라이언트
   */
  @Bean
  public UstockPlusCalendarClient ustockPlusCalendarClient(UstockPlusApiProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5_000);
    factory.setReadTimeout(15_000);
    return new UstockPlusCalendarClient(RestClient.builder().requestFactory(factory), properties);
  }
}
