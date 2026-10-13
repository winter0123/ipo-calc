package com.vibeaiteam.accountauthentication.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.controller.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/** 요청 제한 필터와 고정 윈도 제한기의 단위 동작을 검증한다. */
class RateLimitFilterTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void publicReadAllowsUpToLimitThenReturns429WithRetryAfter() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(3, 20, false), clock);

    for (int i = 0; i < 3; i++) {
      MockHttpServletResponse allowed = call(filter, "GET", "/ipos", "198.51.100.7", null);
      assertThat(allowed.getStatus()).isEqualTo(200);
      assertThat(allowed.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    MockHttpServletResponse limited = call(filter, "GET", "/ipos", "198.51.100.7", null);
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    assertThat(limited.getContentType()).isEqualTo("application/json;charset=UTF-8");
    ErrorResponse body =
        objectMapper.readValue(limited.getContentAsByteArray(), ErrorResponse.class);
    assertThat(body.getError()).isEqualTo("RATE_LIMITED");
    assertThat(body.getMessage()).isEqualTo("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
  }

  @Test
  void publicReadAllowsAgainAfterWindowPasses() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(3, 20, false), clock);
    exhaustPublicRead(filter, "198.51.100.7");

    clock.advanceSeconds(30);
    MockHttpServletResponse sameWindow = call(filter, "GET", "/ipos", "198.51.100.7", null);
    assertThat(sameWindow.getStatus()).isEqualTo(429);
    assertThat(sameWindow.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");

    clock.advanceSeconds(30);
    MockHttpServletResponse nextWindow = call(filter, "GET", "/ipos", "198.51.100.7", null);
    assertThat(nextWindow.getStatus()).isEqualTo(200);
    assertThat(nextWindow.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
  }

  @Test
  void countersAreIndependentPerIpAndPerGroup() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(3, 2, false), clock);
    exhaustPublicRead(filter, "198.51.100.7");

    MockHttpServletResponse otherIp = call(filter, "GET", "/ipos", "198.51.100.8", null);
    assertThat(otherIp.getStatus()).isEqualTo(200);

    MockHttpServletResponse loginSameIp = call(filter, "POST", "/auth/login", "198.51.100.7", null);
    assertThat(loginSameIp.getStatus()).isEqualTo(200);
  }

  @Test
  void authGroupLimitsLoginAndSignupButLeavesOtherApisAlone() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(120, 2, false), clock);
    String ip = "198.51.100.7";

    assertThat(call(filter, "POST", "/auth/login", ip, null).getStatus()).isEqualTo(200);
    assertThat(call(filter, "POST", "/auth/login", ip, null).getStatus()).isEqualTo(200);
    MockHttpServletResponse limited = call(filter, "POST", "/auth/login", ip, null);
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

    // 같은 그룹인 가입(POST /accounts)도 같은 카운터를 쓴다.
    assertThat(call(filter, "POST", "/accounts", ip, null).getStatus()).isEqualTo(429);

    // 로그인·가입이 아닌 보호 API(GET /investors)는 제한 대상이 아니다.
    for (int i = 0; i < 5; i++) {
      MockHttpServletResponse other = call(filter, "GET", "/investors", ip, null);
      assertThat(other.getStatus()).isEqualTo(200);
      assertThat(other.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }
  }

  @Test
  void forwardedForHeaderIsIgnoredByDefault() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(3, 20, false), clock);

    exhaustPublicRead(filter, "198.51.100.7", "203.0.113.50");

    // 헤더가 아니라 remoteAddr로 셌므로 같은 remoteAddr는 계속 막히고 헤더의 IP는 카운터가 비어 있다.
    MockHttpServletResponse sameRemoteAddr =
        call(filter, "GET", "/ipos", "198.51.100.7", "203.0.113.50");
    assertThat(sameRemoteAddr.getStatus()).isEqualTo(429);
    MockHttpServletResponse forwardedIp = call(filter, "GET", "/ipos", "203.0.113.50", null);
    assertThat(forwardedIp.getStatus()).isEqualTo(200);
  }

  @Test
  void forwardedForFirstValueIsUsedOnlyWhenTrusted() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(3, 20, true), clock);
    String forwarded = "203.0.113.50, 192.0.2.9";

    exhaustPublicRead(filter, "198.51.100.7", forwarded);

    // 첫 값(203.0.113.50)으로 셌으므로 같은 IP는 계속 막히고 마지막 홉은 카운터가 비어 있다.
    assertThat(call(filter, "GET", "/ipos", "203.0.113.50", null).getStatus()).isEqualTo(429);
    assertThat(call(filter, "GET", "/ipos", "192.0.2.9", null).getStatus()).isEqualTo(200);
    assertThat(call(filter, "GET", "/ipos", "198.51.100.7", null).getStatus()).isEqualTo(200);
  }

  @Test
  void malformedForwardedValueFallsBackToRemoteAddrEvenWhenTrusted() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(3, 20, true), clock);
    String ip = "198.51.100.7";

    for (int i = 0; i < 3; i++) {
      assertThat(call(filter, "GET", "/ipos", ip, "unknown").getStatus()).isEqualTo(200);
    }
    // 빈 헤더도 remoteAddr로 셌고, 그 IP는 이미 한도에 걸려 있다.
    assertThat(call(filter, "GET", "/ipos", ip, "").getStatus()).isEqualTo(429);
    // 형식이 정상인 헤더는 신뢰 모드에서 새 IP로 센다.
    assertThat(call(filter, "GET", "/ipos", ip, "203.0.113.99").getStatus()).isEqualTo(200);
  }

  @Test
  void nonTargetRequestsArePassedThroughUntouched() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(1, 1, true), clock);

    for (int i = 0; i < 5; i++) {
      MockHttpServletResponse patch = call(filter, "PATCH", "/ipos/1", "198.51.100.7", null);
      assertThat(patch.getStatus()).isEqualTo(200);
      assertThat(patch.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }
  }

  @Test
  void contextPathIsStrippedBeforeMatchingGroup() throws Exception {
    MutableClock clock = MutableClock.at(START);
    RateLimitFilter filter = filter(properties(1, 20, false), clock);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ipos");
    request.setContextPath("/api");
    request.setRemoteAddr("198.51.100.7");

    for (int expected : new int[] {200, 429}) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, (req, res) -> {});
      assertThat(response.getStatus()).isEqualTo(expected);
    }
  }

  @Test
  void pathPrefixLookalikesAreNotRateLimited() throws Exception {
    RateLimitFilter filter = filter(properties(1, 1, false), MutableClock.at(START));

    for (String uri : new String[] {"/iposx", "/market-holidaysx", "/ipos-admin"}) {
      for (int i = 0; i < 3; i++) {
        assertThat(call(filter, "GET", uri, "198.51.100.7", null).getStatus()).isEqualTo(200);
      }
    }
    // 가입·로그인 경로의 GET은 인증 그룹이 아니다.
    for (int i = 0; i < 3; i++) {
      assertThat(call(filter, "GET", "/auth/login", "198.51.100.7", null).getStatus())
          .isEqualTo(200);
    }
  }

  @Test
  void trustedForwardedHeaderAcceptsIpv6ButRejectsMalformedAddresses() throws Exception {
    RateLimitFilter filter = filter(properties(1, 20, true), MutableClock.at(START));
    String remote = "198.51.100.7";

    // IPv6 첫 값은 그 값으로 센다: 같은 값은 두 번째에 막히고 remoteAddr 카운터는 비어 있다.
    assertThat(call(filter, "GET", "/ipos", remote, "2001:db8::1").getStatus()).isEqualTo(200);
    assertThat(call(filter, "GET", "/ipos", remote, "2001:db8::1").getStatus()).isEqualTo(429);

    // 잘못된 값들은 모두 remoteAddr로 세므로 첫 호출만 통과하고 이후는 막힌다.
    String[] malformed = {
      "1.2.3", "1.2.3.4.5", "256.1.1.1", "1..3.4", "1234.1.1.1", "a.b.c.d", "gg::1",
      "1.2.3.-4", "x".repeat(46)
    };
    assertThat(call(filter, "GET", "/ipos", remote, malformed[0]).getStatus()).isEqualTo(200);
    for (String bad : malformed) {
      assertThat(call(filter, "GET", "/ipos", remote, bad).getStatus())
          .as(bad)
          .isEqualTo(429);
    }
  }

  @Test
  void limiterEvictsExpiredWindowsOnlyAfterKeyCountExceedsThreshold() {
    MutableClock clock = MutableClock.at(START);
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);
    for (int i = 0; i < 10_001; i++) {
      limiter.tryAcquire("old-" + i, 5);
    }
    clock.advanceSeconds(60);
    // 새 윈도에서 키 수가 임계값을 넘은 채로 호출하면 지난 윈도 항목이 정리된다.
    limiter.tryAcquire("fresh", 1);
    assertThat(limiter.tryAcquire("fresh", 1).allowed()).isFalse();
    // 정리된 키는 새 윈도에서 다시 1부터 센다.
    assertThat(limiter.tryAcquire("old-0", 1).allowed()).isTrue();
  }

  @Test
  void limiterReportsMinimumOneSecondRetryAtWindowEnd() {
    MutableClock clock = MutableClock.at(START.plusMillis(59_999));
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);
    limiter.tryAcquire("k", 1);

    FixedWindowRateLimiter.Decision d = limiter.tryAcquire("k", 1);

    assertThat(d.allowed()).isFalse();
    assertThat(d.retryAfterSeconds()).isEqualTo(1L);
  }

  private void exhaustPublicRead(RateLimitFilter filter, String ip) throws Exception {
    exhaustPublicRead(filter, ip, null);
  }

  private void exhaustPublicRead(RateLimitFilter filter, String ip, String forwarded)
      throws Exception {
    for (int i = 0; i < 3; i++) {
      MockHttpServletResponse response = call(filter, "GET", "/market-holidays", ip, forwarded);
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  private RateLimitFilter filter(RateLimitProperties properties, Clock clock) {
    return new RateLimitFilter(properties, new FixedWindowRateLimiter(clock), objectMapper);
  }

  private static RateLimitProperties properties(int publicRead, int auth, boolean trustForwarded) {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setPublicReadPerMinute(publicRead);
    properties.setAuthPerMinute(auth);
    properties.setTrustForwardedHeader(trustForwarded);
    return properties;
  }

  private static MockHttpServletResponse call(
      RateLimitFilter filter, String method, String uri, String remoteAddr, String forwarded)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRemoteAddr(remoteAddr);
    if (forwarded != null) {
      request.addHeader("X-Forwarded-For", forwarded);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainCalled = new AtomicBoolean();
    FilterChain chain = (req, res) -> chainCalled.set(true);
    filter.doFilter(request, response, chain);
    if (response.getStatus() == 200) {
      assertThat(chainCalled.get()).isTrue();
    } else {
      assertThat(chainCalled.get()).isFalse();
    }
    return response;
  }

  /** 테스트에서 임의로 전진시킬 수 있는 고정 시점 시계. */
  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private static MutableClock at(Instant instant) {
      return new MutableClock(instant);
    }

    private void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
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
      return instant;
    }
  }
}
