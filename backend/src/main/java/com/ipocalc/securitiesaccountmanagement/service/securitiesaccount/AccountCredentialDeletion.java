package com.ipocalc.securitiesaccountmanagement.service.securitiesaccount;

/** 계좌 비활성화 시 연동 인증정보를 제거하는 저장소 경계. */
public interface AccountCredentialDeletion {
  /** 증권계좌에 연결된 인증정보를 삭제한다. */
  void deleteBySecuritiesAccountId(Long securitiesAccountId);
}
