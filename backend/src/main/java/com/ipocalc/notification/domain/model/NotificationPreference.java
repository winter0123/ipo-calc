package com.ipocalc.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 계정별 알림 종류 설정. 행이 없으면 모두 켜짐으로 본다. */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

  /** 계정 ID(accounts.id). */
  @Id
  @Column(name = "account_id")
  private Long accountId;

  /** 청약 마감 당일 알림 사용 여부. */
  @Column(name = "subscription_deadline", nullable = false)
  private boolean subscriptionDeadline = true;

  /** 상장일 알림 사용 여부. */
  @Column(name = "listing_day", nullable = false)
  private boolean listingDay = true;

  /** 마지막 수정 시각. */
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = LocalDateTime.now();
  }
}
