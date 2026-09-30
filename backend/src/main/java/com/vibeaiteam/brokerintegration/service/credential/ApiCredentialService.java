package com.vibeaiteam.brokerintegration.service.credential;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.brokerintegration.controller.credential.dto.ApiCredentialResponse;
import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialConflictException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialForbiddenException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialNotFoundException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialValidationException;
import com.vibeaiteam.brokerintegration.exception.BrokerConnectionUnavailableException;
import com.vibeaiteam.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.vibeaiteam.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권계좌별 브로커 API 인증정보 서비스.
 *
 * <p>앱키·앱시크릿은 BrokerSecretEncryptor(APP_ENCRYPTION_BROKER_KEY)로 암호화해 저장하며, 응답에는 연결 상태·마스킹된
 * 계좌번호·마지막 동기화 시각만 돌려준다. 접근은 SecuritiesAccount → Investor → Account로 이어지는 2단계 소유권 검증을
 * 거치며, 타 계정 소유 계좌는 403으로 거부한다(AC-1812).
 */
@Service
@RequiredArgsConstructor
public class ApiCredentialService {

  static final String CONNECTION_CONNECTED = "CONNECTED";
  static final String CONNECTION_EXPIRED = "EXPIRED";
  static final String CONNECTION_ERROR = "ERROR";
  static final String CONNECTION_DISCONNECTED = "DISCONNECTED";

  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_INACTIVE = "INACTIVE";

  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final InvestorRepository investorRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /**
   * 증권계좌에 브로커 API 인증정보를 등록한다.
   *
   * <p>시크릿은 암호화해 저장하고(AC-1810), 같은 계좌에 같은 provider의 활성 인증정보가 이미 있으면 409로 거부한다(AC-1813).
   * 브로커 암호화 키가 미설정이면 등록을 거부한다(AC-1814).
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @param apiProvider 브로커 어댑터 식별자 (예: KIS)
   * @param appKey 브로커 발급 앱키 (평문, 저장 시 암호화)
   * @param appSecret 브로커 발급 앱시크릿 (평문, 저장 시 암호화)
   * @return 등록 결과 (연결 상태·마스킹된 계좌번호만 포함)
   */
  @Transactional
  public ApiCredentialResponse register(
      Long securitiesAccountId, String apiProvider, String appKey, String appSecret) {
    if (securitiesAccountId == null) {
      throw new ApiCredentialValidationException("securitiesAccountId는 필수 입력값입니다.");
    }
    if (isBlank(apiProvider) || isBlank(appKey) || isBlank(appSecret)) {
      throw new ApiCredentialValidationException("apiProvider, appKey, appSecret은 필수 입력값입니다.");
    }

    final SecuritiesAccount account = requireOwnedSecuritiesAccount(securitiesAccountId);

    // AC-1814: 키가 미설정이면 암호화 자체가 불가능하므로 기동은 유지하되 연결 등록만 거부한다.
    if (!brokerSecretEncryptor.isConfigured()) {
      throw new BrokerConnectionUnavailableException(
          "브로커 연결 미활성: APP_ENCRYPTION_BROKER_KEY(또는 app.encryption.broker-key)가 "
              + "설정되지 않아 인증정보를 등록할 수 없습니다.");
    }

    String normalizedProvider = apiProvider.trim();
    if (credentialRepository.existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
        securitiesAccountId, normalizedProvider)) {
      throw new ApiCredentialConflictException("이미 등록된 브로커 API 인증정보가 있습니다.");
    }

    LocalDateTime now = LocalDateTime.now(clock);
    SecuritiesAccountApiCredential credential =
        SecuritiesAccountApiCredential.builder()
            .securitiesAccountId(securitiesAccountId)
            .apiProvider(normalizedProvider)
            .appKeyEncrypted(brokerSecretEncryptor.encrypt(appKey))
            .appSecretEncrypted(brokerSecretEncryptor.encrypt(appSecret))
            .accessTokenEncrypted(null)
            .tokenExpiresAt(null)
            .status(STATUS_ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    SecuritiesAccountApiCredential saved;
    try {
      saved = credentialRepository.saveAndFlush(credential);
    } catch (DataIntegrityViolationException e) {
      // 부분 유니크 인덱스(계좌 + provider ACTIVE)가 동시 요청을 막는다. 방어적으로 409로 변환한다.
      throw new ApiCredentialConflictException("이미 등록된 브로커 API 인증정보가 있습니다.");
    }

    return toResponse(account, saved);
  }

