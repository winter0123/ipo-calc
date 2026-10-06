package com.vibeaiteam.brokerintegration.adapter;

import com.vibeaiteam.brokerintegration.adapter.kis.KisAccessToken;
import com.vibeaiteam.brokerintegration.adapter.kis.KisCredentials;
import com.vibeaiteam.brokerintegration.adapter.kis.dto.KisPeriodTradeProfit;
import java.time.LocalDate;

/**
 * 브로커 조회 전용 포트(research 4.3-A 장치 A).
 *
 * <p>조회 메서드만 선언한다. 주문(매수/매도/정정/취소) 관련 메서드는 이 인터페이스에 정의하지 않으며, 구현 클래스에도 대응 메서드를 만들지 않는다. {@code
 * BrokerReadOnlyArchitectureTest}가 메서드명에 주문 키워드가 들어가는 것을 빌드 실패로 막는다(AC-1821).
 */
public interface BrokerQueryPort {

  /**
   * 기간별 매매손익 현황을 조회한다.
   *
   * @param securitiesAccountId 증권계좌 ID (감사 로그에만 쓰인다)
   * @param credentials KIS 인증정보 (앱키·앱시크릿·계좌번호·상품코드)
   * @param token 접근토큰
   * @param from 조회 시작일 (포함)
   * @param to 조회 종료일 (포함)
   * @return 기간별 매매손익 조회 결과
   */
  KisPeriodTradeProfit queryPeriodTradeProfit(
      Long securitiesAccountId,
      KisCredentials credentials,
      KisAccessToken token,
      LocalDate from,
      LocalDate to);
}
