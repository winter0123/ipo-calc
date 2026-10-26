package com.ipocalc.announcements.repository;

import com.ipocalc.announcements.domain.AnnouncementDismissal;
import com.ipocalc.announcements.domain.AnnouncementDismissalId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 계정별 공지 숨김 데이터 저장소. */
public interface AnnouncementDismissalRepository
    extends JpaRepository<AnnouncementDismissal, AnnouncementDismissalId>,
        AnnouncementDismissalMutationRepository {
  /** 계정별 영구 숨김 공지를 조회한다. */
  List<AnnouncementDismissal> findAllByAccountId(Long accountId);
}
