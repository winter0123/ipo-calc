package com.ipocalc.ipocatalog.service.ipo.openprice;

import com.ipocalc.ipocatalog.service.holiday.DataGoKrApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 주식시세 정보 클라이언트 빈. 키가 없어도 기동은 성공하고 호출 시점에 검증한다. */
@Configuration
public class StockPriceClientConfig {

  /** 공공데이터포털 주식시세 정보 클라이언트 빈. */
  @Bean
  public StockPriceClient stockPriceClient(DataGoKrApiProperties properties) {
    return new StockPriceClient(RestClient.builder(), properties);
  }
}
