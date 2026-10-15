package com.ipocalc.ipocatalog.service.ipo.sync.ustockplus;

import com.ipocalc.ipocatalog.service.support.HttpClientSupport;
import com.ipocalc.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    return new UstockPlusCalendarClient(
        RestClient.builder().requestFactory(HttpClientSupport.requestFactory()),
        properties,
        new RequestThrottle(70));
  }
}
