package com.ipocalc.brokerintegration.controller.sync;

import com.ipocalc.brokerintegration.controller.sync.dto.SyncConfirmRequest;
import com.ipocalc.brokerintegration.controller.sync.dto.SyncPreviewResponse;
import com.ipocalc.brokerintegration.service.sync.BrokerSyncService;
import com.ipocalc.salemanagement.controller.sale.dto.SaleResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 증권사 조회 결과로 매도 기록을 채우는 미리보기·확정 API.
 *
 * <p>자동 등록이 아니라 자동 채움이다. 미리보기는 부작용이 없고, 확정한 건만 기존 매도 등록 로직으로 저장한다.
 */
@RestController
@RequestMapping("/securities-accounts/{securitiesAccountId}/broker-sync")
@RequiredArgsConstructor
public class BrokerSyncController {

  private final BrokerSyncService brokerSyncService;

  /**
   * 증권사 매도 내역을 조회해 우리 배정과 매칭한 결과를 돌려준다(저장 없음). {@code from}·{@code to}(yyyy-MM-dd)를 주지 않으면 최근 1년이고,
   * 너무 잦은 호출은 429다.
   */
  @GetMapping("/preview")
  public ResponseEntity<SyncPreviewResponse> preview(
      @PathVariable Long securitiesAccountId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(brokerSyncService.preview(securitiesAccountId, from, to));
  }

  /** 사용자가 확정한 매도만 기존 매도 등록 로직으로 저장하고 201과 저장된 매도 목록을 돌려준다. */
  @PostMapping("/confirm")
  public ResponseEntity<List<SaleResponse>> confirm(
      @PathVariable Long securitiesAccountId, @RequestBody SyncConfirmRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(brokerSyncService.confirm(securitiesAccountId, request));
  }
}
