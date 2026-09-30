package com.vibeaiteam.brokerintegration.service.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
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
import java.lang.reflect.RecordComponent;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * ApiCredentialService 단위 테스트.
 *
 * <p>AC 인용: docs/breakdown/broker-readonly-api-pilot.md (SF-2) - AC-1810: 시크릿이 암호화되어 저장되고 평문이
 * 남지 않는다 - AC-1811: 응답 DTO에 시크릿·토큰 필드가 존재하지 않는다 - AC-1812: 타 계정 소유 계좌 접근은 403 -
 * AC-1813: 같은 계좌·provider 중복 등록은 409 - AC-1814: 브로커 암호화 키 미설정 시 등록이 명확한 오류로 실패한다.
 */
@ExtendWith(MockitoExtension.class)
class ApiCredentialServiceTest {

  private static final Long ACCOUNT_ID = 1L;
  private static final Long INVESTOR_ID = 9L;
  private static final Long OWNER_ACCOUNT_ID = 100L;
  private static final String PLAIN_ACCOUNT_NUMBER = "123-456-789012";
  private static final String DUMMY_APP_KEY = "dummy-app-key";
  private static final String DUMMY_APP_SECRET = "dummy-app-secret";

  @Mock private SecuritiesAccountApiCredentialRepository credentialRepository;
  @Mock private SecuritiesAccountRepository securitiesAccountRepository;
  @Mock private InvestorRepository investorRepository;
  @Mock private SecurityUtils securityUtils;

  private final SecureRandom secureRandom = new SecureRandom();

  private BrokerSecretEncryptor brokerSecretEncryptor;
  private AccountNumberEncryptor accountNumberEncryptor;
  private ApiCredentialService service;

  @BeforeEach
  void setUp() {
    brokerSecretEncryptor = new BrokerSecretEncryptor(newRandomKey());
    accountNumberEncryptor = new AccountNumberEncryptor(newRandomKey());
    service =
        new ApiCredentialService(
            credentialRepository,
            securitiesAccountRepository,
            investorRepository,
            brokerSecretEncryptor,
            accountNumberEncryptor,
            securityUtils,
            Clock.systemDefaultZone());
  }

  private String newRandomKey() {
    byte[] keyBytes = new byte[32];
    secureRandom.nextBytes(keyBytes);
    return Base64.getEncoder().encodeToString(keyBytes);
  }

  private ApiCredentialService serviceWithoutBrokerKey() {
    return new ApiCredentialService(
        credentialRepository,
        securitiesAccountRepository,
        investorRepository,
        new BrokerSecretEncryptor(""),
        accountNumberEncryptor,
        securityUtils,
        Clock.systemDefaultZone());
  }

  private void stubOwnedSecuritiesAccount() {
    stubOwnedSecuritiesAccount(OWNER_ACCOUNT_ID);
  }

  private void stubOwnedSecuritiesAccount(Long ownerAccountId) {
    SecuritiesAccount account =
        SecuritiesAccount.builder()
            .id(ACCOUNT_ID)
            .investorId(INVESTOR_ID)
            .accountNumber(accountNumberEncryptor.encrypt(PLAIN_ACCOUNT_NUMBER))
            .accountName("개인 MTS 계좌")
            .status(RecordStatus.ACTIVE)
            .build();
    when(securitiesAccountRepository.findByIdAndDeletedAtIsNull(ACCOUNT_ID))
        .thenReturn(Optional.of(account));
    when(investorRepository.findById(INVESTOR_ID))
        .thenReturn(
            Optional.of(Investor.builder().id(INVESTOR_ID).accountId(ownerAccountId).build()));
    when(securityUtils.getCurrentAccountId()).thenReturn(OWNER_ACCOUNT_ID);
  }

  private void stubSaveAndFlushAssignsId() {
    when(credentialRepository.saveAndFlush(any(SecuritiesAccountApiCredential.class)))
        .thenAnswer(
            invocation -> {
              SecuritiesAccountApiCredential credential =
                  invocation.getArgument(0, SecuritiesAccountApiCredential.class);
              credential.setId(77L);
              return credential;
            });
  }

  private SecuritiesAccountApiCredential activeCredential(
      LocalDateTime updatedAt, String accessTokenEncrypted, LocalDateTime tokenExpiresAt) {
    return SecuritiesAccountApiCredential.builder()
        .id(77L)
        .securitiesAccountId(ACCOUNT_ID)
        .apiProvider("KIS")
        .appKeyEncrypted("encrypted-app-key")
        .appSecretEncrypted("encrypted-app-secret")
        .accessTokenEncrypted(accessTokenEncrypted)
        .tokenExpiresAt(tokenExpiresAt)
        .status(RecordStatus.ACTIVE)
        .deletedAt(null)
        .createdAt(updatedAt)
        .updatedAt(updatedAt)
        .build();
  }

