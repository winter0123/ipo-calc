package com.ipocalc.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.notification.domain.model.PushSubscription;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 대상 선정 질의(청약 마감·상장일 남은 수량)·중복 방지·만료 구독 정리를 검증한다. 발송기는 mock이다. */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
@TestPropertySource(
    properties = {
      "app.webpush.public-key=pub",
      "app.webpush.private-key=priv",
      "app.webpush.subject=mailto:test@example.com"
    })
class NotificationDispatchServiceTest {

  @Autowired private NotificationDispatchService service;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private PushSender sender;

  private LocalDate day;
  private final List<Long> accounts = new ArrayList<>();
  private final List<Long> ipos = new ArrayList<>();
  private String nonce;

  @BeforeEach
  void setUp() {
    reset(sender);
    when(sender.send(any(), any())).thenReturn(PushSender.Result.OK);
    nonce = Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), 36);
    // 다른 데이터와 겹치지 않는 먼 미래 날짜
    day = LocalDate.of(2090, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
  }

  @AfterEach
  void tearDown() {
    for (Long a : accounts) {
      jdbc.update("DELETE FROM notification_logs WHERE account_id = ?", a);
      jdbc.update("DELETE FROM notification_preferences WHERE account_id = ?", a);
      jdbc.update("DELETE FROM push_subscriptions WHERE account_id = ?", a);
      jdbc.update(
          "DELETE FROM sales WHERE allocation_id IN (SELECT a.id FROM allocations a"
              + " JOIN subscriptions s ON s.id = a.subscription_id"
              + " JOIN investors i ON i.id = s.investor_id WHERE i.account_id = ?)",
          a);
      jdbc.update(
          "DELETE FROM allocations WHERE subscription_id IN (SELECT s.id FROM subscriptions s"
              + " JOIN investors i ON i.id = s.investor_id WHERE i.account_id = ?)",
          a);
      jdbc.update(
          "DELETE FROM subscriptions WHERE investor_id IN"
              + " (SELECT id FROM investors WHERE account_id = ?)",
          a);
      jdbc.update("DELETE FROM investors WHERE account_id = ?", a);
      jdbc.update("DELETE FROM accounts WHERE id = ?", a);
    }
    for (Long i : ipos) {
      jdbc.update("DELETE FROM ipos WHERE id = ?", i);
    }
  }

  @Test
  void deadlineGoesToAccountsWithPreferenceOnAndIsNotRepeated() {
    long on = account("on");
    long off = account("off");
    jdbc.update(
        "INSERT INTO notification_preferences (account_id, subscription_deadline,"
            + " listing_day) VALUES (?, FALSE, TRUE)",
        off);
    long ipo = ipo(day.minusDays(10), day, day.plusDays(7));

    assertThat(service.dispatch(day)).isEqualTo(1);
    verify(sender, times(1)).send(endpointOf(on), any());
    verify(sender, never()).send(endpointOf(off), any());

    assertThat(service.dispatch(day)).isZero();
    verify(sender, times(1)).send(endpointOf(on), any());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_logs WHERE account_id = ? AND ref_id = ?",
                Integer.class,
                on,
                ipo))
        .isEqualTo(1);
  }

  @Test
  void listingDayOnlyForAccountsWithRemainingAllocation() {
    long remaining = account("remain");
    long soldOut = account("sold");
    long noAllocation = account("none");
    long ipo = ipo(day.minusDays(20), day.minusDays(15), day);
    allocation(remaining, ipo, 10, 3);
    allocation(soldOut, ipo, 10, 10);

    service.dispatch(day);

    verify(sender, times(1)).send(endpointOf(remaining), any());
    verify(sender, never()).send(endpointOf(soldOut), any());
    verify(sender, never()).send(endpointOf(noAllocation), any());
  }

  @Test
  void goneSubscriptionIsCancelledAndNotLogged() {
    long acc = account("gone");
    ipo(day.minusDays(10), day, null);
    when(sender.send(any(), any())).thenReturn(PushSender.Result.GONE);

    assertThat(service.dispatch(day)).isZero();

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM push_subscriptions WHERE account_id = ?", String.class, acc))
        .isEqualTo("CANCELLED");
  }

  @Test
  void failedDeliveryIsRetriedOnNextRun() {
    long acc = account("fail");
    ipo(day.minusDays(10), day, null);
    when(sender.send(any(), any())).thenReturn(PushSender.Result.FAILED);

    assertThat(service.dispatch(day)).isZero();
    assertThat(service.dispatch(day)).isZero();

    verify(sender, times(2)).send(endpointOf(acc), any());
  }

  private PushSubscription endpointOf(long accountId) {
    String endpoint = endpoint(accountId);
    return argThat(s -> s != null && endpoint.equals(s.getEndpoint()));
  }

  private String endpoint(long accountId) {
    return "https://fcm.googleapis.com/fcm/send/" + nonce + "-" + accountId;
  }

  private long account(String label) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO accounts (email, password_hash, name, nickname) "
                + "VALUES (?, 'x', ?, ?) RETURNING id",
            Long.class,
            "push-" + label + "-" + nonce + "@example.com",
            label,
            label);
    accounts.add(id);
    jdbc.update(
        "INSERT INTO push_subscriptions (account_id, endpoint, p256dh, auth)"
            + " VALUES (?, ?, 'k', 'a')",
        id,
        endpoint(id));
    return id;
  }

  private long ipo(LocalDate start, LocalDate end, LocalDate listing) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO ipos (company_name, ipo_name, subscription_start_date,"
                + " subscription_end_date, listing_date) VALUES (?, ?, ?, ?, ?) RETURNING id",
            Long.class,
            "푸시" + nonce,
            "푸시" + nonce + start,
            start,
            end,
            listing);
    ipos.add(id);
    return id;
  }

  private void allocation(long accountId, long ipoId, long qty, long sold) {
    Long inv =
        jdbc.queryForObject(
            "INSERT INTO investors (account_id, display_name) VALUES (?, '푸시') RETURNING id",
            Long.class,
            accountId);
    Long sub =
        jdbc.queryForObject(
            "INSERT INTO subscriptions (investor_id, securities_account_id, ipo_id, quantity,"
                + " deposit_amount, subscribed_at) VALUES (?, 1, ?, ?, 0, now()) RETURNING id",
            Long.class,
            inv,
            ipoId,
            qty);
    Long alloc =
        jdbc.queryForObject(
            "INSERT INTO allocations (subscription_id, allocated_quantity, allocated_at)"
                + " VALUES (?, ?, now()) RETURNING id",
            Long.class,
            sub,
            qty);
    if (sold > 0) {
      jdbc.update(
          "INSERT INTO sales (allocation_id, quantity, sale_date) VALUES (?, ?, now())",
          alloc,
          sold);
    }
  }
}
