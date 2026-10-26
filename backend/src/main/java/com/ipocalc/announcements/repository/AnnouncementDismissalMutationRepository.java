package com.ipocalc.announcements.repository;

import java.time.LocalDateTime;

/** 공지 숨김의 멱등 저장을 담당한다. */
public interface AnnouncementDismissalMutationRepository {
  /** 공지 숨김 기록이 없을 때 삽입한다. */
  int insertIfAbsent(Long accountId, Long announcementId, LocalDateTime dismissedAt);
}
