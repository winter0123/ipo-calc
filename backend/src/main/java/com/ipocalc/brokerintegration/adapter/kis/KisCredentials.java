package com.ipocalc.brokerintegration.adapter.kis;

/**
 * KIS 인증 자격증명.
 *
 * <p>앱키, 앱시크릿, 계좌번호(CANO), 계좌상품코드(ACNT_PRDT_CD)를 포함한다. 이 레코드는 DB·설정 파일에 저장되지 않고 호출 시점의 인자로만 전달된다.
 * {@code toString()}을 오버라이드해 어떤 값도 출력하지 않는다.
 */
public record KisCredentials(String appKey, String appSecret, String cano, String acntPrdtCd) {

  /**
   * 어떤 값도 노출하지 않는 문자열을 반환한다.
   *
   * @return 항상 {@code KisCredentials[***]}
   */
  @Override
  public String toString() {
    return "KisCredentials[***]";
  }
}
