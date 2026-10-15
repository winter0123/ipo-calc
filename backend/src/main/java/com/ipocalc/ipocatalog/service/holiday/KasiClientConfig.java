package com.ipocalc.ipocatalog.service.holiday;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** KASI 특일 정보 클라이언트 빈. 키가 없어도 기동은 성공하고 호출 시점에 검증한다. */
@Configuration
public class KasiClientConfig {

  /** 한국천문연구원 특일 정보 클라이언트 빈. */
  @Bean
  public KasiSpecialDayClient kasiSpecialDayClient(DataGoKrApiProperties properties) {
    return new KasiSpecialDayClient(RestClient.builder(), properties);
  }
}
