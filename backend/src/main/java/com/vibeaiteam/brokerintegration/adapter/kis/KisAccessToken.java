package com.vibeaiteam.brokerintegration.adapter.kis;

import java.time.LocalDateTime;

/**
 * KIS 접근토큰.
 *
 * <p>토큰 값과 만료 시각을 담는다. 유효기간은 1일이며 재발급은 분당 1회로 제한되므로 캐싱 responsibility는 SF-2 저장소가 담당한다(이 어댑터는 발급만
 * 한다). {@code toString()}을 오버라이드해 토큰 값을 마스킹한다.
 */
public record KisAccessToken(String token, LocalDateTime expiresAt) {

  /**
   * 토큰 값을 노출하지 않는 문자열을 반환한다.
   *
   * @return 항상 {@code KisAccessToken[***]}
   */
  @Override
  public String toString() {
    return "KisAccessToken[***]";
  }
}
