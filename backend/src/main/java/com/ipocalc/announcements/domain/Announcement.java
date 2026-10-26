package com.ipocalc.announcements.domain;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** 공지사항 엔티티. */
@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {
  /** Creates an announcement and initializes its audit timestamps. */
  public static Announcement create(
      String title,
      String content,
      LocalDate startAt,
      LocalDate endAt,
      RecordStatus status,
      LocalDateTime now) {
    Announcement a = new Announcement();
    a.edit(title, content, startAt, endAt, status, now);
    a.createdAt = now;
    return a;
  }

  /** Updates announcement content and publication period. */
  public void edit(
      String title,
      String content,
      LocalDate startAt,
      LocalDate endAt,
      RecordStatus status,
      LocalDateTime now) {
    this.title = title;
    this.content = content;
    this.startAt = startAt;
    this.endAt = endAt;
    if (status != null) {
      this.status = status;
    }
    this.updatedAt = now;
  }

  /** Cancels the record using a soft delete. */
  public void cancel(LocalDateTime now) {
    this.status = RecordStatus.CANCELLED;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Comment("공지사항 고유 식별자")
  private Long id;

  @Column(nullable = false, length = 200)
  @Comment("공지사항 제목")
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  @Comment("공지사항 내용")
  private String content;

  @Column(name = "start_at", nullable = false)
  @Comment("게시 시작일")
  private LocalDate startAt;

  @Column(name = "end_at", nullable = false)
  @Comment("게시 종료일")
  private LocalDate endAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  @Comment("공지사항 상태(ACTIVE/CANCELLED)")
  private RecordStatus status = RecordStatus.ACTIVE;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Comment("생성 시각")
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @Comment("마지막 수정 시각")
  private LocalDateTime updatedAt;

  @Column(name = "deleted_at")
  @Comment("소프트 삭제 시각")
  private LocalDateTime deletedAt;
}
