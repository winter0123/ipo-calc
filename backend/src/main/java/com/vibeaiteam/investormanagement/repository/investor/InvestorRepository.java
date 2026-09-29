package com.vibeaiteam.investormanagement.repository.investor;

import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestorRepository extends JpaRepository<Investor, Long> {

  List<Investor> findByAccountIdAndStatus(Long accountId, String status);

  List<Investor> findByAccountId(Long accountId);
}
