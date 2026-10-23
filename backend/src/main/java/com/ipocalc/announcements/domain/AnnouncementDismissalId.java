package com.ipocalc.announcements.domain;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 계정별 공지 숨김 복합 키. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AnnouncementDismissalId implements Serializable {
  private Long accountId;
  private Long announcementId;
}
