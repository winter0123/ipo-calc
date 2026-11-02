package com.ipocalc.accountauthentication.repository.account;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** 계정 JPA 저장소. */
@Repository
public interface AccountRepository
    extends JpaRepository<Account, Long>,
        JpaSpecificationExecutor<Account>,
        AccountMutationRepository {

  /** 이메일 조건으로 계정을 목록 조회한다. */
  Account findByEmail(String email);

  /** 이메일 조건에 맞는 계정이(가) 존재하는지 확인한다. */
  boolean existsByEmail(String email);

  /**
   * 승인 여부로 계정을 가입 순서대로 조회한다.
   *
   * @param approved 승인 여부
   * @return 계정 목록
   */
  List<Account> findByApprovedOrderByCreatedAtAsc(boolean approved);

  /** ID로 계정을 조회하며 승인된 계정만 조회한다. */
  Optional<Account> findByIdAndApprovedTrue(Long id);

  /** 모든 계정을 가입 순서대로 조회한다. */
  List<Account> findAllByOrderByCreatedAtAsc();
}
