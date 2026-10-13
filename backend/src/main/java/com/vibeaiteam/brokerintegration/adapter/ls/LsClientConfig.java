package com.vibeaiteam.brokerintegration.adapter.ls;

import com.vibeaiteam.brokerintegration.adapter.BrokerHttp;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** LS 클라이언트 빈 설정. 인증정보가 없어도 기동은 실패하지 않고 호출 시점에만 실패한다. */
@Configuration
public class LsClientConfig {

  /**
   * LS 접근토큰 발급 클라이언트 빈.
   *
   * @param properties LS API 설정
   * @param clock 시각 기준
   * @return 토큰 발급 클라이언트
   */
  @Bean
  public LsAuthClient lsAuthClient(LsApiProperties properties, Clock clock) {
    return new LsAuthClient(
        BrokerHttp.newClient(RestClient.builder(), properties.getBaseUrl()), clock);
  }

  /**
   * LS 매매일지 조회 클라이언트 빈.
   *
   * @param properties LS API 설정
   * @param holidays 시장 휴장일 리포지터리
   * @return 매도 조회 소스
   */
  @Bean
  public LsDailyTradeClient lsDailyTradeClient(
      LsApiProperties properties, MarketHolidayRepository holidays) {
    return new LsDailyTradeClient(
        BrokerHttp.newClient(RestClient.builder(), properties.getBaseUrl()),
        holidays::existsByHolidayDate);
  }
}
