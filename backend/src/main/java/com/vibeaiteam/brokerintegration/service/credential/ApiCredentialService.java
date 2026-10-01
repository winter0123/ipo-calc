package com.vibeaiteam.brokerintegration.service.credential;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.brokerintegration.controller.credential.dto.ApiCredentialResponse;
import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialConflictException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialForbiddenException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialNotFoundException;
import com.vibeaiteam.brokerintegration.exception.ApiCredentialValidationException;
import com.vibeaiteam.brokerintegration.exception.BrokerConnectionUnavailableException;
import com.vibeaiteam.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.vibeaiteam.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  /** 한국투자증권 서비스 사용기간은 신청일부터 1년이다(갱신하면 키가 새로 발급된다). */
  private static final Period KEY_VALIDITY = Period.ofYears(1);

  private static final Logger log = LoggerFactory.getLogger(ApiCredentialService.class);

  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final OwnershipGuard ownershipGuard;
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
  public ApiCredentialResponse register(
      Long securitiesAccountId, String apiProvider, String appKey, String appSecret) {
    return register(securitiesAccountId, apiProvider, appKey, appSecret, null);
  }

  /**
   * 증권계좌에 브로커 API 인증정보를 등록한다(키 발급일 포함).
   *
   * <p>증권계좌의 증권사가 API 연동 대상이고 그 어댑터 식별자와 {@code apiProvider}가 같을 때만 등록한다. 발급일이 없으면 오늘로
   * 보고, 미래 날짜는 거부한다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @param apiProvider 브로커 어댑터 식별자 (예: KIS)
   * @param appKey 브로커 발급 앱키 (평문, 저장 시 암호화)
   * @param appSecret 브로커 발급 앱시크릿 (평문, 저장 시 암호화)
   * @param keyIssuedOn 키 발급일(선택)
   * @return 등록 결과 (연결 상태·마스킹된 계좌번호만 포함)
   */
  @Transactional
  public ApiCredentialResponse register(
      Long securitiesAccountId,
      String apiProvider,
      String appKey,
      String appSecret,
      LocalDate keyIssuedOn) {
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
    String supported = supportedProvider(account);
    if (supported == null || !supported.equals(normalizedProvider)) {
      throw new ApiCredentialValidationException("이 증권사는 아직 API 연동을 지원하지 않습니다.");
    }
    LocalDate issuedOn = resolveIssuedOn(keyIssuedOn);
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
            .keyIssuedOn(issuedOn)
            .status(RecordStatus.ACTIVE)
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

    log.info(
        "브로커 API 인증정보 등록: securitiesAccountId={}, provider={}",
        securitiesAccountId,
        normalizedProvider);
    return toResponse(account, saved);
  }

  /**
   * 연결된 증권사 API 키를 새 키로 교체한다.
   *
   * <p>증권사에서 서비스를 갱신하면 앱키·앱시크릿이 새로 발급되므로 이 경로로 바꾼다. 캐시된 접근토큰은 버리고 다음 조회 때 새 키로
   * 다시 발급한다. 연결이 없으면 404다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @param appKey 새 앱키 (평문, 저장 시 암호화)
   * @param appSecret 새 앱시크릿 (평문, 저장 시 암호화)
   * @param keyIssuedOn 새 키의 발급일(선택, 없으면 오늘)
   * @return 교체 결과 (연결 상태·마스킹된 계좌번호만 포함)
   */
  @Transactional
  public ApiCredentialResponse replace(
      Long securitiesAccountId, String appKey, String appSecret, LocalDate keyIssuedOn) {
    if (securitiesAccountId == null) {
      throw new ApiCredentialValidationException("securitiesAccountId는 필수 입력값입니다.");
    }
    if (isBlank(appKey) || isBlank(appSecret)) {
      throw new ApiCredentialValidationException("appKey, appSecret은 필수 입력값입니다.");
    }
    final SecuritiesAccount account = requireOwnedSecuritiesAccount(securitiesAccountId);
    if (!brokerSecretEncryptor.isConfigured()) {
      throw new BrokerConnectionUnavailableException(
          "브로커 연결 미활성: APP_ENCRYPTION_BROKER_KEY(또는 app.encryption.broker-key)가 "
              + "설정되지 않아 인증정보를 교체할 수 없습니다.");
    }
    SecuritiesAccountApiCredential credential =
        credentialRepository
            .findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(securitiesAccountId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiCredentialNotFoundException("연결된 브로커 API 인증정보가 없습니다."));
    credential.setAppKeyEncrypted(brokerSecretEncryptor.encrypt(appKey.trim()));
    credential.setAppSecretEncrypted(brokerSecretEncryptor.encrypt(appSecret.trim()));
    credential.setAccessTokenEncrypted(null);
    credential.setTokenExpiresAt(null);
    credential.setKeyIssuedOn(resolveIssuedOn(keyIssuedOn));
    credential.setUpdatedAt(LocalDateTime.now(clock));
    SecuritiesAccountApiCredential saved = credentialRepository.save(credential);
    log.info("브로커 API 인증정보 교체: securitiesAccountId={}", securitiesAccountId);
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
   * <p>저장된 앱키·앱시크릿·토큰 암호문이 남지 않도록 행을 물리 삭제한다(이 테이블만의 예외). 이미 연결 해제된 계좌는 204로 끝낸다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   */
  @Transactional
  public void disconnect(Long securitiesAccountId) {
    if (securitiesAccountId == null) {
      throw new ApiCredentialValidationException("securitiesAccountId는 필수 입력값입니다.");
    }

    requireOwnedSecuritiesAccount(securitiesAccountId);

    // 비밀키 암호문을 DB에 남기지 않으려고 이 테이블만 예외적으로 물리 삭제한다(D-B-015).
    credentialRepository.deleteBySecuritiesAccountId(securitiesAccountId);
    log.info("브로커 API 인증정보 삭제: securitiesAccountId={}", securitiesAccountId);
  }

  /**
   * 현재 계정이 소유한 증권계좌를 돌려준다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 증권계좌(없으면 404, 타 계정 소유면 403 예외)
   */
  public SecuritiesAccount requireOwnedSecuritiesAccount(Long securitiesAccountId) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(securitiesAccountId)
            .orElseThrow(() -> new ApiCredentialNotFoundException("증권계좌를 찾을 수 없습니다."));

    ownershipGuard.requireOwnedInvestor(
        account.getInvestorId(),
        () -> new ApiCredentialNotFoundException("증권계좌를 찾을 수 없습니다."),
        () -> new ApiCredentialForbiddenException("계정 소유권이 없습니다."));
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
    if (credential == null || credential.getKeyIssuedOn() == null) {
      return null;
    }
    return credential.getKeyIssuedOn().plus(KEY_VALIDITY);
  }

  /** 증권계좌의 증권사가 API 연동 대상이면 그 어댑터 식별자를, 아니면 null을 돌려준다. */
  private String supportedProvider(SecuritiesAccount account) {
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

  private LocalDate resolveIssuedOn(LocalDate keyIssuedOn) {
    LocalDate today = LocalDate.now(clock);
    if (keyIssuedOn == null) {
      return today;
    }
    if (keyIssuedOn.isAfter(today)) {
      throw new ApiCredentialValidationException("키 발급일은 오늘 이후일 수 없습니다.");
    }
    return keyIssuedOn;
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
