package com.vibeaiteam.accountauthentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** {@link ClockConfig}의 서울 시각 기준과 Clock 주입으로 시각을 고정하는 방식을 검증한다. */
class ClockConfigTest {

  @Test
  void clockBeanUsesAsiaSeoulZone() {
    Clock clock = new ClockConfig().clock();

    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
  }

  @Test
  void fixedClockMakesLockExpiryDeterministic() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    LocalDateTime now = LocalDateTime.now(clock);
    Account account = Account.builder().email("a@example.com").build();

    account.setLockedUntil(now.plusSeconds(1));
    assertThat(account.isAccountLocked(now)).isTrue();

    account.setLockedUntil(now);
    assertThat(account.isAccountLocked(now)).isFalse();

    account.setLockedUntil(null);
    assertThat(account.isAccountLocked(now)).isFalse();
  }
}
