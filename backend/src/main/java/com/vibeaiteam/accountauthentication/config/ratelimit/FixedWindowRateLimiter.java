package com.vibeaiteam.accountauthentication.config.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP와 제한 그룹을 키로 쓰는 고정 윈도(60초) 요청 카운터.
 *
 * <p>각 키에 윈도 시작 시각과 누적 횟수를 함께 두고, 같은 키의 갱신은 {@link ConcurrentHashMap#compute} 안에서 원자적으로 수행한다. 시각은
 * 주입받은 {@link Clock}을 쓰므로 테스트에서 고정하거나 전진시킬 수 있다.
 */
public class FixedWindowRateLimiter {

  /** 윈도 길이(밀리초). */
  private static final long WINDOW_MILLIS = 60_000L;

  /** 키 수가 이 값을 넘으면 지난 윈도 항목부터 정리한다. */
  private static final int EVICTION_THRESHOLD = 10_000;

  private final Map<String, Window> counters = new ConcurrentHashMap<>();

  private final Clock clock;

  /** 카운터가 읽을 시각을 주입받는다. */
  public FixedWindowRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * 요청 1건을 시도하고 허용 여부를 돌려준다.
   *
   * @param key IP와 제한 그룹을 합친 카운터 키
   * @param limit 한 윈도에서 허용할 최대 요청 수
   * @return 허용 여부와 한도를 넘긴 경우 재시도까지 남은 초
   */
  public Decision tryAcquire(String key, int limit) {
    long now = clock.millis();
    long windowStart = now - Math.floorMod(now, WINDOW_MILLIS);
    Window updated =
        counters.compute(
            key,
            (ignored, current) -> {
              if (current == null || current.windowStart() != windowStart) {
                return new Window(windowStart, 1L);
              }
              return new Window(windowStart, current.count() + 1L);
            });
    if (counters.size() > EVICTION_THRESHOLD) {
      evictExpired(windowStart);
    }
    if (updated.count() > limit) {
      return new Decision(false, remainingSeconds(now, windowStart));
    }
    return new Decision(true, 0L);
  }

  private static long remainingSeconds(long now, long windowStart) {
    long remainingMillis = windowStart + WINDOW_MILLIS - now;
    return Math.max(1L, (remainingMillis + 999L) / 1_000L);
  }

  private void evictExpired(long currentWindowStart) {
    for (Map.Entry<String, Window> entry : counters.entrySet()) {
      if (entry.getValue().windowStart() < currentWindowStart) {
        counters.remove(entry.getKey(), entry.getValue());
      }
    }
  }

  /** 허용 여부와 재시도까지 남은 초를 담은 판정 결과. */
  public record Decision(boolean allowed, long retryAfterSeconds) {}

  /** 한 윈도 구간의 시작 시각과 그 구간에서 세운 요청 횟수. */
  private record Window(long windowStart, long count) {}
}
