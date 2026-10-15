package com.ipocalc.investormanagement.repository.investor;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 투자자 JPA 저장소. */
@Repository
public interface InvestorRepository extends JpaRepository<Investor, Long> {

  /** 계정 ID · 상태 조건으로 투자자를 목록 조회한다. */
  List<Investor> findByAccountIdAndStatus(Long accountId, RecordStatus status);

  /** 계정 ID 조건으로 투자자를 목록 조회한다. */
  List<Investor> findByAccountId(Long accountId);
}
