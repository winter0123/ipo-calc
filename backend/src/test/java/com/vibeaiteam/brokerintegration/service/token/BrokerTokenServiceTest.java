package com.vibeaiteam.brokerintegration.service.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.brokerintegration.adapter.kis.KisAccessToken;
import com.vibeaiteam.brokerintegration.adapter.kis.KisAuthClient;
import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.vibeaiteam.brokerintegration.exception.BrokerApiException;
import com.vibeaiteam.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.vibeaiteam.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link BrokerTokenService} 테스트.
 *
 * <p>네트워크 호출 없이 {@link KisAuthClient}는 Mockito 목, 리포지터리는 "저장된 엔티티를 그대로 돌려주는" 목으로 검증한다. DB에도 네트워크에도
 * 닿지 않으므로 공유 로컬 DB에 남는 행이 없다. 시크릿은 전부 더미 값이다.
 */
class BrokerTokenServiceTest {

  private static final Long CREDENTIAL_ID = 4242L;

  /** 테스트 전용 더미 암호화 키(32바이트). 운영 키가 아니다. */
  private static final String DUMMY_ENCRYPTION_KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  private static final String DUMMY_APP_KEY = "dummy-app-key-1a2b";
  private static final String DUMMY_APP_SECRET = "dummy-app-secret-3c4d";
  private static final String CACHED_TOKEN = "dummy-cached-token-5e6f";
  private static final String ISSUED_TOKEN = "dummy-issued-token-7a8b";

  private BrokerSecretEncryptor secretEncryptor;
  private KisAuthClient authClient;
  private SecuritiesAccountApiCredentialRepository credentialRepository;
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
  private BrokerTokenService service;
  private SecuritiesAccountApiCredential credential;
  private LoggerContext loggerContext;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    secretEncryptor = new BrokerSecretEncryptor(DUMMY_ENCRYPTION_KEY);
    authClient = mock(KisAuthClient.class);
    when(authClient.provider()).thenReturn("KIS");
    credentialRepository = mock(SecuritiesAccountApiCredentialRepository.class);
    service =
        new BrokerTokenService(List.of(authClient), secretEncryptor, credentialRepository, clock);

    credential = buildCredential(null, null);

