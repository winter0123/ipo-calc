package com.ipocalc.brokerintegration.adapter;

import com.ipocalc.brokerintegration.adapter.kis.KisAccessToken;

/** 증권사별 접근토큰 발급 포트. 캐시·락은 {@code BrokerTokenService}가 공통으로 맡는다. */
public interface BrokerTokenIssuer {

  /**
   * 이 발급기가 담당하는 증권사 식별자.
   *
   * @return 인증정보의 {@code apiProvider} 값
   */
  String provider();

  /**
   * 앱키·앱시크릿으로 접근토큰을 1회 발급한다(재시도 없음).
   *
   * @param appKey 앱키
   * @param appSecret 앱시크릿
   * @return 접근토큰과 만료 시각(KST)
   */
  KisAccessToken issueToken(String appKey, String appSecret);
}
