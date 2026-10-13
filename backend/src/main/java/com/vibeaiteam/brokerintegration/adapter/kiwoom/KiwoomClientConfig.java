package com.vibeaiteam.brokerintegration.adapter.kiwoom;

import com.vibeaiteam.brokerintegration.adapter.BrokerHttp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 키움 클라이언트 빈 설정. 인증정보가 없어도 기동은 실패하지 않고 호출 시점에만 실패한다. */
@Configuration
public class KiwoomClientConfig {

  /**
   * 키움 접근토큰 발급 클라이언트 빈.
   *
   * @param properties 키움 API 설정
   * @return 토큰 발급 클라이언트
   */
  @Bean
  public KiwoomAuthClient kiwoomAuthClient(KiwoomApiProperties properties) {
    return new KiwoomAuthClient(
        BrokerHttp.newClient(RestClient.builder(), properties.getBaseUrl()));
  }

  /**
   * 키움 실현손익 조회 클라이언트 빈.
   *
   * @param properties 키움 API 설정
   * @return 매도 조회 소스
   */
  @Bean
  public KiwoomTradeProfitClient kiwoomTradeProfitClient(KiwoomApiProperties properties) {
    return new KiwoomTradeProfitClient(
        BrokerHttp.newClient(RestClient.builder(), properties.getBaseUrl()));
  }
}
