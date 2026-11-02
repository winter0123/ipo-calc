package com.ipocalc.accountauthentication.controller.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 임퍼소네이션 상태 조회 응답 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationStatusResponse {

  /** 임퍼소네이션 진행 중 여부. */
  private boolean isImpersonating;

  /** 대상 회원 계정 ID (임퍼소네이션 중일 때만 존재). */
  private Long targetAccountId;

  /** 만료 시각 (임퍼소네이션 중일 때만 존재). */
  private LocalDateTime expiresAt;
}
