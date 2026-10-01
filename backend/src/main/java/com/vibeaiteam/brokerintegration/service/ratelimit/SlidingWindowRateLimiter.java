package com.vibeaiteam.brokerintegration.service.ratelimit;

import com.vibeaiteam.brokerintegration.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 키별 슬라이딩 윈도우 호출 횟수 제한.
 *
 * <p>한 키에 여러 제한(예: 10초에 1회, 1시간에 30회)을 함께 걸 수 있고, 하나라도 넘으면 호출을 기록하지 않고 거부한다.
 *
 * <p>ponytail: 메모리 구현이라 서버 한 대에서만 정확하다. 여러 대로 늘리면 DB나 Redis로 바꿔야 한다. 키 항목은 지우지 않으므로 키 종류(계정·증권계좌)
 * 수만큼만 늘어난다.
 */
@Component
public class SlidingWindowRateLimiter {

  /**
   * 윈도우 안에서 허용하는 최대 횟수.
   *
   * @param maxAttempts 윈도우 안 최대 호출 횟수
   * @param window 윈도우 길이
   */
  public record Limit(int maxAttempts, Duration window) {}

  private final Clock clock;
  private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

  /**
   * 생성자.
   *
   * @param clock 시각 기준
   */
  public SlidingWindowRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * 호출 1회를 기록하고, 제한을 넘으면 예외를 던진다.
   *
   * @param key 제한 단위 키(예: "credential-write:계정ID")
   * @param limits 함께 적용할 제한들
   * @throws RateLimitExceededException 어느 한 제한이라도 넘었을 때(호출은 기록되지 않는다)
   */
  public void acquire(String key, Limit... limits) {
    Instant now = clock.instant();
    Duration longest = Duration.ZERO;
    for (Limit limit : limits) {
      if (limit.window().compareTo(longest) > 0) {
        longest = limit.window();
      }
    }
    Deque<Instant> history = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (history) {
      while (!history.isEmpty() && !history.peekFirst().isAfter(now.minus(longest))) {
        history.removeFirst();
      }
      long retryAfterSeconds = 0;
      for (Limit limit : limits) {
        Instant windowStart = now.minus(limit.window());
        long inWindow = history.stream().filter(t -> t.isAfter(windowStart)).count();
        if (inWindow >= limit.maxAttempts()) {
          Instant oldestInWindow =
              history.stream().filter(t -> t.isAfter(windowStart)).findFirst().orElse(now);
          long wait = Duration.between(now, oldestInWindow.plus(limit.window())).toSeconds() + 1;
          retryAfterSeconds = Math.max(retryAfterSeconds, wait);
        }
      }
      if (retryAfterSeconds > 0) {
        throw new RateLimitExceededException(retryAfterSeconds);
      }
      history.addLast(now);
    }
  }
}
