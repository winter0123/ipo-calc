package com.ipocalc.announcements.controller;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.announcements.domain.Announcement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 공지사항 응답. */
public record AnnouncementResponse(
    Long id,
    String title,
    String content,
    LocalDate startAt,
    LocalDate endAt,
    RecordStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  /** 엔티티를 응답으로 변환한다. */
  public static AnnouncementResponse from(Announcement announcement) {
    return new AnnouncementResponse(
        announcement.getId(),
        announcement.getTitle(),
        announcement.getContent(),
        announcement.getStartAt(),
        announcement.getEndAt(),
        announcement.getStatus(),
        announcement.getCreatedAt(),
        announcement.getUpdatedAt());
  }
}
