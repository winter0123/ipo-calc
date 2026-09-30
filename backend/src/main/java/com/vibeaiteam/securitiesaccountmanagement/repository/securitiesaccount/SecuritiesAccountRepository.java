package com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 증권계좌 JPA 저장소.
 */
@Repository
public interface SecuritiesAccountRepository extends JpaRepository<SecuritiesAccount, Long> {

  /**
   * 투자자 ID · 상태 조건으로 증권계좌를 목록 조회한다.
   */
  List<SecuritiesAccount> findByInvestorIdAndStatus(Long investorId, RecordStatus status);

  /**
   * 투자자 ID 조건으로 증권계좌를 목록 조회한다.
   */
  List<SecuritiesAccount> findByInvestorId(Long investorId);

  /**
   * ID 조건(삭제되지 않은 건만)으로 증권계좌를 단건 조회한다.
   */
  Optional<SecuritiesAccount> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 투자자 ID · 기본 계좌 여부 조건으로 증권계좌를 단건 조회한다.
   */
  Optional<SecuritiesAccount> findByInvestorIdAndIsDefaultTrue(Long investorId);
}
