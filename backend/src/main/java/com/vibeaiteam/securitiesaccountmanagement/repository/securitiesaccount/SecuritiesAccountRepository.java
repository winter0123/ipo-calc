package com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 증권계좌 JPA 저장소. */
@Repository
public interface SecuritiesAccountRepository extends JpaRepository<SecuritiesAccount, Long> {

  /** 투자자 ID · 상태 조건으로 증권계좌를 목록 조회한다. */
  List<SecuritiesAccount> findByInvestorIdAndStatus(Long investorId, RecordStatus status);

  /** 투자자 ID 조건으로 증권계좌를 목록 조회한다. */
  List<SecuritiesAccount> findByInvestorId(Long investorId);

  /** ID 조건(삭제되지 않은 건만)으로 증권계좌를 단건 조회한다. */
  Optional<SecuritiesAccount> findByIdAndDeletedAtIsNull(Long id);

  /** 투자자 ID · 기본 계좌 여부 조건으로 증권계좌를 단건 조회한다. */
  Optional<SecuritiesAccount> findByInvestorIdAndIsDefaultTrue(Long investorId);

  /** 투자자의 활성 계좌를 오래된 순으로 조회한다. */
  List<SecuritiesAccount> findByInvestorIdAndStatusOrderByCreatedAtAscIdAsc(
      Long investorId, RecordStatus status);

  /**
   * 계좌의 API 인증정보를 물리 삭제한다. brokerintegration이 이 모듈에 의존하므로 순환을 피하려 네이티브 쿼리를 쓴다.
   *
   * @param securitiesAccountId 증권계좌 ID
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM securities_account_api_credentials WHERE securities_account_id = :id",
      nativeQuery = true)
  void deleteApiCredentials(@Param("id") Long securitiesAccountId);
}
