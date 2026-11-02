package com.ipocalc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.admin.dto.AdminUserDetailResponse;
import com.ipocalc.admin.dto.AdminUserListResponse;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 회원 관리 서비스 테스트. */
@SpringBootTest
@Transactional
class AdminUserServiceTest {

  @Autowired private AdminUserService adminUserService;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  /** 회원 목록 조회 - 기본 동작 확인. */
  @Test
  void getUserListReturnsResults() {
    Account account = createAccount();

    var result =
        adminUserService.getUserList(
            0, 10, null, null, null, null, Sort.by(Sort.Direction.DESC, "createdAt"));

    assertThat(result.getContent())
        .extracting(AdminUserListResponse::getId)
        .contains(account.getId());
  }

  /** 회원 상세 조회 - 직접 만든 계정. */
  @Test
  void getUserDetailReturnsDetail() {
    Account account = createAccount();

    AdminUserDetailResponse detail = adminUserService.getUserDetail(account.getId());

    assertThat(detail).isNotNull();
    assertThat(detail.getId()).isEqualTo(account.getId());
    assertThat(detail.getEmail()).isEqualTo(account.getEmail());
  }

  /** 회원 투자자 목록은 해당 계정의 투자자를 반환한다. */
  @Test
  void getInvestorsReturnsInvestors() {
    Account account = createAccount();
    LocalDateTime now = LocalDateTime.now();
    Investor investor =
        investorRepository.saveAndFlush(
            Investor.builder()
                .accountId(account.getId())
                .displayName("테스트 투자자")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());

    var investors = adminUserService.getInvestors(account.getId());

    assertThat(investors).extracting("id").containsExactly(investor.getId());
  }

  /** 페이지 크기 제한 - 101 이상은 예외. */
  @Test
  void getUserListInvalidSizeThrowsException() {
    try {
      adminUserService.getUserList(
          0, 101, null, null, null, null, Sort.by(Sort.Direction.DESC, "createdAt"));
      org.junit.jupiter.api.Assertions.fail("페이지 크기 101 이상은 예외가 발생해야 함");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("1~100");
    }
  }

  /** 잘못된 정렬 필드 - 예외. */
  @Test
  void getUserListInvalidSortThrowsException() {
    try {
      adminUserService.getUserList(
          0, 10, null, null, null, null, Sort.by(Sort.Direction.DESC, "invalidField"));
      org.junit.jupiter.api.Assertions.fail("잘못된 정렬 필드는 예외가 발생해야 함");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("정렬 필드");
    }
  }

  /** 잘못된 역할 값 - 예외. */
  @Test
  void getUserListInvalidRoleThrowsException() {
    try {
      adminUserService.getUserList(
          0, 10, null, "INVALID_ROLE", null, null, Sort.by(Sort.Direction.DESC, "createdAt"));
      org.junit.jupiter.api.Assertions.fail("잘못된 역할 값은 예외가 발생해야 함");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("역할 값");
    }
  }

  private Account createAccount() {
    LocalDateTime now = LocalDateTime.now();
    return accountRepository.saveAndFlush(
        Account.builder()
            .email("admin-user-test-" + UUID.randomUUID() + "@example.com")
            .passwordHash("test-password-hash")
            .name("테스트 회원")
            .createdAt(now)
            .updatedAt(now)
            .build());
  }
}
