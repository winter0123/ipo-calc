package com.ipocalc.investmentqueryapi.controller.investmenthistory;

import com.ipocalc.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.ipocalc.investmentqueryapi.exception.InvestmentQueryValidationException;
import com.ipocalc.investmentqueryapi.service.investmenthistory.InvestmentHistoryQueryService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 투자 이력 조회 API. */
@RestController
@RequestMapping("/investment-history")
public class InvestmentHistoryController {

  private static final int MAX_PAGE = 100_000;

  private final InvestmentHistoryQueryService queryService;

  /** 조회 서비스를 주입받아 생성한다. */
  public InvestmentHistoryController(InvestmentHistoryQueryService queryService) {
    this.queryService = queryService;
  }

  /** 투자자·증권계좌·공모주 조건으로 투자 이력을 페이지 단위로 조회한다. */
  @GetMapping
  public ResponseEntity<Page<InvestmentHistoryDto>> getHistory(
      @RequestParam(required = false) Long investorId,
      @RequestParam(required = false) Long securitiesAccountId,
      @RequestParam(required = false) Long ipoId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    if (size < 1 || size > 100) {
      throw new InvestmentQueryValidationException("size는 1 이상 100 이하여야 합니다.");
    }
    if (page < 0 || page > MAX_PAGE) {
      throw new InvestmentQueryValidationException("page는 0 이상 " + MAX_PAGE + " 이하여야 합니다.");
    }

    Page<InvestmentHistoryDto> pageResult =
        queryService.getHistory(investorId, securitiesAccountId, ipoId, page, size);
    return ResponseEntity.ok(pageResult);
  }

  /** 청약 ID로 투자 이력 상세를 조회한다. */
  @GetMapping("/{subscriptionId}")
  public ResponseEntity<InvestmentHistoryDto> getHistoryDetail(@PathVariable Long subscriptionId) {
    InvestmentHistoryDto dto = queryService.getHistoryDetail(subscriptionId);
    return ResponseEntity.ok(dto);
  }
}
