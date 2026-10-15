package com.ipocalc.brokerintegration.service.token;

import com.ipocalc.brokerintegration.adapter.BrokerTokenIssuer;
import com.ipocalc.brokerintegration.adapter.kis.KisAccessToken;
import com.ipocalc.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.ipocalc.brokerintegration.exception.BrokerApiException;
import com.ipocalc.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.ipocalc.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 브로커(KIS·키움·LS) 접근토큰 캐시 서비스. 증권사별 발급은 {@link BrokerTokenIssuer}가 맡고 캐시·락·5분 마진은 모두 공유한다.
 *
 * <p>KIS 접근토큰은 유효기간이 1일이고 재발급이 분당 1회로 제한된다. 그래서 발급한 토큰을 {@link BrokerSecretEncryptor}로 암호화해 인증정보
 * 엔티티의 {@code accessTokenEncrypted}에 두고, {@code tokenExpiresAt}과 함께 재사용한다. 캐시가 없거나 만료 임박이면
 * 앱키·앱시크릿으로 1회만 재발급한다.
 *
 * <p>설계 판단 3가지.
 *
 * <p>1. 재사용 판정: 만료 시각이 "지금 + 5분" 이후일 때만 캐시를 쓴다. 만료 직후에 캐시를 낙가시키면 그 순간의 모든 조회 요청이 실패하므로 5분 여유를 둔다.
 *
 * <p>2. 동시 직렬화: 인증정보 ID 단위 락을 잡고, 락 안에서 캐시 재검사 → 발급 → 저장을 한 번에 수행한다. 락 밖에서 캐시를 먼저 읽으면 동시 요청 N개가 모두
 * "캐시 없음"을 보고 N번 발급하게 되므로 재검사는 반드시 락 안에서 한다.
 *
 * <p>3. 발급 실패: 재시도 차단 상태를 남기지 않고 {@link BrokerApiException}을 그대로 전파한다. 60초 차단도 검토했지만 채택하지 않았다. 분당
 * 1회 제한과 같은 60초 단위라 보장이 되지 않고, 재시도 시점이 다른 시점으로 밀리는 이점도 없어 실제 보호 효과가 없다. 차라리 상태를 남기지 않아 다음 호출이 원래
 * 제한에 따라 다시 시도하도록 두는 편이 단순하고 복구에도 유리하다.
 *
 * <p>트랜잭션: 발급은 네트워크 호출을 포함하므로 이 서비스에는 {@code @Transactional}을 걸지 않는다. 저장은 {@code repository.save}의
 * 기본 트랜잭션에 맡겨 토큰이 즉시 커밋되게 한다(캐시이므로 롤백되면 재발급 횟수만 늘어난다).
 *
 * <p>ponytail: 다중 인스턴스라면 JVM 내 락으로는 동시 발급을 막을 수 없으므로 DB 락(비관적 쓰기 락)이나 배포 단위를 계좌 1개로 제한해야 한다.
 */
@Service
public class BrokerTokenService {

  /** 만료 임박 시 여유를 두고 미리 재발급하기 위한 안전 마진. */
  private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

  private static final Logger log = LoggerFactory.getLogger(BrokerTokenService.class);

  private final Map<String, BrokerTokenIssuer> issuers;
  private final BrokerSecretEncryptor secretEncryptor;
  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final Clock clock;

  /** 인증정보 ID별 발급 락. 인증정보 수만큼만 늘어나므로 제거하지 않는다. */
  private final Map<Long, ReentrantLock> issuanceLocks = new ConcurrentHashMap<>();

  /**
   * 생성자.
   *
   * @param issuers 증권사별 접근토큰 발급 클라이언트
   * @param secretEncryptor 시크릿 암호화 유틸리티
   * @param credentialRepository 인증정보 리포지터리
   * @param clock 시각 기준
   */
  public BrokerTokenService(
      List<BrokerTokenIssuer> issuers,
      BrokerSecretEncryptor secretEncryptor,
      SecuritiesAccountApiCredentialRepository credentialRepository,
      Clock clock) {
    this.issuers =
        issuers.stream()
            .collect(Collectors.toMap(BrokerTokenIssuer::provider, Function.identity()));
    this.secretEncryptor = secretEncryptor;
    this.credentialRepository = credentialRepository;
    this.clock = clock;
  }

