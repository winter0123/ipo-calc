package com.ipocalc.accountauthentication.repository.account;

import java.time.LocalDateTime;

/** 계정 로그인 상태의 원자적 갱신 저장소. */
public interface AccountMutationRepository {
  /** 로그인 실패 횟수와 잠금 상태를 원자적으로 기록한다. */
  int recordFailedLogin(Long id, LocalDateTime now, int maxAttempts, LocalDateTime lockUntil);

  /** 로그인 성공 시 계정의 실패 횟수와 마지막 로그인 시각을 갱신한다. */
  int recordLoginSuccess(Long id, LocalDateTime now);
}
