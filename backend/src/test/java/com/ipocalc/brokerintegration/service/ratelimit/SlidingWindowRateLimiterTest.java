package com.ipocalc.brokerintegration.service.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ipocalc.brokerintegration.exception.RateLimitExceededException;
import com.ipocalc.brokerintegration.service.ratelimit.SlidingWindowRateLimiter.Limit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

  private static final Limit FIVE_PER_TEN_MINUTES = new Limit(5, Duration.ofMinutes(10));

  private MutableClock clock;
  private SlidingWindowRateLimiter limiter;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
    limiter = new SlidingWindowRateLimiter(clock);
  }

  @Test
  void allowsUpToMaxAttemptsThenRejectsWithRetryAfter() {
    for (int i = 0; i < 5; i++) {
      limiter.acquire("k", FIVE_PER_TEN_MINUTES);
      clock.advance(Duration.ofSeconds(1));
    }

    assertThatThrownBy(() -> limiter.acquire("k", FIVE_PER_TEN_MINUTES))
        .isInstanceOfSatisfying(
            RateLimitExceededException.class,
            e -> assertThat(e.getRetryAfterSeconds()).isBetween(590L, 600L));
  }

  @Test
  void recoversAfterWindowPasses() {
    for (int i = 0; i < 5; i++) {
      limiter.acquire("k", FIVE_PER_TEN_MINUTES);
    }
    clock.advance(Duration.ofMinutes(10).plusSeconds(1));

    assertThatCode(() -> limiter.acquire("k", FIVE_PER_TEN_MINUTES)).doesNotThrowAnyException();
  }

  @Test
  void keysAreIndependent() {
    for (int i = 0; i < 5; i++) {
      limiter.acquire("a", FIVE_PER_TEN_MINUTES);
    }

    assertThatCode(() -> limiter.acquire("b", FIVE_PER_TEN_MINUTES)).doesNotThrowAnyException();
  }

  @Test
  void anyViolatedLimitRejectsAndRejectedCallIsNotRecorded() {
    Limit minInterval = new Limit(1, Duration.ofSeconds(10));
    Limit hourly = new Limit(30, Duration.ofHours(1));
    limiter.acquire("p", minInterval, hourly);

    assertThatThrownBy(() -> limiter.acquire("p", minInterval, hourly))
        .isInstanceOf(RateLimitExceededException.class);
    clock.advance(Duration.ofSeconds(11));

    // 거부된 호출이 기록됐다면 아직도 10초 안에 2건이라 또 거부된다.
    assertThatCode(() -> limiter.acquire("p", minInterval, hourly)).doesNotThrowAnyException();
  }

  @Test
  void longerWindowStillCountsAfterShorterWindowPasses() {
    Limit minInterval = new Limit(1, Duration.ofSeconds(10));
    Limit twoPerMinute = new Limit(2, Duration.ofMinutes(1));
    limiter.acquire("p", minInterval, twoPerMinute);
    clock.advance(Duration.ofSeconds(11));
    limiter.acquire("p", minInterval, twoPerMinute);
    clock.advance(Duration.ofSeconds(11));

    assertThatThrownBy(() -> limiter.acquire("p", minInterval, twoPerMinute))
        .isInstanceOf(RateLimitExceededException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