  /**
   * 인증정보에 대응하는 유효한 접근토큰을 반환한다(만료 임박이면 재발급).
   *
   * <p>토큰 평문은 로그·예외 메시지·{@code toString()} 어디에도 남지 않는다. 반환값은 {@link KisAccessToken}이고 그 {@code
   * toString()}은 값을 마스킹한다.
   *
   * @param credential 인증정보 엔티티(소유·삭제 여부 검증은 호출자가 한다)
   * @return 유효한 접근토큰과 만료 시각
   * @throws BrokerApiException 토큰 발급이 실패한 경우
   */
  public KisAccessToken getValidToken(SecuritiesAccountApiCredential credential) {
    if (credential == null || credential.getId() == null) {
      throw new IllegalArgumentException("인증정보와 인증정보 ID가 필요합니다.");
    }

    ReentrantLock lock =
        issuanceLocks.computeIfAbsent(credential.getId(), key -> new ReentrantLock());
    lock.lock();
    try {
      // 락 안에서 다시 검사한다. 대기 중 다른 요청이 이미 발급했을 수 있다.
      // 호출자가 미리 읽은 엔티티는 낡았을 수 있으므로 캐시는 DB에서 다시 읽어 판정한다.
      SecuritiesAccountApiCredential fresh =
          credentialRepository.findByIdAndDeletedAtIsNull(credential.getId()).orElse(credential);
      KisAccessToken cached = readCachedToken(fresh);
      if (cached != null) {
        log.debug(
            "브로커 접근토큰 캐시 재사용: credentialId={}, expiresAt={}",
            credential.getId(),
            cached.expiresAt());
        return cached;
      }
      return issueAndCache(credential);
    } finally {
      lock.unlock();
    }
  }

  /**
   * 인증정보 ID로 유효한 접근토큰을 반환한다(만료 임박이면 재발급).
   *
   * @param credentialId 인증정보 ID
   * @return 유효한 접근토큰과 만료 시각
   * @throws BrokerApiException 인증정보를 찾을 수 없거나 토큰 발급이 실패한 경우
   */
  public KisAccessToken getValidToken(Long credentialId) {
    if (credentialId == null) {
      throw new IllegalArgumentException("인증정보 ID가 필요합니다.");
    }
    return getValidToken(findActiveCredential(credentialId));
  }

  private SecuritiesAccountApiCredential findActiveCredential(Long credentialId) {
    return credentialRepository
        .findByIdAndDeletedAtIsNull(credentialId)
        .orElseThrow(
            () ->
                new BrokerApiException(
                    "credential_not_found",
                    "증권계좌 API 인증정보를 찾을 수 없습니다. credentialId=" + credentialId));
  }

  /**
   * 캐시된 토큰이 아직 유효하면 복호화해 반환하고, 아니면 null을 반환한다.
   *
   * @param credential 인증정보 엔티티(락을 잡은 상태로 호출된다)
   * @return 유효한 캐시 토큰, 재발급이 필요하면 null
   */
  private KisAccessToken readCachedToken(SecuritiesAccountApiCredential credential) {
    String encryptedToken = credential.getAccessTokenEncrypted();
    LocalDateTime expiresAt = credential.getTokenExpiresAt();
    if (encryptedToken == null || encryptedToken.isBlank() || expiresAt == null) {
      return null;
    }
    if (!expiresAt.isAfter(LocalDateTime.now(clock).plus(REFRESH_MARGIN))) {
      return null;
    }
    return new KisAccessToken(secretEncryptor.decrypt(encryptedToken), expiresAt);
  }

  /**
   * 앱키·앱시크릿으로 접근토큰을 1회 발급하고 암호화해 저장한다.
   *
   * <p>발급에 실패하면 엔티티를 건드리지 않고(부분 상태 금지) {@link BrokerApiException}을 그대로 전파한다.
   *
   * @param credential 인증정보 엔티티(락을 잡은 상태로 호출된다)
   * @return 발급된 접근토큰과 만료 시각
   * @throws BrokerApiException 토큰 발급이 실패한 경우
   */
  private KisAccessToken issueAndCache(SecuritiesAccountApiCredential credential) {
    String appKey = secretEncryptor.decrypt(credential.getAppKeyEncrypted());
    String appSecret = secretEncryptor.decrypt(credential.getAppSecretEncrypted());

    BrokerTokenIssuer issuer = issuers.get(credential.getApiProvider());
    if (issuer == null) {
      throw new BrokerApiException("unsupported_provider", "지원하지 않는 증권사 연동입니다.");
    }
    KisAccessToken issued;
    try {
      issued = issuer.issueToken(appKey, appSecret);
    } catch (BrokerApiException e) {
      // 실패 사실을 상태로 남기지 않는다(재시도 차단 없음). 예외 메시지에 시크릿이 없으므로 상태 코드만 남긴다.
      log.warn(
          "브로커 접근토큰 재발급 실패: credentialId={}, statusCode={}", credential.getId(), e.getStatusCode());
      throw e;
    }

    // 키가 그 사이 교체됐으면 0행이 갱신되어 옛 키의 토큰은 캐시되지 않는다(새 키를 덮어쓰지 않는다).
    int updated =
        credentialRepository.updateTokenIfKeyUnchanged(
            credential.getId(),
            credential.getAppKeyEncrypted(),
            secretEncryptor.encrypt(issued.token()),
            issued.expiresAt(),
            LocalDateTime.now(clock));
    log.info(
        "브로커 접근토큰 캐시 갱신: credentialId={}, expiresAt={}, cached={}",
        credential.getId(),
        issued.expiresAt(),
        updated > 0);
    return issued;
  }
}
