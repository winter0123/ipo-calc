package com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount;

import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecuritiesAccountRepository extends JpaRepository<SecuritiesAccount, Long> {

    List<SecuritiesAccount> findByInvestorIdAndStatus(Long investorId, String status);

    List<SecuritiesAccount> findByInvestorId(Long investorId);

    Optional<SecuritiesAccount> findByIdAndDeletedAtIsNull(Long id);

    Optional<SecuritiesAccount> findByInvestorIdAndIsDefaultTrue(Long investorId);
}