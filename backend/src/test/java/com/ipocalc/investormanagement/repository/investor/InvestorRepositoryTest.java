package com.ipocalc.investormanagement.repository.investor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/spec/investor-management.md - AC-001: 투자자 등록 성공 — 신규 투자자 레코드가 생성되고 status=ACTIVE,
 * deleted_at=null 반환 - AC-004: 투자자 목록 조회 (본인 계정 소유만) — 해당 계정 소유 투자자 목록 반환 - D-I-003: 소프트 삭제는 status
 * + deleted_at 모두 사용 - D-I-004: 이름 중복 완전 허용, 유니크 제약 없음.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class InvestorRepositoryTest {

  @Autowired private InvestorRepository investorRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private Long accountId;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();
    Account account =
        accountRepository.save(
            Account.builder()
                .email("investor-owner@example.com")
                .passwordHash("encodedPasswordHash")
                .name("계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    accountId = account.getId();
  }

  @Test
  void statusColumnStoresEnumNameAsPlainString() {
    // Given: ACTIVE 와 INACTIVE 투자자를 저장함
    LocalDateTime now = LocalDateTime.now();
    Investor active =
        investorRepository.saveAndFlush(
            Investor.builder()
                .accountId(accountId)
                .displayName("활성")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    Investor inactive =
        investorRepository.saveAndFlush(
            Investor.builder()
                .accountId(accountId)
                .displayName("비활성")
                .status(RecordStatus.INACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());

    // When/Then: enum 도입 후에도 DB 컬럼 값은 기존과 같은 문자열이다
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM investors WHERE id = ?", String.class, active.getId()))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM investors WHERE id = ?", String.class, inactive.getId()))
        .isEqualTo("INACTIVE");
  }

  @Test
  void saveInvestorFindByIdReturnsInvestorWithActiveStatus() {
    // Given: 저장할 투자자(displayName, accountId, status 기본값 ACTIVE)가 존재함
    // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null로 생성된다

    Investor investor =
        Investor.builder()
            .accountId(accountId)
            .displayName("홍길동")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When: 투자자 저장 후 findById로 조회
    Investor saved = investorRepository.save(investor);
    Optional<Investor> found = investorRepository.findById(saved.getId());

    // Then: 저장된 투자자가 id, account_id, display_name, status로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getAccountId()).isEqualTo(accountId);
    assertThat(found.get().getDisplayName()).isEqualTo("홍길동");
    assertThat(found.get().getRelationship()).isEqualTo("SELF");
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findByAccountIdAndStatusReturnsOnlyMatchingInvestors() {
    // Given: 같은 계정 소유의 ACTIVE 투자자 1명과 INACTIVE 투자자 1명이 저장되어 있음
    // - spec AC-004: 계정 소유 투자자 목록을 status 기준으로 조회한다

    Investor active =
        Investor.builder()
            .accountId(accountId)
            .displayName("김철수")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    Investor inactive =
        Investor.builder()
            .accountId(accountId)
            .displayName("김영희")
            .relationship("SPOUSE")
            .status(RecordStatus.INACTIVE)
            .deletedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    investorRepository.save(active);
    investorRepository.save(inactive);

    // When: 해당 계정 + ACTIVE 상태로 findByAccountIdAndStatus 호출
    List<Investor> result =
        investorRepository.findByAccountIdAndStatus(accountId, RecordStatus.ACTIVE);

    // Then: ACTIVE 투자자만 반환된다
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getDisplayName()).isEqualTo("김철수");
    assertThat(result.get(0).getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void findByAccountIdAndStatusReturnsEmptyWhenNoMatch() {
    // Given: 투자자가 저장되어 있지 않은 계정 ID가 존재함

    // When: 존재하지 않는 계정 ID로 findByAccountIdAndStatus 호출
    List<Investor> result =
        investorRepository.findByAccountIdAndStatus(999999L, RecordStatus.ACTIVE);

    // Then: 빈 목록이 반환된다
    assertThat(result).isEmpty();
  }

  @Test
  void findByAccountIdReturnsAllStatuses() {
    // Given: 같은 계정 소유의 ACTIVE 투자자 1명과 INACTIVE 투자자 1명이 저장되어 있음
    // - GET /investors?status=BOTH 시 ACTIVE+INACTIVE 모두 조회해야 한다 (findByAccountId)

    LocalDateTime now = LocalDateTime.now();
    investorRepository.save(
        Investor.builder()
            .accountId(accountId)
            .displayName("김철수")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    investorRepository.save(
        Investor.builder()
            .accountId(accountId)
            .displayName("김영희")
            .relationship("SPOUSE")
            .status(RecordStatus.INACTIVE)
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: 해당 계정으로 findByAccountId 호출
    List<Investor> result = investorRepository.findByAccountId(accountId);

    // Then: ACTIVE/INACTIVE 투자자 모두 반환된다
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(Investor::getStatus)
        .containsExactlyInAnyOrder(RecordStatus.ACTIVE, RecordStatus.INACTIVE);
  }

  @Test
  void findByAccountIdReturnsEmptyWhenNoMatch() {
    // When: 투자자가 저장되어 있지 않은 계정 ID로 findByAccountId 호출
    List<Investor> result = investorRepository.findByAccountId(888888L);

    // Then: 빈 목록이 반환된다
    assertThat(result).isEmpty();
  }

  @Test
  void saveTwoInvestorsWithSameDisplayNameAllowed() {
    // Given: 같은 계정에서 동일한 표시 이름의 투자자를 두 명 등록
    // - spec AC-011: 이름 중복 허용 (동명이인 가족 상황 반영)
    // - D-I-004: 유니크 제약 없음

    LocalDateTime now = LocalDateTime.now();
    investorRepository.save(
        Investor.builder()
            .accountId(accountId)
            .displayName("홍길동")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    investorRepository.save(
        Investor.builder()
            .accountId(accountId)
            .displayName("홍길동")
            .relationship("CHILD")
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: 해당 계정 + ACTIVE 상태로 조회
    List<Investor> result =
        investorRepository.findByAccountIdAndStatus(accountId, RecordStatus.ACTIVE);

    // Then: 중복 이름의 투자자 두 명이 모두 조회된다
    assertThat(result).hasSize(2);
  }
}
