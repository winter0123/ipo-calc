package com.vibeaiteam.brokerintegration.controller.sync.dto;

import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import java.util.List;

/**
 * 사용자가 확정한 매도 목록. 미리보기 값을 고친 채로 보낼 수 있으며, 기존 매도 등록 요청을 그대로 쓴다.
 *
 * @param sales 확정할 매도 등록 요청 목록
 */
public record SyncConfirmRequest(List<SaleRegisterRequest> sales) {}
