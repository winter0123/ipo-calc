package com.ipocalc.announcements.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계정별 공지사항 영구 숨김 기록. */
@Entity
@Table(name = "announcement_dismissals")
@IdClass(AnnouncementDismissalId.class)
@Getter
@NoArgsConstructor
public class AnnouncementDismissal {
  @Id
  @Column(name = "account_id")
  private Long accountId;

  @Id
  @Column(name = "announcement_id")
  private Long announcementId;

  @Column(name = "dismissed_at", nullable = false)
  private LocalDateTime dismissedAt;

  /** 새 숨김 기록을 만든다. */
  public AnnouncementDismissal(Long accountId, Long announcementId, LocalDateTime dismissedAt) {
    this.accountId = accountId;
    this.announcementId = announcementId;
    this.dismissedAt = dismissedAt;
  }
}
