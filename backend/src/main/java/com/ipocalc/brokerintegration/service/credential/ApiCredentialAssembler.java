package com.ipocalc.brokerintegration.service.credential;

import com.ipocalc.brokerintegration.adapter.kis.KisApiProperties;
import com.ipocalc.brokerintegration.controller.credential.dto.ApiCredentialResponse;
import com.ipocalc.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.ipocalc.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 증권계좌 인증정보를 연결 상태 응답으로 조립한다.
 *
 * <p>연결 상태(연결됨·만료·오류·해제)와 키 사용기간 만료일, 지원 증권사 식별자, 마스킹된 계좌번호를 계산한다. 응답에는 시크릿·토큰이 들어가지 않는다(AC-1811).
 */
@Component
@RequiredArgsConstructor
public class ApiCredentialAssembler {

  static final String CONNECTION_CONNECTED = "CONNECTED";
  static final String CONNECTION_EXPIRED = "EXPIRED";
  static final String CONNECTION_ERROR = "ERROR";
  static final String CONNECTION_DISCONNECTED = "DISCONNECTED";

  /** 한국투자증권 서비스 사용기간은 신청일부터 1년이다(갱신하면 키가 새로 발급된다). */
  private static final Period KEY_VALIDITY = Period.ofYears(1);

  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final Clock clock;

  /**
   * 증권계좌와 인증정보로 응답을 만든다. 시크릿·토큰은 담지 않고 마스킹된 계좌번호만 담는다.
   *
   * @param account 대상 증권계좌
   * @param credential 인증정보(연결이 없으면 null)
   * @return 연결 상태 응답
   */
  public ApiCredentialResponse toResponse(
      SecuritiesAccount account, SecuritiesAccountApiCredential credential) {
    String maskedAccountNumber =
        AccountNumberMasker.mask(accountNumberEncryptor.decrypt(account.getAccountNumber()));
    return new ApiCredentialResponse(
        credential == null ? null : credential.getId(),
        account.getId(),
        credential == null ? null : credential.getApiProvider(),
        resolveConnectionStatus(credential),
        maskedAccountNumber,
        lastSyncedAt(credential),
        supportedProvider(account),
        credential == null ? null : credential.getKeyIssuedOn(),
        keyExpiresOn(credential));
  }

  private String resolveConnectionStatus(SecuritiesAccountApiCredential credential) {
    if (credential == null) {
      return CONNECTION_DISCONNECTED;
    }
    // 브로커 키가 미설정이면 저장된 시크릿을 쓸 수 없으므로 연결 오류 상태로 표시한다.
    if (!brokerSecretEncryptor.isConfigured()) {
      return CONNECTION_ERROR;
    }
    // 접근토큰(1일)은 서버가 자동으로 다시 발급하므로 상태에 반영하지 않는다.
    // 사용자가 조치해야 하는 만료는 증권사 서비스 사용기간이다.
    LocalDate expiresOn = keyExpiresOn(credential);
    if (expiresOn != null && expiresOn.isBefore(LocalDate.now(clock))) {
      return CONNECTION_EXPIRED;
    }
    return CONNECTION_CONNECTED;
  }

  private LocalDate keyExpiresOn(SecuritiesAccountApiCredential credential) {
    // 키움·LS는 사용기간 규칙을 확인하지 못했으므로 만료일을 계산하지 않는다.
    if (credential == null
        || credential.getKeyIssuedOn() == null
        || !KisApiProperties.PROVIDER.equals(credential.getApiProvider())) {
      return null;
    }
    return credential.getKeyIssuedOn().plus(KEY_VALIDITY);
  }

  /** 증권계좌의 증권사가 API 연동 대상이면 그 어댑터 식별자를, 아니면 null을 돌려준다. */
  public String supportedProvider(SecuritiesAccount account) {
    if (account.getSecuritiesFirmId() == null) {
      return null;
    }
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(account.getSecuritiesFirmId())
            .orElse(null);
    if (firm == null || !Boolean.TRUE.equals(firm.getApiSupported())) {
      return null;
    }
    return firm.getApiProvider();
  }

  private LocalDateTime lastSyncedAt(SecuritiesAccountApiCredential credential) {
    if (credential == null || credential.getAccessTokenEncrypted() == null) {
      return null;
    }
    return credential.getUpdatedAt();
  }
}
