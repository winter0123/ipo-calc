package com.ipocalc.salemanagement.repository.sale;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import java.util.List;

/** 매도 도메인의 조인·집계 QueryDSL 저장소. */
public interface SaleQueryRepository {
  /** 배정에 연결된 활성 매도 수량을 합산한다. */
  long sumActiveSaleQuantity(Long allocationId);

  /** 배정별 활성 매도 수량 합계를 반환한다. */
  Long sumActiveQuantityByAllocationId(Long allocationId);

  /** 지정한 매도를 제외한 배정별 활성 매도 수량 합계를 반환한다. */
  Long sumActiveQuantityByAllocationIdExcludingId(Long allocationId, Long excludeId);

  /** 투자자 계정의 상태별 매도 내역을 조회한다. */
  List<Sale> findAllByStatusAndInvestorAccountId(RecordStatus status, Long accountId);

  /** 계정과 선택 필터에 해당하는 활성 매도 내역을 조회한다. */
  List<Sale> findActiveWithFilters(
      Long accountId, Long allocationId, Long investorId, String investorName);
}