    // 저장소 목은 방금 만든 엔티티를 그대로 돌려준다(서비스가 엔티티를 갱신하면 다음 조회에 그대로 반영된다).
    when(credentialRepository.findByIdAndDeletedAtIsNull(CREDENTIAL_ID))
        .thenAnswer(invocation -> Optional.of(credential));
    when(credentialRepository.save(any(SecuritiesAccountApiCredential.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    listAppender = new ListAppender<>();
    listAppender.start();
    loggerContext.getLogger(BrokerTokenService.class).addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    loggerContext.getLogger(BrokerTokenService.class).detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void getValidToken_validCachedToken_reusesCacheWithoutIssuing() {
    // Given: 만료 시각이 5분 여유를 넘게 남아 있는 캐시 토큰
    credential =
        buildCredential(
            secretEncryptor.encrypt(CACHED_TOKEN), LocalDateTime.now(clock).plusHours(12));

    // When
    KisAccessToken token = service.getValidToken(credential);

    // Then: 복호화된 캐시 토큰을 돌려주고 발급·저장은 하지 않는다.
    assertThat(token.token()).isEqualTo(CACHED_TOKEN);
    assertThat(token.expiresAt()).isEqualTo(credential.getTokenExpiresAt());
    verify(authClient, never()).issueToken(anyString(), anyString());
    verify(credentialRepository, never()).save(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void getValidToken_expiringWithinRefreshMargin_issuesOnce() {
    // Given: 만료 시각이 5분 여유 안에 들어온 캐시 토큰(아직 만료 전이지만 재사용하면 안 된다)
    credential =
        buildCredential(
            secretEncryptor.encrypt(CACHED_TOKEN), LocalDateTime.now(clock).plusMinutes(2));
    stubSuccessfulIssuance();

    // When
    KisAccessToken token = service.getValidToken(credential);

    // Then: 1회만 재발급하고 새 토큰을 돌려준다.
    assertThat(token.token()).isEqualTo(ISSUED_TOKEN);
    verify(authClient).issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET);
  }

  @Test
  void getValidToken_noCachedToken_issuesOnceAndCachesResult() {
    // Given: 캐시된 토큰이 없는 상태
    credential = buildCredential(null, null);
    stubSuccessfulIssuance();

    // When
    KisAccessToken token = service.getValidToken(credential);

    // Then: 1회 발급하고 만료 시각과 암호문을 저장한다.
    assertThat(token.token()).isEqualTo(ISSUED_TOKEN);
    verify(authClient).issueToken(DUMMY_APP_KEY, DUMMY_APP_SECRET);
    verify(credentialRepository).save(credential);
    assertThat(credential.getTokenExpiresAt()).isEqualTo(token.expiresAt());
  }

  @Test
  void getValidToken_storedTokenIsCiphertext_notPlainText() {
    // Given: 캐시가 없어 새로 발급되는 경로
    credential = buildCredential(null, null);
    stubSuccessfulIssuance();

    // When
    service.getValidToken(credential);

    // Then: 엔티티에는 평문이 아니라 암호문이 남고, 그 암호문을 복호화해야 평문이 나온다.
    String stored = credential.getAccessTokenEncrypted();
    assertThat(stored).isNotNull().isNotEqualTo(ISSUED_TOKEN).doesNotContain(ISSUED_TOKEN);
    assertThat(secretEncryptor.decrypt(stored)).isEqualTo(ISSUED_TOKEN);
  }

  @Test
  void getValidToken_tenConcurrentCalls_issuesTokenOnlyOnce() throws Exception {
    // Given: 캐시가 없는 상태에서 동시 10개 호출이 들어온다.
    credential = buildCredential(null, null);
    AtomicInteger issueCount = new AtomicInteger();
    when(authClient.issueToken(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              issueCount.incrementAndGet();
              // 락이 없다면 다른 스레드와 발급이 겹치는 상황을 재현하기 위한 지연.
              Thread.sleep(50);
              return new KisAccessToken(ISSUED_TOKEN, LocalDateTime.now(clock).plusDays(1));
            });

    int callers = 10;
    ExecutorService executor = Executors.newFixedThreadPool(callers);
    CountDownLatch startGate = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < callers; i++) {
        futures.add(
            executor.submit(
                () -> {
                  startGate.await();
                  return service.getValidToken(credential).token();
                }));
      }
      startGate.countDown();

      List<String> tokens = new ArrayList<>();
      for (Future<String> future : futures) {
        tokens.add(future.get(10, TimeUnit.SECONDS));
      }

      // Then: 첫 호출만 발급하고 나머지 9개는 직렬화된 뒤 캐시를 재사용한다.
      assertThat(issueCount.get()).isEqualTo(1);
      assertThat(tokens).hasSize(callers).containsOnly(ISSUED_TOKEN);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void getValidToken_issuanceFails_propagatesBrokerApiExceptionWithoutStoringAnything() {
    // Given: 캐시가 없는 상태에서 발급이 실패한다.
    credential = buildCredential(null, null);
    when(authClient.issueToken(anyString(), anyString()))
        .thenThrow(new BrokerApiException("token_issue_failed", "KIS 토큰 발급 중 오류 발생"));

    // When & Then: BrokerApiException이 그대로 전파된다(격리).
    assertThatThrownBy(() -> service.getValidToken(credential))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("KIS 토큰 발급");

    // 실패한 시도는 상태를 남기지 않는다(부분 상태 금지) → 재시도 차단을 추가하지 않았다.
    assertThat(credential.getAccessTokenEncrypted()).isNull();
    assertThat(credential.getTokenExpiresAt()).isNull();
    verify(credentialRepository, never()).save(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void getValidToken_issuanceFails_keepsStaleCacheUntouched() {
    // Given: 이미 만료된 캐시 토큰이 있고 재발급이 실패한다.
    String staleCiphertext = secretEncryptor.encrypt(CACHED_TOKEN);
    credential = buildCredential(staleCiphertext, LocalDateTime.now(clock).minusMinutes(1));
    when(authClient.issueToken(anyString(), anyString()))
        .thenThrow(new BrokerApiException("token_issue_failed", "KIS 토큰 발급 중 오류 발생"));

    // When & Then
    assertThatThrownBy(() -> service.getValidToken(credential))
        .isInstanceOf(BrokerApiException.class);

    // Then: 기존 캐시 값은 덮어쓰지 않는다(부분 갱신 금지).
    assertThat(credential.getAccessTokenEncrypted()).isEqualTo(staleCiphertext);
    verify(credentialRepository, never()).save(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void getValidToken_byCredentialId_looksUpCredentialAndReusesCache() {
    // Given: ID로 조회되는 인증정보에 유효한 캐시 토큰이 있다.
    credential =
        buildCredential(
            secretEncryptor.encrypt(CACHED_TOKEN), LocalDateTime.now(clock).plusHours(12));

    // When
    KisAccessToken token = service.getValidToken(CREDENTIAL_ID);

    // Then
    assertThat(token.token()).isEqualTo(CACHED_TOKEN);
    verify(authClient, never()).issueToken(anyString(), anyString());
  }

  @Test
  void getValidToken_byCredentialId_missingCredential_throwsBrokerApiException() {
    // Given: 없는 인증정보 ID
    when(credentialRepository.findByIdAndDeletedAtIsNull(CREDENTIAL_ID))
        .thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> service.getValidToken(CREDENTIAL_ID))
        .isInstanceOf(BrokerApiException.class)
        .hasMessageContaining("인증정보를 찾을 수 없습니다");
    verify(authClient, never()).issueToken(anyString(), anyString());
  }

  @Test
  void getValidToken_neverLeaksTokenOrSecretIntoLogsOrTokenToString() {
    // Given: 캐시가 없어 발급되는 경로
    credential = buildCredential(null, null);
    stubSuccessfulIssuance();

    // When
    KisAccessToken token = service.getValidToken(credential);

    // Then: 로그와 반환값의 toString()에 평문 토큰·앱키·앱시크릿이 없다.
    String logs = capturedLogs();
    assertThat(logs)
        .contains("브로커 접근토큰 캐시 갱신")
        .doesNotContain(ISSUED_TOKEN, CACHED_TOKEN, DUMMY_APP_KEY, DUMMY_APP_SECRET);
    assertThat(token.toString()).isEqualTo("KisAccessToken[***]");
    assertThat(credential.toString()).doesNotContain(ISSUED_TOKEN, DUMMY_APP_KEY, DUMMY_APP_SECRET);
  }

  @Test
  void getValidToken_nullCredentialOrNullId_throwsIllegalArgument() {
    SecuritiesAccountApiCredential noId =
        SecuritiesAccountApiCredential.builder().apiProvider("KIS").build();

    assertThatThrownBy(() -> service.getValidToken((SecuritiesAccountApiCredential) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.getValidToken(noId))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.getValidToken((Long) null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(authClient, never()).issueToken(anyString(), anyString());
  }

  @Test
  void getValidToken_blankCachedTokenOrMissingExpiry_issuesNewToken() {
    stubSuccessfulIssuance();
    // 토큰은 있는데 만료 시각이 없는 경우와, 토큰이 공백인 경우 모두 캐시로 인정하지 않는다.
    credential = buildCredential(secretEncryptor.encrypt(CACHED_TOKEN), null);
    assertThat(service.getValidToken(credential).token()).isEqualTo(ISSUED_TOKEN);

    credential = buildCredential("  ", LocalDateTime.now(clock).plusHours(12));
    assertThat(service.getValidToken(credential).token()).isEqualTo(ISSUED_TOKEN);
  }

  @Test
  void getValidToken_expiredExactlyAtMargin_issuesNewToken() {
    // 만료까지 정확히 5분이면 isAfter가 false라 재발급한다(경계값).
    credential =
        buildCredential(
            secretEncryptor.encrypt(CACHED_TOKEN), LocalDateTime.now(clock).plusMinutes(5));
    stubSuccessfulIssuance();

    assertThat(service.getValidToken(credential).token()).isEqualTo(ISSUED_TOKEN);
  }

  @Test
  void getValidToken_unsupportedProvider_throwsWithoutCallingIssuer() {
    credential =
        SecuritiesAccountApiCredential.builder()
            .id(CREDENTIAL_ID)
            .apiProvider("UNKNOWN")
            .appKeyEncrypted(secretEncryptor.encrypt(DUMMY_APP_KEY))
            .appSecretEncrypted(secretEncryptor.encrypt(DUMMY_APP_SECRET))
            .build();

    assertThatThrownBy(() -> service.getValidToken(credential))
        .isInstanceOfSatisfying(
            BrokerApiException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo("unsupported_provider"));
    verify(authClient, never()).issueToken(anyString(), anyString());
    verify(credentialRepository, never()).save(any(SecuritiesAccountApiCredential.class));
  }

  private void stubSuccessfulIssuance() {
    when(authClient.issueToken(anyString(), anyString()))
        .thenReturn(new KisAccessToken(ISSUED_TOKEN, LocalDateTime.now(clock).plusDays(1)));
  }

  private SecuritiesAccountApiCredential buildCredential(
      String accessTokenEncrypted, LocalDateTime tokenExpiresAt) {
    LocalDateTime now = LocalDateTime.now(clock);
    return SecuritiesAccountApiCredential.builder()
        .id(CREDENTIAL_ID)
        .securitiesAccountId(77L)
        .apiProvider("KIS")
        .appKeyEncrypted(secretEncryptor.encrypt(DUMMY_APP_KEY))
        .appSecretEncrypted(secretEncryptor.encrypt(DUMMY_APP_SECRET))
        .accessTokenEncrypted(accessTokenEncrypted)
        .tokenExpiresAt(tokenExpiresAt)
        .status(RecordStatus.ACTIVE)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private String capturedLogs() {
    return listAppender.list.stream()
        .map(
            event ->
                event.getFormattedMessage()
                    + " "
                    + (event.getThrowableProxy() == null
                        ? ""
                        : event.getThrowableProxy().getMessage()))
        .collect(Collectors.joining("\n"));
  }
}
