package com.ipocalc.brokerintegration.repository.credential;

import java.time.LocalDateTime;

/** 브로커 토큰의 조건부 갱신 저장소. */
public interface CredentialTokenMutationRepository {
  /** 증권계좌의 인증정보를 삭제한다. */
  void deleteBySecuritiesAccountId(Long securitiesAccountId);

  /** 앱 키가 변경되지 않은 경우에만 토큰을 갱신한다. */
  @SuppressWarnings("checkstyle:LineLength")
  int updateTokenIfKeyUnchanged(
      Long id,
      String appKeyEncrypted,
      String tokenEncrypted,
      LocalDateTime expiresAt,
      LocalDateTime now);
}
