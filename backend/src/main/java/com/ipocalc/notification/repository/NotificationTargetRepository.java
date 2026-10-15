package com.ipocalc.notification.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 오늘 알림을 받을 (계정, 공모주) 조합을 고르고 발송 기록(notification_logs)을 다룬다.
 *
 * <p>다른 Epic 테이블(ipos·subscriptions·allocations·sales·investors)을 읽기만 하는 질의라
 * 엔티티 대신 SQL로 쓴다. 이렇게 하면 패키지
 * 사이 의존이 생기지 않는다.
 */
@Repository
public class NotificationTargetRepository {

  /** 알림 대상 한 건. */
  public record Target(long accountId, long ipoId, String ipoName) {}

  private static final String ACTIVE_ACCOUNTS =
      "(SELECT DISTINCT account_id FROM push_subscriptions WHERE status = 'ACTIVE')";

  private final JdbcTemplate jdbc;

  /** JdbcTemplate을 주입받는다. */
  public NotificationTargetRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 오늘 청약이 마감되는 공모주 전체 × 마감 알림을 켠 구독 계정. */
  public List<Target> findSubscriptionDeadlineTargets(LocalDate today) {
    return jdbc.query(
        "SELECT p.account_id, i.id, i.ipo_name FROM ipos i CROSS JOIN "
            + ACTIVE_ACCOUNTS
            + " p LEFT JOIN notification_preferences np ON np.account_id = p.account_id"
            + " WHERE i.status = 'ACTIVE' AND i.subscription_end_date = ?"
            + " AND COALESCE(np.subscription_deadline, TRUE) ORDER BY p.account_id, i.id",
        (rs, n) -> new Target(rs.getLong(1), rs.getLong(2), rs.getString(3)),
        today);
  }

  /** 오늘 상장하는 공모주 중, 계정의 활성 배정에 남은 수량(배정-활성 매도 합)이 있는 것 × 상장일 알림을 켠 구독 계정. */
  public List<Target> findListingDayTargets(LocalDate today) {
    return jdbc.query(
        "SELECT p.account_id, i.id, i.ipo_name FROM ipos i CROSS JOIN "
            + ACTIVE_ACCOUNTS
            + " p LEFT JOIN notification_preferences np ON np.account_id = p.account_id"
            + " WHERE i.status = 'ACTIVE' AND i.listing_date = ?"
            + " AND COALESCE(np.listing_day, TRUE)"
            + " AND EXISTS (SELECT 1 FROM subscriptions s"
            + "   JOIN investors inv ON inv.id = s.investor_id AND inv.status = 'ACTIVE'"
            + "     AND inv.account_id = p.account_id"
            + "   JOIN allocations a ON a.subscription_id = s.id AND a.status = 'ACTIVE'"
            + "   WHERE s.ipo_id = i.id AND s.status = 'ACTIVE'"
            + "   AND a.allocated_quantity > COALESCE((SELECT SUM(sl.quantity) FROM sales sl"
            + "     WHERE sl.allocation_id = a.id AND sl.status = 'ACTIVE'), 0))"
            + " ORDER BY p.account_id, i.id",
        (rs, n) -> new Target(rs.getLong(1), rs.getLong(2), rs.getString(3)),
        today);
  }

  /** 오늘 이미 보낸 알림인지. */
  public boolean wasSent(long accountId, String type, long refId, LocalDate day) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_logs"
                + " WHERE account_id = ? AND type = ? AND ref_id = ? AND sent_on = ?",
            Integer.class,
            accountId,
            type,
            refId,
            day);
    return count != null && count > 0;
  }

  /** 발송 기록을 남긴다. 이미 있으면 무시한다. */
  public void markSent(long accountId, String type, long refId, LocalDate day) {
    jdbc.update(
        "INSERT INTO notification_logs (account_id, type, ref_id, sent_on) VALUES (?, ?, ?, ?)"
            + " ON CONFLICT DO NOTHING",
        accountId,
        type,
        refId,
        day);
  }
}
