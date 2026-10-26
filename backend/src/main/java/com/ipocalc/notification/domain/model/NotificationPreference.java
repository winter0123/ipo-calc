package com.ipocalc.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** 계정별 알림 종류 설정. 행이 없으면 모두 켜짐으로 본다. */
@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {
  /** Creates default notification preferences for an account. */
  public static NotificationPreference defaultsFor(Long accountId) {
    NotificationPreference p = new NotificationPreference();
    p.accountId = accountId;
    return p;
  }

  /** Updates notification preferences. */
  public void updateSettings(boolean subscriptionDeadline, boolean listingDay) {
    this.subscriptionDeadline = subscriptionDeadline;
    this.listingDay = listingDay;
  }

  /** 계정 ID(accounts.id). */
  @Id
  @Column(name = "account_id")
  @Comment("계정 고유 식별자(기본 키, 계정 테이블 참조)")
  private Long accountId;

  /** 청약 마감 당일 알림 사용 여부. */
  @Column(name = "subscription_deadline", nullable = false)
  @Comment("청약 마감 알림 사용 여부")
  private boolean subscriptionDeadline = true;

  /** 상장일 알림 사용 여부. */
  @Column(name = "listing_day", nullable = false)
  @Comment("상장일 알림 사용 여부")
  private boolean listingDay = true;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  @Comment("마지막 수정 시각")
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = LocalDateTime.now();
  }
}
