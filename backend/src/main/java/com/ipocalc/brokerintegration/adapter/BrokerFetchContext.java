package com.ipocalc.brokerintegration.adapter;

import com.ipocalc.brokerintegration.adapter.kis.KisAccessToken;
import java.util.Set;

/**
 * 증권사 매도 조회 한 번에 필요한 입력.
 *
 * <p>DB·설정에 저장되지 않고 호출 인자로만 전달된다. {@code toString()}은 어떤 값도 출력하지 않는다.
 *
 * @param securitiesAccountId 증권계좌 ID (감사 로그에만 쓰인다)
 * @param appKey 앱키
 * @param appSecret 앱시크릿
 * @param accountNumber 계좌번호 (숫자만, 증권사가 안 쓰면 무시된다)
 * @param token 접근토큰
 * @param stockCodes 우리 배정의 종목코드 (종목별로 조회하는 증권사가 쓴다)
 */
public record BrokerFetchContext(
    Long securitiesAccountId,
    String appKey,
    String appSecret,
    String accountNumber,
    KisAccessToken token,
    Set<String> stockCodes) {

  @Override
  public String toString() {
    return "BrokerFetchContext[***]";
  }
}