  /**
   * 증권계좌의 브로커 API 인증정보 연결 상태를 조회한다.
   *
   * <p>연결 정보가 없으면 DISCONNECTED를 반환하며, 같은 계좌에 복수 provider가 있으면 최근 갱신 순 1건을 기준으로 상태를
   * 돌려준다. 응답에는 시크릿·토큰이 포함되지 않는다(AC-1811).
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 연결 상태·마스킹된 계좌번호·마지막 동기화 시각
   */
  @Transactional(readOnly = true)
  public ApiCredentialResponse getStatus(Long securitiesAccountId) {
    if (securitiesAccountId == null) {
      throw new ApiCredentialValidationException("securitiesAccountId는 필수 입력값입니다.");
    }

    SecuritiesAccount account = requireOwnedSecuritiesAccount(securitiesAccountId);

    SecuritiesAccountApiCredential credential =
        credentialRepository
            .findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(securitiesAccountId)
            .stream()
            .findFirst()
            .orElse(null);

    return toResponse(account, credential);
  }

  /**
   * 증권계좌의 브로커 API 인증정보를 연결 해제(소프트 삭제)한다.
   *
   * <p>물리 삭제하지 않고 status를 INACTIVE로, deletedAt을 설정해 이력은 남긴다. 이미 연결 해제된 계좌는 204로 끝낸다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   */
  @Transactional
  public void disconnect(Long securitiesAccountId) {
    if (securitiesAccountId == null) {
      throw new ApiCredentialValidationException("securitiesAccountId는 필수 입력값입니다.");
    }

    requireOwnedSecuritiesAccount(securitiesAccountId);

    List<SecuritiesAccountApiCredential> activeCredentials =
        credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNull(securitiesAccountId);
    if (activeCredentials.isEmpty()) {
      return;
    }

    LocalDateTime now = LocalDateTime.now(clock);
    for (SecuritiesAccountApiCredential credential : activeCredentials) {
      credential.setStatus(STATUS_INACTIVE);
      credential.setDeletedAt(now);
      credential.setUpdatedAt(now);
    }
    credentialRepository.saveAll(activeCredentials);
  }

  private SecuritiesAccount requireOwnedSecuritiesAccount(Long securitiesAccountId) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(securitiesAccountId)
            .orElseThrow(() -> new ApiCredentialNotFoundException("증권계좌를 찾을 수 없습니다."));

    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new ApiCredentialNotFoundException("증권계좌를 찾을 수 없습니다."));

    Long currentAccountId = securityUtils.getCurrentAccountId();
    if (!currentAccountId.equals(investor.getAccountId())) {
      throw new ApiCredentialForbiddenException("계정 소유권이 없습니다.");
    }
    return account;
  }

  private ApiCredentialResponse toResponse(
      SecuritiesAccount account, SecuritiesAccountApiCredential credential) {
    String maskedAccountNumber =
        AccountNumberMasker.mask(accountNumberEncryptor.decrypt(account.getAccountNumber()));
    return new ApiCredentialResponse(
        credential == null ? null : credential.getId(),
        account.getId(),
        credential == null ? null : credential.getApiProvider(),
        resolveConnectionStatus(credential),
        maskedAccountNumber,
        lastSyncedAt(credential));
  }

  private String resolveConnectionStatus(SecuritiesAccountApiCredential credential) {
    if (credential == null) {
      return CONNECTION_DISCONNECTED;
    }
    // 브로커 키가 미설정이면 저장된 시크릿을 쓸 수 없으므로 연결 오류 상태로 표시한다.
    if (!brokerSecretEncryptor.isConfigured()) {
      return CONNECTION_ERROR;
    }
    if (credential.getTokenExpiresAt() != null
        && credential.getTokenExpiresAt().isBefore(LocalDateTime.now(clock))) {
      return CONNECTION_EXPIRED;
    }
    return CONNECTION_CONNECTED;
  }

  private LocalDateTime lastSyncedAt(SecuritiesAccountApiCredential credential) {
    if (credential == null || credential.getAccessTokenEncrypted() == null) {
      return null;
    }
    return credential.getUpdatedAt();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
