package com.ipocalc.brokerintegration.service.credential;

import com.ipocalc.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.ipocalc.securitiesaccountmanagement.service.securitiesaccount.AccountCredentialDeletion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 브로커 자격정보 저장소를 증권계좌 도메인의 제거 포트에 연결한다. */
@Component
@RequiredArgsConstructor
public class AccountCredentialDeletionAdapter implements AccountCredentialDeletion {
  private final SecuritiesAccountApiCredentialRepository credentialRepository;

  @Override
  public void deleteBySecuritiesAccountId(Long securitiesAccountId) {
    credentialRepository.deleteBySecuritiesAccountId(securitiesAccountId);
  }
}
