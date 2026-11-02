package com.ipocalc.admin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자용 회원 목록 조회 응답 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListResponse {

  /** 계정 ID. */
  private Long id;

  /** 이메일. */
  private String email;

  /** 이름. */
  private String name;

  /** 닉네임. */
  private String nickname;

  /** 역할 (USER, ADMIN). */
  private String role;

  /** 승인 여부. */
  private boolean approved;

  /** 잠금 여부. */
  private boolean locked;

  /** 잠금 해제 시각 (잠긴 경우). */
  private LocalDateTime lockedUntil;

  /** 마지막 로그인 시각. */
  private LocalDateTime lastLoginAt;

  /** 가입일. */
  private LocalDateTime createdAt;

  /** 투자자 수. */
  private int investorCount;

  /** 청약 건수. */
  private int subscriptionCount;
}
