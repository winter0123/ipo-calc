package com.ipocalc.accountauthentication.controller.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 임퍼소네이션 시작 응답 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationStartResponse {

  /** 임퍼소네이션 세션 고유 ID (UUID). */
  private String sessionId;

  /** 만료 시각. */
  private LocalDateTime expiresAt;

  /** 읽기 전용 여부. */
  private boolean readOnly;
}
