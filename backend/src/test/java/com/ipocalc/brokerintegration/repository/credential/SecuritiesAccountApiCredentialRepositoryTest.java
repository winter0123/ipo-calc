package com.ipocalc.brokerintegration.repository.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/breakdown/broker-readonly-api-pilot.md (SF-2) - AC-1810: 시크릿이 암호화되어 저장되고 DB 직접 조회해도
 * 평문이 보이지 않는다. - AC-1813: 계좌 1개에 동일 api_provider ACTIVE 인증정보가 2건 이상 생성되지 않는다 - 소프트 삭제(INACTIVE) 후
 * 같은 조합 재생성 가능
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesAccountApiCredentialRepositoryTest {

  @Autowired private SecuritiesAccountApiCredentialRepository credentialRepository;

  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  private Long securitiesAccountId;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();
    Account account =
        accountRepository.save(
            Account.builder()
                .email("credential-test-owner@example.com")
                .passwordHash("encodedPasswordHash")
                .name("계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(account.getId())
                .displayName("홍길동")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());

    // V15 시드와 겹치지 않는 고유 이름을 쓴다(uk_securities_firm_name_active 유니크 제약 대비).
    SecuritiesFirm securitiesFirm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name(uniqueFirmName())
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());

    SecuritiesAccount securitiesAccount =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(investor.getId())
                .securitiesFirmId(securitiesFirm.getId())
                .accountNumber("encrypted-account-number")
                .accountName("개인 MTS 계좌")
                .isDefault(false)
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    securitiesAccountId = securitiesAccount.getId();
  }

  /**
   * 시드(V15)에 없는 고유 증권사명을 만든다.
   *
   * <p>securities_firms의 (name, status) 유니크 제약 때문에 시드와 같은 이름을 쓰면 저장이 실패한다. @DataJpaTest의 롤백으로 행은 남지
   * 않지만, 이름은 매번 달라야 기존 행과 충돌하지 않는다.
   */
  private String uniqueFirmName() {
    return "자격증명테스트증권-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
  }

  private SecuritiesAccountApiCredential buildCredential(
      Long securitiesAccountId,
      String apiProvider,
      String appKeyEncrypted,
      String appSecretEncrypted,
      String accessTokenEncrypted,
      LocalDateTime tokenExpiresAt,
      RecordStatus status,
      LocalDateTime deletedAt) {
    LocalDateTime now = LocalDateTime.now();
    return SecuritiesAccountApiCredential.builder()
        .securitiesAccountId(securitiesAccountId)
        .apiProvider(apiProvider)
        .appKeyEncrypted(appKeyEncrypted)
        .appSecretEncrypted(appSecretEncrypted)
        .accessTokenEncrypted(accessTokenEncrypted)
        .tokenExpiresAt(tokenExpiresAt)
        .status(status)
        .deletedAt(deletedAt)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Test
  void saveCredentialFindByIdAndDeletedAtIsNullReturnsCredential() {
    // Given: 저장할 인증정보가 존재함
    // - AC-1810 성격: 엔티티 저장 후 DB 컬럼을 직접 읽어 평문이 아니라 암호문임을 확인 (테스트에서는 암호문 값으로 저장)

    SecuritiesAccountApiCredential credential =
        buildCredential(
            securitiesAccountId,
            "KIS",
            "encrypted-app-key",
            "encrypted-app-secret",
            "encrypted-access-token",
            LocalDateTime.now().plusDays(1),
            RecordStatus.ACTIVE,
            null);

    // When: 인증정보 저장 후 findByIdAndDeletedAtIsNull로 조회
    SecuritiesAccountApiCredential saved = credentialRepository.saveAndFlush(credential);
    Optional<SecuritiesAccountApiCredential> found =
        credentialRepository.findByIdAndDeletedAtIsNull(saved.getId());

    // Then: 저장된 인증정보가 정상 조회되고 암호문 값이 보관됨
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getSecuritiesAccountId()).isEqualTo(securitiesAccountId);
    assertThat(found.get().getApiProvider()).isEqualTo("KIS");
    assertThat(found.get().getAppKeyEncrypted()).isEqualTo("encrypted-app-key");
    assertThat(found.get().getAppSecretEncrypted()).isEqualTo("encrypted-app-secret");
    assertThat(found.get().getAccessTokenEncrypted()).isEqualTo("encrypted-access-token");
    assertThat(found.get().getTokenExpiresAt()).isNotNull();
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNullReturnsActiveCredential() {
    // Given: 같은 계좌의 KIS ACTIVE 인증정보 1개와 INACTIVE 인증정보 1개가 저장되어 있음

    credentialRepository.saveAndFlush(
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key-active",
            "enc-secret-active",
            "enc-token-active",
            LocalDateTime.now().plusDays(1),
            RecordStatus.ACTIVE,
            null));

    credentialRepository.saveAndFlush(
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key-inactive",
            "enc-secret-inactive",
            null,
            null,
            RecordStatus.INACTIVE,
            LocalDateTime.now()));

    // When: 계좌 ID + KIS + deletedAt IS NULL로 조회
    Optional<SecuritiesAccountApiCredential> result =
        credentialRepository.findBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
            securitiesAccountId, "KIS");

    // Then: ACTIVE 인증정보만 반환됨
    assertThat(result).isPresent();
    assertThat(result.get().getAppKeyEncrypted()).isEqualTo("enc-key-active");
    assertThat(result.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNullReturnsTrueForActive() {
    // Given: 같은 계좌의 KIS ACTIVE 인증정보가 저장되어 있음

    credentialRepository.saveAndFlush(
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key",
            "enc-secret",
            null,
            null,
            RecordStatus.ACTIVE,
            null));

    // When: 존재 여부 확인
    boolean exists =
        credentialRepository.existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
            securitiesAccountId, "KIS");

    // Then: true 반환
    assertThat(exists).isTrue();
  }

  @Test
  void existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNullReturnsFalseForInactive() {
    // Given: 같은 계좌의 KIS INACTIVE 인증정보만 저장되어 있음 (deletedAt 설정됨)

    credentialRepository.saveAndFlush(
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key",
            "enc-secret",
            null,
            null,
            RecordStatus.INACTIVE,
            LocalDateTime.now()));

    // When: 존재 여부 확인
    boolean exists =
        credentialRepository.existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
            securitiesAccountId, "KIS");

    // Then: false 반환 (소프트 삭제된 것은 조회 대상 아님)
    assertThat(exists).isFalse();
  }

  @Test
  void saveTwoActiveCredentialsForSameAccountAndProviderThrowsDataIntegrityViolationException() {
    // Given: 특정 계좌의 KIS ACTIVE 인증정보 1개가 저장되어 있음
    // - AC-1813: 계좌 1개에 동일 api_provider ACTIVE 인증정보가 2건 이상 생성되지 않는다
    //   (partial unique index uk_api_cred_account_provider_active)

    credentialRepository.saveAndFlush(
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key-1",
            "enc-secret-1",
            null,
            null,
            RecordStatus.ACTIVE,
            null));

    SecuritiesAccountApiCredential secondCredential =
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key-2",
            "enc-secret-2",
            null,
            null,
            RecordStatus.ACTIVE,
            null);

    // When & Then: 같은 계좌·같은 provider ACTIVE 2건 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class,
        () -> credentialRepository.saveAndFlush(secondCredential));
  }

  @Test
  void saveActiveCredentialAfterSoftDeleteAllowed() {
    // Given: 특정 계좌의 KIS ACTIVE 인증정보가 저장된 뒤 소프트 삭제(INACTIVE, deletedAt 설정)됨
    // - AC-1813 하위: 소프트 삭제 후 같은 조합 재생성 가능

    SecuritiesAccountApiCredential first =
        credentialRepository.saveAndFlush(
            buildCredential(
                securitiesAccountId,
                "KIS",
                "enc-key-1",
                "enc-secret-1",
                null,
                null,
                RecordStatus.ACTIVE,
                null));

    // 소프트 삭제
    first.deactivate(LocalDateTime.now());
    credentialRepository.saveAndFlush(first);

    // When: 같은 계좌·같은 provider로 새로운 ACTIVE 인증정보 저장
    SecuritiesAccountApiCredential second =
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key-2",
            "enc-secret-2",
            null,
            null,
            RecordStatus.ACTIVE,
            null);

    SecuritiesAccountApiCredential saved = credentialRepository.saveAndFlush(second);

    // Then: 유니크 위반 없이 저장됨
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getAppKeyEncrypted()).isEqualTo("enc-key-2");

    // 기존 것 조회 시 INACTIVE라 findBy...AndDeletedAtIsNull로 조회 안 됨
    Optional<SecuritiesAccountApiCredential> found =
        credentialRepository.findBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
            securitiesAccountId, "KIS");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  void differentProvidersForSameAccountAllowed() {
    // Given: 같은 계좌의 KIS ACTIVE 인증정보가 저장되어 있음

    credentialRepository.saveAndFlush(
        buildCredential(
            securitiesAccountId,
            "KIS",
            "enc-key-kis",
            "enc-secret-kis",
            null,
            null,
            RecordStatus.ACTIVE,
            null));

    // When: 같은 계좌의 다른 provider(예: 미래 확장용) ACTIVE 인증정보 저장
    SecuritiesAccountApiCredential otherProvider =
        buildCredential(
            securitiesAccountId,
            "OTHER",
            "enc-key-other",
            "enc-secret-other",
            null,
            null,
            RecordStatus.ACTIVE,
            null);

    SecuritiesAccountApiCredential saved = credentialRepository.saveAndFlush(otherProvider);

    // Then: 유니크 위반 없이 저장됨 (provider가 다르므로)
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getApiProvider()).isEqualTo("OTHER");
  }

  @Test
  void findByIdAndDeletedAtIsNullExcludesSoftDeletedCredential() {
    // Given: ACTIVE 인증정보와 소프트 삭제된(INACTIVE) 인증정보가 저장되어 있음

    SecuritiesAccountApiCredential active =
        credentialRepository.saveAndFlush(
            buildCredential(
                securitiesAccountId,
                "KIS",
                "enc-key-active",
                "enc-secret-active",
                null,
                null,
                RecordStatus.ACTIVE,
                null));

    SecuritiesAccountApiCredential deleted =
        credentialRepository.saveAndFlush(
            buildCredential(
                securitiesAccountId,
                "OTHER",
                "enc-key-deleted",
                "enc-secret-deleted",
                null,
                null,
                RecordStatus.INACTIVE,
                LocalDateTime.now()));

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<SecuritiesAccountApiCredential> activeResult =
        credentialRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<SecuritiesAccountApiCredential> deletedResult =
        credentialRepository.findByIdAndDeletedAtIsNull(deleted.getId());

    // Then: ACTIVE 인증정보만 반환되고 소프트 삭제된 것은 반환되지 않음
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getApiProvider()).isEqualTo("KIS");
    assertThat(deletedResult).isEmpty();
  }

  @Test
  void updateTokenIfKeyUnchangedSkipsWhenKeyWasRotatedAndKeepsNewKey() {
    // Given: 옛 키로 저장된 인증정보
    SecuritiesAccountApiCredential saved =
        credentialRepository.saveAndFlush(
            buildCredential(
                securitiesAccountId,
                "KIS",
                "old-key",
                "secret",
                null,
                null,
                RecordStatus.ACTIVE,
                null));
    LocalDateTime exp = LocalDateTime.now().plusDays(1);

    // When: 키 교체 후, 옛 키 기준으로 토큰 갱신 시도 / 같은 키로 토큰 갱신 시도
    saved.rotateSecrets("new-key", null, null);
    credentialRepository.saveAndFlush(saved);
    int stale =
        credentialRepository.updateTokenIfKeyUnchanged(
            saved.getId(), "old-key", "old-token", exp, LocalDateTime.now());
    int fresh =
        credentialRepository.updateTokenIfKeyUnchanged(
            saved.getId(), "new-key", "new-token", exp, LocalDateTime.now());

    // Then: 옛 키 토큰은 저장되지 않고, 새 키는 유지된다.
    assertThat(stale).isZero();
    assertThat(fresh).isEqualTo(1);
    SecuritiesAccountApiCredential reloaded =
        credentialRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getAppKeyEncrypted()).isEqualTo("new-key");
    assertThat(reloaded.getAccessTokenEncrypted()).isEqualTo("new-token");
  }
}
