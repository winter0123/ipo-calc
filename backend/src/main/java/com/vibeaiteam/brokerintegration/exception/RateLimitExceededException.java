package com.vibeaiteam.brokerintegration.exception;

/** 짧은 시간에 너무 자주 호출해 제한에 걸렸을 때 던진다. */
public class RateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  /**
   * 생성자.
   *
   * @param retryAfterSeconds 다시 시도해도 되는 시점까지 남은 초
   */
  public RateLimitExceededException(long retryAfterSeconds) {
    super("요청이 너무 잦습니다. " + retryAfterSeconds + "초 뒤에 다시 시도해 주세요.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /**
   * 다시 시도해도 되는 시점까지 남은 초를 돌려준다.
   *
   * @return 남은 초
   */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
