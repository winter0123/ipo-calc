package com.ipocalc.ipocatalog.service.ipo.krx;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** KIND 공고 클라이언트 설정. */
@Configuration
public class KrxListingNoticeClientConfig {

  /** KIND 공고 클라이언트 빈. */
  @Bean
  public KrxListingNoticeClient krxListingNoticeClient() {
    return new KrxListingNoticeClient(RestClient.builder());
  }

  /** KIND 공고 파서 빈. */
  @Bean
  public KrxListingNoticeParser krxListingNoticeParser() {
    return new KrxListingNoticeParser();
  }

  /** 시계 빈. */
  @Bean
  public Clock krxListingClock() {
    return Clock.systemDefaultZone();
  }
}
