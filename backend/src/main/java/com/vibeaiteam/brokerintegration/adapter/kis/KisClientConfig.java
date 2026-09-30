package com.vibeaiteam.brokerintegration.adapter.kis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** KIS 클라이언트 빈 설정. 인증정보가 없어도 앱 기동은 실패하지 않고, 실제 호출 시점에만 실패한다. */
@Configuration
public class KisClientConfig {

  /**
   * KIS 접근토큰 발급 클라이언트 빈.
   *
   * @param properties KIS API 설정
   * @return 토큰 발급 클라이언트
   */
  @Bean
  public KisAuthClient kisAuthClient(KisApiProperties properties) {
    return new KisAuthClient(RestClient.builder(), properties.getBaseUrl());
  }

  /**
   * KIS 기간별 매매손익 조회 클라이언트 빈.
   *
   * @param properties KIS API 설정
   * @return 기간별 매매손익 조회 클라이언트
   */
  @Bean
  public KisPeriodTradeProfitClient kisPeriodTradeProfitClient(KisApiProperties properties) {
    return new KisPeriodTradeProfitClient(RestClient.builder(), properties.getBaseUrl());
  }
}