  @Test
  void registerStoresEncryptedSecretsAndNeverPlainText() {
    // Given: 인증된 계정이 소유한 증권계좌가 있고, 브로커 암호화 키가 설정되어 있음 (AC-1810)
    stubOwnedSecuritiesAccount();
    stubSaveAndFlushAssignsId();

    // When: 인증정보를 등록함
    ApiCredentialResponse response =
        service.register(ACCOUNT_ID, "KIS", DUMMY_APP_KEY, DUMMY_APP_SECRET);

    // Then: 저장된 값은 평문이 아닌 암호문이며, 복호화하면 원본과 일치함 (AC-1810)
    ArgumentCaptor<SecuritiesAccountApiCredential> captor =
        ArgumentCaptor.forClass(SecuritiesAccountApiCredential.class);
    verify(credentialRepository).saveAndFlush(captor.capture());
    SecuritiesAccountApiCredential saved = captor.getValue();
    assertThat(saved.getAppKeyEncrypted())
        .as("앱키는 평문 그대로 저장되면 안 됩니다 (AC-1810)")
        .isNotEqualTo(DUMMY_APP_KEY);
    assertThat(saved.getAppSecretEncrypted())
        .as("앱시크릿은 평문 그대로 저장되면 안 됩니다 (AC-1810)")
        .isNotEqualTo(DUMMY_APP_SECRET);
    assertThat(brokerSecretEncryptor.decrypt(saved.getAppKeyEncrypted()))
        .as("암호문은 원본 앱키로 복호화되어야 합니다 (AC-1810)")
        .isEqualTo(DUMMY_APP_KEY);
    assertThat(brokerSecretEncryptor.decrypt(saved.getAppSecretEncrypted()))
        .isEqualTo(DUMMY_APP_SECRET);
    assertThat(saved.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(saved.getDeletedAt()).isNull();

    // And: 응답에는 연결 상태와 마스킹된 계좌번호만 있고 시크릿 필드는 없음 (AC-1811)
    assertThat(response.connectionStatus()).isEqualTo(ApiCredentialService.CONNECTION_CONNECTED);
    assertThat(response.accountNumber()).isEqualTo(AccountNumberMasker.mask(PLAIN_ACCOUNT_NUMBER));
    assertThat(response.lastSyncedAt()).isNull();
    List<String> responseFields =
        Arrays.stream(ApiCredentialResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    assertThat(responseFields)
        .as("응답 DTO에 시크릿·토큰 필드가 존재하면 안 됩니다 (AC-1811)")
        .doesNotContain("appKey", "appSecret", "accessToken", "tokenExpiresAt")
        .noneMatch(name -> name.toLowerCase().contains("secret"))
        .noneMatch(name -> name.toLowerCase().contains("token"));
  }

  @Test
  void registerWithBlankSecretInputsThrowsValidation() {
    // Given: apiProvider는 제공되었으나 appKey가 비어 있음

    // When & Then: 400 성격의 검증 예외가 발생하고 저장은 호출되지 않음
    ApiCredentialValidationException exception =
        assertThrows(
            ApiCredentialValidationException.class,
            () -> service.register(ACCOUNT_ID, "KIS", " ", DUMMY_APP_SECRET));
    assertThat(exception.getMessage()).contains("appKey");
    verify(credentialRepository, never()).saveAndFlush(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void registerWithUnknownSecuritiesAccountThrowsNotFound() {
    // Given: 존재하지 않는 증권계좌 ID
    when(securitiesAccountRepository.findByIdAndDeletedAtIsNull(ACCOUNT_ID))
        .thenReturn(Optional.empty());

    // When & Then: 404 성격의 예외 발생
    assertThrows(
        ApiCredentialNotFoundException.class,
        () -> service.register(ACCOUNT_ID, "KIS", DUMMY_APP_KEY, DUMMY_APP_SECRET));
    verify(credentialRepository, never()).saveAndFlush(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void registerForOtherAccountsSecuritiesAccountThrowsForbidden() {
    // Given: 인증된 계정 A와, 다른 계정 B가 소유한 증권계좌 (AC-1812)
    stubOwnedSecuritiesAccount(200L);

    // When & Then: 소유권이 없으면 403 성격의 예외 발생 (AC-1812)
    ApiCredentialForbiddenException exception =
        assertThrows(
            ApiCredentialForbiddenException.class,
            () -> service.register(ACCOUNT_ID, "KIS", DUMMY_APP_KEY, DUMMY_APP_SECRET));
    assertThat(exception.getMessage()).isEqualTo("계정 소유권이 없습니다.");
    verify(credentialRepository, never()).saveAndFlush(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void registerWithExistingActiveCredentialThrowsConflict() {
    // Given: 같은 계좌·같은 provider의 활성 인증정보가 이미 존재함 (AC-1813)
    stubOwnedSecuritiesAccount();
    when(credentialRepository.existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
            ACCOUNT_ID, "KIS"))
        .thenReturn(true);

    // When & Then: 중복 등록 시 409 성격의 예외 발생 (AC-1813)
    ApiCredentialConflictException exception =
        assertThrows(
            ApiCredentialConflictException.class,
            () -> service.register(ACCOUNT_ID, "KIS", DUMMY_APP_KEY, DUMMY_APP_SECRET));
    assertThat(exception.getMessage()).contains("이미 등록된");
    verify(credentialRepository, never()).saveAndFlush(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void registerWhenUniqueIndexViolatedConcurrentlyThrowsConflict() {
    // Given: 존재 여부 검사 결과는 false이나 저장 시 부분 유니크 인덱스에 위반됨 (AC-1813 방어)
    stubOwnedSecuritiesAccount();
    when(credentialRepository.existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
            ACCOUNT_ID, "KIS"))
        .thenReturn(false);
    when(credentialRepository.saveAndFlush(any(SecuritiesAccountApiCredential.class)))
        .thenThrow(new DataIntegrityViolationException("uk_api_cred_account_provider_active"));

    // When & Then: 409 성격의 예외로 변환됨 (AC-1813)
    assertThrows(
        ApiCredentialConflictException.class,
        () -> service.register(ACCOUNT_ID, "KIS", DUMMY_APP_KEY, DUMMY_APP_SECRET));
  }

  @Test
  void registerWhenBrokerEncryptionKeyMissingThrowsUnavailable() {
    // Given: APP_ENCRYPTION_BROKER_KEY가 미설정인 서비스와, 소유권이 있는 증권계좌 (AC-1814)
    stubOwnedSecuritiesAccount();

    // When & Then: 등록 요청이 "브로커 연결 미활성" 오류로 거부됨 (AC-1814)
    BrokerConnectionUnavailableException exception =
        assertThrows(
            BrokerConnectionUnavailableException.class,
            () ->
                serviceWithoutBrokerKey()
                    .register(ACCOUNT_ID, "KIS", DUMMY_APP_KEY, DUMMY_APP_SECRET));
    assertThat(exception.getMessage()).contains("브로커 연결 미활성");
    assertThat(exception.getMessage()).doesNotContain(DUMMY_APP_KEY);
    assertThat(exception.getMessage()).doesNotContain(DUMMY_APP_SECRET);
    verify(credentialRepository, never()).saveAndFlush(any(SecuritiesAccountApiCredential.class));
  }

  @Test
  void getStatusReturnsConnectedWithMaskedAccountNumber() {
    // Given: 활성 인증정보가 있고 접근토큰은 아직 캐시되지 않음
    stubOwnedSecuritiesAccount();
    LocalDateTime updatedAt = LocalDateTime.now().minusMinutes(5);
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(List.of(activeCredential(updatedAt, null, null)));

    // When: 연결 상태를 조회함
    ApiCredentialResponse response = service.getStatus(ACCOUNT_ID);

    // Then: CONNECTED와 마스킹된 계좌번호가 반환되고 시크릿 필드는 없음 (AC-1811)
    assertThat(response.connectionStatus()).isEqualTo(ApiCredentialService.CONNECTION_CONNECTED);
    assertThat(response.apiProvider()).isEqualTo("KIS");
    assertThat(response.accountNumber()).isEqualTo(AccountNumberMasker.mask(PLAIN_ACCOUNT_NUMBER));
    assertThat(response.accountNumber()).isNotEqualTo(PLAIN_ACCOUNT_NUMBER);
    assertThat(response.lastSyncedAt())
        .as("토큰이 캐시되지 않았으면 마지막 동기화 시각은 null이어야 합니다")
        .isNull();
  }

  @Test
  void getStatusReturnsExpiredWhenCachedTokenExpired() {
    // Given: 접근토큰 만료 시각이 지난 인증정보가 있음
    stubOwnedSecuritiesAccount();
    LocalDateTime updatedAt = LocalDateTime.now().minusDays(2);
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(
            List.of(
                activeCredential(updatedAt, "encrypted-token", LocalDateTime.now().minusHours(1))));

    // When & Then: EXPIRED 상태가 반환됨
    ApiCredentialResponse response = service.getStatus(ACCOUNT_ID);
    assertThat(response.connectionStatus()).isEqualTo(ApiCredentialService.CONNECTION_EXPIRED);
    assertThat(response.lastSyncedAt()).isEqualTo(updatedAt);
  }

  @Test
  void getStatusWithoutCredentialReturnsDisconnected() {
    // Given: 연결된 인증정보가 없는 증권계좌
    stubOwnedSecuritiesAccount();
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(List.of());

    // When & Then: DISCONNECTED가 반환됨
    ApiCredentialResponse response = service.getStatus(ACCOUNT_ID);
    assertThat(response.connectionStatus())
        .isEqualTo(ApiCredentialService.CONNECTION_DISCONNECTED);
    assertThat(response.id()).isNull();
    assertThat(response.apiProvider()).isNull();
    assertThat(response.accountNumber()).isEqualTo(AccountNumberMasker.mask(PLAIN_ACCOUNT_NUMBER));
  }

  @Test
  void getStatusReturnsErrorWhenBrokerEncryptionKeyMissing() {
    // Given: 인증정보는 있으나 브로커 암호화 키가 미설정인 상태
    stubOwnedSecuritiesAccount();
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            ACCOUNT_ID))
        .thenReturn(List.of(activeCredential(LocalDateTime.now(), null, null)));

    // When & Then: 저장된 시크릿을 쓸 수 없으므로 ERROR 상태가 반환됨
    ApiCredentialResponse response = serviceWithoutBrokerKey().getStatus(ACCOUNT_ID);
    assertThat(response.connectionStatus()).isEqualTo(ApiCredentialService.CONNECTION_ERROR);
  }

  @Test
  void disconnectSoftDeletesActiveCredentialWithoutPhysicalDelete() {
    // Given: 활성 인증정보가 1건 존재함
    stubOwnedSecuritiesAccount();
    SecuritiesAccountApiCredential credential =
        activeCredential(LocalDateTime.now(), "encrypted-token", LocalDateTime.now().plusDays(1));
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNull(ACCOUNT_ID))
        .thenReturn(List.of(credential));

    // When: 연결 해제함
    service.disconnect(ACCOUNT_ID);

    // Then: 물리 삭제 없이 status/deletedAt으로 소프트 삭제됨
    verify(credentialRepository, never()).delete(any(SecuritiesAccountApiCredential.class));
    verify(credentialRepository, never()).deleteAll(anyList());
    verify(credentialRepository).saveAll(List.of(credential));
    assertThat(credential.getStatus()).isEqualTo(RecordStatus.INACTIVE);
    assertThat(credential.getDeletedAt()).as("소프트 삭제 시각이 설정되어야 합니다").isNotNull();
    assertThat(credential.getUpdatedAt()).isNotNull();
  }

  @Test
  void disconnectWithoutCredentialDoesNothing() {
    // Given: 이미 연결 해제된 증권계좌
    stubOwnedSecuritiesAccount();
    when(credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNull(ACCOUNT_ID))
        .thenReturn(List.of());

    // When & Then: 예외 없이 종료되고 저장도 호출되지 않음
    service.disconnect(ACCOUNT_ID);
    verify(credentialRepository, never()).saveAll(anyList());
  }

  @Test
  void disconnectForOtherAccountsSecuritiesAccountThrowsForbidden() {
    // Given: 다른 계정이 소유한 증권계좌 (AC-1812)
    stubOwnedSecuritiesAccount(200L);

    // When & Then: 403 성격의 예외 발생 (AC-1812)
    assertThrows(ApiCredentialForbiddenException.class, () -> service.disconnect(ACCOUNT_ID));
    verify(credentialRepository, never()).saveAll(anyList());
  }

  @Test
  void disconnectWithUnknownSecuritiesAccountThrowsNotFound() {
    // Given: 존재하지 않는 증권계좌 ID
    when(securitiesAccountRepository.findByIdAndDeletedAtIsNull(ACCOUNT_ID))
        .thenReturn(Optional.empty());

    // When & Then: 404 성격의 예외 발생
    assertThrows(ApiCredentialNotFoundException.class, () -> service.disconnect(ACCOUNT_ID));
  }
}
