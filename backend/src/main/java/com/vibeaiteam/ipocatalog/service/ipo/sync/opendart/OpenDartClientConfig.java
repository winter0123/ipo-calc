package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** OpenDART 클라이언트 빈 설정. API 키가 없어도 앱 기동이 실패하지 않도록 런타임 검증 위임. */
@Configuration
public class OpenDartClientConfig {

  /** OpenDART 기업 공시 조회 클라이언트 빈. */
  @Bean
  public OpenDartCorpDisclosureClient openDartCorpDisclosureClient(
      OpenDartApiProperties properties, Clock clock) {
    return new OpenDartCorpDisclosureClient(RestClient.builder(), properties, clock);
  }

  /** OpenDART 지분증권 조회 클라이언트 빈. */
  @Bean
  public OpenDartEquitySecuritiesClient openDartEquitySecuritiesClient(
      OpenDartApiProperties properties) {
    return new OpenDartEquitySecuritiesClient(RestClient.builder(), properties);
  }

  /** OpenDART 공모 공시(투자설명서·증권신고서) 조회 클라이언트 빈. 문서가 수 MB라 읽기 타임아웃을 60초로 둔다. */
  @Bean
  public DartIpoFilingClient dartIpoFilingClient(OpenDartApiProperties properties) {
    return new DartIpoFilingClient(RestClient.builder(), properties, 5_000, 60_000);
  }
}
