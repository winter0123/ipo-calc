package com.vibeaiteam.investormanagement.repository.investor;

import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorRepository extends JpaRepository<Investor, Long> {

    List<Investor> findByAccountIdAndStatus(Long accountId, String status);
}