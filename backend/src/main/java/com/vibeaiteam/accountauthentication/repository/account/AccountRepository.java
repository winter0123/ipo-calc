package com.vibeaiteam.accountauthentication.repository.account;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 계정 JPA 저장소. */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

  /**
   * 로그인 실패를 원자적으로 기록한다(읽기-수정-쓰기 경합 없음, 커넥션은 이 문장 하나만 쓴다). 잠금이 이미 만료됐으면 횟수를 1부터 다시 세고 잠금을 푼다. 새 횟수가
   * {@code maxAttempts} 이상이면 {@code lockUntil}까지 잠근다.
   */
  @Transactional
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE accounts SET
            failed_login_attempts =
              CASE WHEN locked_until IS NOT NULL AND locked_until <= :now
                   THEN 1 ELSE failed_login_attempts + 1 END,
            locked_until =
              CASE WHEN (CASE WHEN locked_until IS NOT NULL AND locked_until <= :now
                              THEN 1 ELSE failed_login_attempts + 1 END) >= :maxAttempts
                   THEN :lockUntil
                   WHEN locked_until <= :now THEN NULL
                   ELSE locked_until END,
            updated_at = :now
          WHERE id = :id
          """)
  int recordFailedLogin(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("maxAttempts") int maxAttempts,
      @Param("lockUntil") LocalDateTime lockUntil);

  /** 로그인 성공을 기록한다: 실패 횟수·잠금을 비우고 마지막 로그인 시각을 갱신한다. */
  @Transactional
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "UPDATE accounts SET failed_login_attempts = 0, locked_until = NULL,"
              + " last_login_at = :now, updated_at = :now WHERE id = :id")
  int recordLoginSuccess(@Param("id") Long id, @Param("now") LocalDateTime now);

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
  java.util.List<Account> findByApprovedOrderByCreatedAtAsc(boolean approved);

  /**
   * 모든 계정을 가입 순서대로 조회한다.
   *
   * @return 계정 목록
   */
  java.util.List<Account> findAllByOrderByCreatedAtAsc();
}
