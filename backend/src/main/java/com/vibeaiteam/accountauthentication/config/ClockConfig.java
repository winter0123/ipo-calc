package com.vibeaiteam.accountauthentication.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각 의존 로직을 테스트에서 고정할 수 있도록 {@link Clock}을 빈으로 제공한다. */
@Configuration
public class ClockConfig {

  /**
   * 서울 시간대 시스템 시계.
   *
   * @return Asia/Seoul 기준 {@link Clock}
   */
  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
