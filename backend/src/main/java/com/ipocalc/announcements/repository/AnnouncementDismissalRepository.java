package com.ipocalc.announcements.repository;

import com.ipocalc.announcements.domain.AnnouncementDismissal;
import com.ipocalc.announcements.domain.AnnouncementDismissalId;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 계정별 공지 숨김 데이터 저장소. */
public interface AnnouncementDismissalRepository
    extends JpaRepository<AnnouncementDismissal, AnnouncementDismissalId> {
  /** 계정별 영구 숨김 공지를 조회한다. */
  List<AnnouncementDismissal> findAllByAccountId(Long accountId);

  /** 이미 저장된 숨김 요청은 그대로 두고 새 계정·공지 조합만 추가한다. */
  @Modifying
  @Query(
      value =
          "INSERT INTO announcement_dismissals (account_id, announcement_id, dismissed_at) "
              + "VALUES (:accountId, :announcementId, :dismissedAt) "
              + "ON CONFLICT (account_id, announcement_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("accountId") Long accountId,
      @Param("announcementId") Long announcementId,
      @Param("dismissedAt") LocalDateTime dismissedAt);
}
