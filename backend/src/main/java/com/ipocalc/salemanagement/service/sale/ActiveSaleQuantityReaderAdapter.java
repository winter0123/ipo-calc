package com.ipocalc.salemanagement.service.sale;

import com.ipocalc.allocationmanagement.service.allocation.ActiveSaleQuantityReader;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 매도 저장소 조회를 배정 도메인 포트에 연결한다. */
@Component
@RequiredArgsConstructor
public class ActiveSaleQuantityReaderAdapter implements ActiveSaleQuantityReader {
  private final SaleRepository saleRepository;

  @Override
  public long sumActiveSaleQuantity(Long allocationId) {
    return saleRepository.sumActiveSaleQuantity(allocationId);
  }
}
