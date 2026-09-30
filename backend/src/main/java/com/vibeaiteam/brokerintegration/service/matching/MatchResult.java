package com.vibeaiteam.brokerintegration.service.matching;

/**
 * 브로커 매도 집계 1건의 매칭 결과.
 *
 * <p>성공 여부는 reason이 null인지로만 판단하며, 성공이면 allocationId가 반드시 있다. 실패여도 대상 배정이 특정되는 경우(이미 등록됨·분할 합산
 * 불일치·수량 초과)에는 allocationId를 참고용으로 채운다.
 *
 * @param summary 브로커 매도 집계
 * @param allocationId 매칭된(또는 특정된) 배정 ID
 * @param reason 실패 사유, 성공이면 null
 */
public record MatchResult(BrokerSaleSummary summary, Long allocationId, UnmatchedReason reason) {

  /** 성공인데 allocationId가 없으면 예외를 던진다. */
  public MatchResult {
    if (summary == null) {
      throw new IllegalArgumentException("summary는 null일 수 없습니다.");
    }
    if (reason == null && allocationId == null) {
      throw new IllegalArgumentException("성공 결과에는 allocationId가 필요합니다.");
    }
  }

  /**
   * 매칭 성공 여부를 돌려준다.
   *
   * @return reason이 null이면 true
   */
  public boolean isMatched() {
    return reason == null;
  }
}
