package com.ipocalc.brokerintegration.service.credential;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.brokerintegration.controller.credential.dto.ApiCredentialResponse;
import com.ipocalc.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.ipocalc.brokerintegration.exception.ApiCredentialConflictException;
import com.ipocalc.brokerintegration.exception.ApiCredentialForbiddenException;
import com.ipocalc.brokerintegration.exception.ApiCredentialNotFoundException;
import com.ipocalc.brokerintegration.exception.ApiCredentialValidationException;
import com.ipocalc.brokerintegration.exception.BrokerConnectionUnavailableException;
import com.ipocalc.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.ipocalc.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.ipocalc.brokerintegration.service.ratelimit.SlidingWindowRateLimiter;
import com.ipocalc.investormanagement.service.ownership.OwnershipGuard;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권계좌별 브로커 API 인증정보 서비스.
 *
 * <p>앱키·앱시크릿은 BrokerSecretEncryptor(APP_ENCRYPTION_BROKER_KEY)로 암호화해 저장하며, 응답에는 연결 상태·마스킹된 계좌번호·마지막
 * 동기화 시각만 돌려준다. 접근은 SecuritiesAccount → Investor → Account로 이어지는 2단계 소유권 검증을 거치며, 타 계정 소유 계좌는 403으로
 * 거부한다(AC-1812).
 */
@Service
@RequiredArgsConstructor
public class ApiCredentialService {

  /** 키 등록·교체는 계정당 10분에 5회까지만 허용한다(탈취된 세션의 연속 시도 방지). */
  private static final SlidingWindowRateLimiter.Limit KEY_WRITE_LIMIT =
      new SlidingWindowRateLimiter.Limit(5, Duration.ofMinutes(10));

  private static final Logger log = LoggerFactory.getLogger(ApiCredentialService.class);

  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final OwnershipGuard ownershipGuard;
  private final ApiCredentialAssembler assembler;
  private final Clock clock;
  private final SecurityUtils securityUtils;
  private final SlidingWindowRateLimiter rateLimiter;

  /**
   * 증권계좌에 브로커 API 인증정보를 등록한다.
   *
   * <p>시크릿은 암호화해 저장하고(AC-1810), 같은 계좌에 같은 provider의 활성 인증정보가 이미 있으면 409로 거부한다(AC-1813). 브로커 암호화 키가
   * 미설정이면 등록을 거부한다(AC-1814).
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
   * <p>증권계좌의 증권사가 API 연동 대상이고 그 어댑터 식별자와 {@code apiProvider}가 같을 때만 등록한다. 발급일이 없으면 오늘로 보고, 미래 날짜는
   * 거부한다.
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
    requireId(securitiesAccountId);
    if (isBlank(apiProvider) || isBlank(appKey) || isBlank(appSecret)) {
      throw new ApiCredentialValidationException("apiProvider, appKey, appSecret은 필수 입력값입니다.");
    }
    rateLimiter.acquire(keyWriteBucket(), KEY_WRITE_LIMIT);

    final SecuritiesAccount account = requireOwnedSecuritiesAccount(securitiesAccountId);

    // AC-1814: 키가 미설정이면 암호화 자체가 불가능하므로 기동은 유지하되 연결 등록만 거부한다.
    if (!brokerSecretEncryptor.isConfigured()) {
      throw new BrokerConnectionUnavailableException(
          "브로커 연결 미활성: APP_ENCRYPTION_BROKER_KEY(또는 app.encryption.broker-key)가 "
              + "설정되지 않아 인증정보를 등록할 수 없습니다.");
    }

    String normalizedProvider = apiProvider.trim();
    String supported = assembler.supportedProvider(account);
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
            .appKeyEncrypted(brokerSecretEncryptor.encrypt(appKey.trim()))
            .appSecretEncrypted(brokerSecretEncryptor.encrypt(appSecret.trim()))
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
    return assembler.toResponse(account, saved);
  }

  /**
   * 연결된 증권사 API 키를 새 키로 교체한다.
   *
   * <p>증권사에서 서비스를 갱신하면 앱키·앱시크릿이 새로 발급되므로 이 경로로 바꾼다. 캐시된 접근토큰은 버리고 다음 조회 때 새 키로 다시 발급한다. 연결이 없으면
   * 404다.
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
    requireId(securitiesAccountId);
    if (isBlank(appKey) || isBlank(appSecret)) {
      throw new ApiCredentialValidationException("appKey, appSecret은 필수 입력값입니다.");
    }
    rateLimiter.acquire(keyWriteBucket(), KEY_WRITE_LIMIT);
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
    return assembler.toResponse(account, saved);
  }

  /**
   * 증권계좌의 브로커 API 인증정보 연결 상태를 조회한다.
   *
   * <p>연결 정보가 없으면 DISCONNECTED를 반환하며, 같은 계좌에 복수 provider가 있으면 최근 갱신 순 1건을 기준으로 상태를 돌려준다. 응답에는
   * 시크릿·토큰이 포함되지 않는다(AC-1811).
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   * @return 연결 상태·마스킹된 계좌번호·마지막 동기화 시각
   */
  @Transactional(readOnly = true)
  public ApiCredentialResponse getStatus(Long securitiesAccountId) {
    requireId(securitiesAccountId);

    SecuritiesAccount account = requireOwnedSecuritiesAccount(securitiesAccountId);

    SecuritiesAccountApiCredential credential =
        credentialRepository
            .findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(securitiesAccountId)
            .stream()
            .findFirst()
            .orElse(null);

    return assembler.toResponse(account, credential);
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
    requireId(securitiesAccountId);

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

  private String keyWriteBucket() {
    return "credential-write:" + securityUtils.getCurrentAccountId();
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

  private static void requireId(Long securitiesAccountId) {
    if (securitiesAccountId == null) {
      throw new ApiCredentialValidationException("securitiesAccountId는 필수 입력값입니다.");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
