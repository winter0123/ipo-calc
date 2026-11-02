package com.ipocalc.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자용 회원 상세 조회 응답 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {

  /** 계정 ID. */
  private Long id;

  /** 이메일. */
  private String email;

  /** 이름. */
  private String name;

  /** 닉네임. */
  private String nickname;

  /** 권한. */
  private String role;

  /** 승인 여부. */
  private boolean approved;

  /** 잠금 여부. */
  private boolean locked;

  /** 잠금 만료 시각. */
  private LocalDateTime lockedUntil;

  /** 마지막 로그인 시각. */
  private LocalDateTime lastLoginAt;

  /** 가입 시각. */
  private LocalDateTime createdAt;

  /** 투자자 요약 목록. */
  private List<InvestorSummaryResponse> investors;

  /** 회원 통계. */
  private UserStats stats;

  /** 요약된 투자자 정보. */
  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InvestorSummaryResponse {
    /** 투자자 ID. */
    private Long id;

    /** 표시 이름. */
    private String displayName;

    /** 회원과의 관계. */
    private String relationship;

    /** 상태. */
    private String status;
  }

  /** 회원별 집계 통계. */
  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserStats {
    /** 청약 수. */
    private int subscriptionCount;

    /** 배정 수. */
    private int allocationCount;

    /** 매도 수. */
    private int saleCount;

    /** 총 실현 손익. */
    private long totalProfit;
  }
}
