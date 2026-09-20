package com.vibeaiteam.investormanagement.repository.investor;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC 인용: docs/spec/investor-management.md
 * - AC-001: 투자자 등록 성공 — 신규 투자자 레코드가 생성되고 status=ACTIVE, deleted_at=null 반환
 * - AC-004: 투자자 목록 조회 (본인 계정 소유만) — 해당 계정 소유 투자자 목록 반환
 * - D-I-003: 소프트 삭제는 status + deleted_at 모두 사용
 * - D-I-004: 이름 중복 완전 허용, 유니크 제약 없음
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class InvestorRepositoryTest {

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        Account account = accountRepository.save(Account.builder()
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
    void saveInvestorFindByIdReturnsInvestorWithActiveStatus() {
        // Given: 저장할 투자자(displayName, accountId, status 기본값 ACTIVE)가 존재함
        // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null로 생성된다

        Investor investor = Investor.builder()
                .accountId(accountId)
                .displayName("홍길동")
                .relationship("SELF")
                .status("ACTIVE")
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
        assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(found.get().getDeletedAt()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void findByAccountIdAndStatusReturnsOnlyMatchingInvestors() {
        // Given: 같은 계정 소유의 ACTIVE 투자자 1명과 INACTIVE 투자자 1명이 저장되어 있음
        // - spec AC-004: 계정 소유 투자자 목록을 status 기준으로 조회한다

        Investor active = Investor.builder()
                .accountId(accountId)
                .displayName("김철수")
                .relationship("SELF")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Investor inactive = Investor.builder()
                .accountId(accountId)
                .displayName("김영희")
                .relationship("SPOUSE")
                .status("INACTIVE")
                .deletedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        investorRepository.save(active);
        investorRepository.save(inactive);

        // When: 해당 계정 + ACTIVE 상태로 findByAccountIdAndStatus 호출
        List<Investor> result = investorRepository.findByAccountIdAndStatus(accountId, "ACTIVE");

        // Then: ACTIVE 투자자만 반환된다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDisplayName()).isEqualTo("김철수");
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void findByAccountIdAndStatusReturnsEmptyWhenNoMatch() {
        // Given: 투자자가 저장되어 있지 않은 계정 ID가 존재함

        // When: 존재하지 않는 계정 ID로 findByAccountIdAndStatus 호출
        List<Investor> result = investorRepository.findByAccountIdAndStatus(999999L, "ACTIVE");

        // Then: 빈 목록이 반환된다
        assertThat(result).isEmpty();
    }

    @Test
    void saveTwoInvestorsWithSameDisplayNameAllowed() {
        // Given: 같은 계정에서 동일한 표시 이름의 투자자를 두 명 등록
        // - spec AC-011: 이름 중복 허용 (동명이인 가족 상황 반영)
        // - D-I-004: 유니크 제약 없음

        LocalDateTime now = LocalDateTime.now();
        investorRepository.save(Investor.builder()
                .accountId(accountId)
                .displayName("홍길동")
                .relationship("SELF")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
        investorRepository.save(Investor.builder()
                .accountId(accountId)
                .displayName("홍길동")
                .relationship("CHILD")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());

        // When: 해당 계정 + ACTIVE 상태로 조회
        List<Investor> result = investorRepository.findByAccountIdAndStatus(accountId, "ACTIVE");

        // Then: 중복 이름의 투자자 두 명이 모두 조회된다
        assertThat(result).hasSize(2);
    }
}