package com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.LocalDateTime;
import java.util.List;
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
 * AC 인용: docs/spec/securities-account-management.md, docs/work-log/securities-account-management.md
 * - AC-001: 증권계좌 등록 성공 — 저장 시 status=ACTIVE, is_default=false, deleted_at=null 기본값 - AC-006: 계좌 목록은
 * 투자자 + status 기준으로 조회된다 (ACTIVE 필터) - AC-017: 투자자당 기본 계좌는 정확히 1개 — partial unique
 * index(uk_securities_account_default)로 2개 이상 불가 - D-SA-003~007: 엔티티/Repository는 암호화·마스킹·논리삭제 처리 없이
 * 순수 도메인 값만 보관한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesAccountRepositoryTest {

  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  private Long investorId;

  private Long securitiesFirmId;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();
    Account account =
        accountRepository.save(
            Account.builder()
                .email("securities-account-owner@example.com")
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
    investorId = investor.getId();

    // V15 시드와 겹치지 않는 고유 이름을 쓴다(uk_securities_firm_name_active 유니크 제약 대비).
    SecuritiesFirm securitiesFirm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name(uniqueFirmName())
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    securitiesFirmId = securitiesFirm.getId();
  }

  /**
   * 시드(V15)에 없는 고유 증권사명을 만든다.
   *
   * <p>securities_firms의 (name, status) 유니크 제약 때문에 시드와 같은 이름을 쓰면 저장이 실패한다. @DataJpaTest의 롤백으로 행은 남지
   * 않지만, 이름은 매번 달라야 기존 행과 충돌하지 않는다.
   */
  private String uniqueFirmName() {
    return "계좌테스트증권-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
  }

  private SecuritiesAccount buildAccount(
      Long investorIdForAccount,
      Long firmId,
      boolean isDefault,
      RecordStatus status,
      LocalDateTime deletedAt,
      String accountNumber) {
    LocalDateTime now = LocalDateTime.now();
    return SecuritiesAccount.builder()
        .investorId(investorIdForAccount)
        .securitiesFirmId(firmId)
        .accountNumber(accountNumber)
        .accountName("개인 MTS 계좌")
        .isDefault(isDefault)
        .status(status)
        .deletedAt(deletedAt)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private Investor createAdditionalInvestor(String email) {
    LocalDateTime now = LocalDateTime.now();
    Account account =
        accountRepository.save(
            Account.builder()
                .email(email)
                .passwordHash("encodedPasswordHash")
                .name("계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return investorRepository.save(
        Investor.builder()
            .accountId(account.getId())
            .displayName("홍길동")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  @Test
  void saveAccountFindByIdReturnsAccountWithDefaults() {
    // Given: 저장할 증권계좌(accountNumber, investorId, securitiesFirmId)가 존재함
    // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null, is_default 기본값 false로 생성

    LocalDateTime now = LocalDateTime.now();
    SecuritiesAccount account =
        buildAccount(
            investorId,
            securitiesFirmId,
            false,
            RecordStatus.ACTIVE,
            null,
            "encrypted-account-number-1");

    // When: 계좌 저장 후 findById로 조회
    SecuritiesAccount saved = securitiesAccountRepository.save(account);
    Optional<SecuritiesAccount> found = securitiesAccountRepository.findById(saved.getId());

    // Then: 저장된 계좌가 id, investor_id, securities_firm_id, account_number, 기본값으로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getInvestorId()).isEqualTo(investorId);
    assertThat(found.get().getSecuritiesFirmId()).isEqualTo(securitiesFirmId);
    assertThat(found.get().getAccountNumber()).isEqualTo("encrypted-account-number-1");
    assertThat(found.get().getAccountName()).isEqualTo("개인 MTS 계좌");
    assertThat(found.get().getIsDefault()).isFalse();
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findByInvestorIdAndStatusReturnsOnlyMatchingAccounts() {
    // Given: 같은 투자자의 ACTIVE 계좌 1개와 INACTIVE 계좌 1개가 저장되어 있음
    // - spec AC-006: 목록 조회 status 필터(기본 ACTIVE)에 해당하는 계좌만 반환

    LocalDateTime now = LocalDateTime.now();
    securitiesAccountRepository.save(
        buildAccount(investorId, securitiesFirmId, false, RecordStatus.ACTIVE, null, "enc-A"));
    securitiesAccountRepository.save(
        buildAccount(investorId, securitiesFirmId, false, RecordStatus.INACTIVE, now, "enc-B"));

    // When: 투자자 + ACTIVE 상태로 findByInvestorIdAndStatus 호출
    List<SecuritiesAccount> result =
        securitiesAccountRepository.findByInvestorIdAndStatus(investorId, RecordStatus.ACTIVE);

    // Then: ACTIVE 계좌만 반환된다
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getAccountNumber()).isEqualTo("enc-A");
    assertThat(result.get(0).getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void findByInvestorIdReturnsAllStatuses() {
    // Given: 같은 투자자의 ACTIVE 계좌 1개와 INACTIVE 계좌 1개가 저장되어 있음
    // - status=BOTH 조회 시 ACTIVE+INACTIVE 모두 조회해야 한다 (findByInvestorId)

    LocalDateTime now = LocalDateTime.now();
    securitiesAccountRepository.save(
        buildAccount(investorId, securitiesFirmId, false, RecordStatus.ACTIVE, null, "enc-A"));
    securitiesAccountRepository.save(
        buildAccount(investorId, securitiesFirmId, false, RecordStatus.INACTIVE, now, "enc-B"));

    // When: 해당 투자자로 findByInvestorId 호출
    List<SecuritiesAccount> result = securitiesAccountRepository.findByInvestorId(investorId);

    // Then: ACTIVE/INACTIVE 계좌 모두 반환된다
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(SecuritiesAccount::getStatus)
        .containsExactlyInAnyOrder(RecordStatus.ACTIVE, RecordStatus.INACTIVE);
  }

  @Test
  void findByIdAndDeletedAtIsNullExcludesSoftDeletedAccount() {
    // Given: ACTIVE 계좌와 deletedAt이 설정된(논리 삭제) 계좌가 저장되어 있음
    // - 상세 조회는 deletedAt IS NULL(활성) 계좌만 노출

    LocalDateTime now = LocalDateTime.now();
    SecuritiesAccount active =
        securitiesAccountRepository.save(
            buildAccount(investorId, securitiesFirmId, false, RecordStatus.ACTIVE, null, "enc-A"));
    SecuritiesAccount deleted =
        securitiesAccountRepository.save(
            buildAccount(investorId, securitiesFirmId, false, RecordStatus.INACTIVE, now, "enc-B"));

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<SecuritiesAccount> activeResult =
        securitiesAccountRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<SecuritiesAccount> deletedResult =
        securitiesAccountRepository.findByIdAndDeletedAtIsNull(deleted.getId());

    // Then: ACTIVE 계좌만 반환되고 논리 삭제된 계좌는 반환되지 않는다
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getAccountNumber()).isEqualTo("enc-A");
    assertThat(deletedResult).isEmpty();
  }

  @Test
  void findByInvestorIdAndIsDefaultTrueReturnsDefaultAccount() {
    // Given: 같은 투자자의 기본 계좌(is_default=true) 1개와 일반 계좌 1개가 저장되어 있음
    // - SF-7 기본 계좌 지정 시 기존 기본 계좌 조회에 사용

    securitiesAccountRepository.saveAndFlush(
        buildAccount(investorId, securitiesFirmId, true, RecordStatus.ACTIVE, null, "enc-default"));
    securitiesAccountRepository.saveAndFlush(
        buildAccount(investorId, securitiesFirmId, false, RecordStatus.ACTIVE, null, "enc-normal"));

    // When: 해당 투자자로 findByInvestorIdAndIsDefaultTrue 호출
    Optional<SecuritiesAccount> result =
        securitiesAccountRepository.findByInvestorIdAndIsDefaultTrue(investorId);

    // Then: 기본 계좌가 반환된다
    assertThat(result).isPresent();
    assertThat(result.get().getAccountNumber()).isEqualTo("enc-default");
    assertThat(result.get().getIsDefault()).isTrue();
  }

  @Test
  void saveTwoDefaultAccountsForSameInvestorThrowsDataIntegrityViolationException() {
    // Given: 특정 투자자의 기본 계좌(is_default=true) 1개가 저장되어 있음
    // - spec AC-017 / D-SF 계열과 동일 원리: partial unique index(uk_securities_account_default)로
    //   투자자당 is_default=true는 정확히 1개만 허용 (PostgreSQL 부분 유니크)

    securitiesAccountRepository.saveAndFlush(
        buildAccount(investorId, securitiesFirmId, true, RecordStatus.ACTIVE, null, "enc-1"));

    SecuritiesAccount secondDefault =
        buildAccount(investorId, securitiesFirmId, true, RecordStatus.ACTIVE, null, "enc-2");

    // When & Then: 같은 투자자의 두 번째 기본 계좌 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class,
        () -> securitiesAccountRepository.saveAndFlush(secondDefault));
  }

  @Test
  void saveDefaultAccountsForDifferentInvestorsAllowed() {
    // Given: 투자자 A가 기본 계좌를 보유한 상태
    // - AC-017: 투자자당 1개의 제약이므로 서로 다른 투자자의 기본 계좌는 동시에 존재 가능

    securitiesAccountRepository.saveAndFlush(
        buildAccount(
            investorId, securitiesFirmId, true, RecordStatus.ACTIVE, null, "enc-A-default"));
    Investor otherInvestor = createAdditionalInvestor("other-securities-owner@example.com");

    // When: 투자자 B의 기본 계좌 저장
    SecuritiesAccount otherDefault =
        securitiesAccountRepository.saveAndFlush(
            buildAccount(
                otherInvestor.getId(),
                securitiesFirmId,
                true,
                RecordStatus.ACTIVE,
                null,
                "enc-B-default"));

    // Then: 유니크 위반 없이 저장된다
    assertThat(otherDefault.getId()).isNotNull();
    assertThat(otherDefault.getIsDefault()).isTrue();
  }
}
