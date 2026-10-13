package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** OpenDART 클라이언트 빈 설정. API 키가 없어도 앱 기동이 실패하지 않도록 런타임 검증 위임. */
@Configuration
public class OpenDartClientConfig {

  /** OpenDART 호출 간격 조절기. 세 클라이언트가 같은 API 키를 쓰므로 인스턴스 하나를 공유한다. */
  @Bean
  public RequestThrottle openDartRequestThrottle() {
    return new RequestThrottle(200);
  }

  /** OpenDART 기업 공시 조회 클라이언트 빈. */
  @Bean
  public OpenDartCorpDisclosureClient openDartCorpDisclosureClient(
      OpenDartApiProperties properties, Clock clock, RequestThrottle openDartRequestThrottle) {
    return new OpenDartCorpDisclosureClient(
        RestClient.builder(), properties, clock, openDartRequestThrottle);
  }

  /** OpenDART 지분증권 조회 클라이언트 빈. */
  @Bean
  public OpenDartEquitySecuritiesClient openDartEquitySecuritiesClient(
      OpenDartApiProperties properties, RequestThrottle openDartRequestThrottle) {
    return new OpenDartEquitySecuritiesClient(
        RestClient.builder(), properties, openDartRequestThrottle);
  }

  /** OpenDART 공모 공시(투자설명서·증권신고서) 조회 클라이언트 빈. 문서가 수 MB라 읽기 타임아웃을 60초로 둔다. */
  @Bean
  public DartIpoFilingClient dartIpoFilingClient(
      OpenDartApiProperties properties, RequestThrottle openDartRequestThrottle) {
    return new DartIpoFilingClient(
        RestClient.builder(), properties, openDartRequestThrottle, 5_000, 60_000);
  }
}
