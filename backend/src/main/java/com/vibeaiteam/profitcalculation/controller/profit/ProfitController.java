package com.vibeaiteam.profitcalculation.controller.profit;

import com.vibeaiteam.profitcalculation.dto.profit.InvestorProfitDto;
import com.vibeaiteam.profitcalculation.dto.profit.ProfitDto;
import com.vibeaiteam.profitcalculation.service.profit.ProfitCalculationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수익 계산 API를 제공한다. 단일 매도건 및 투자자별 전체 수익을 조회 시점에 계산해 반환한다. 계산 결과는 데이터베이스에 저장하지 않는 파생값이다 (D-P-001,
 * D-P-006).
 */
@RestController
@RequiredArgsConstructor
public class ProfitController {

  private final ProfitCalculationService profitCalculationService;

  /** 특정 매도건의 순수익을 계산해 반환한다. CANCELLED 상태의 Sale이나 존재하지 않는 Sale이면 404를 반환한다. */
  @GetMapping("/sales/{saleId}/profit")
  public ResponseEntity<ProfitDto> getSaleProfit(@PathVariable Long saleId) {
    ProfitDto profit = profitCalculationService.calculateForSale(saleId);
    return ResponseEntity.ok(profit);
  }

  /**
   * 투자자의 ACTIVE 매도건 수익 목록과 합계를 계산해 반환한다. from·to를 주면 매도일(saleDate)의 날짜가 범위에 포함되는 매도만 반환하며 양 끝은
   * 포함한다. 존재하지 않는 Investor면 404, 타 계정 소유면 403, from이 to보다 늦으면 400을 반환한다.
   *
   * @param investorId 투자자 ID
   * @param from 조회 시작일(ISO 날짜, 생략 시 하한 없음)
   * @param to 조회 종료일(ISO 날짜, 생략 시 상한 없음)
   */
  @GetMapping("/investors/{investorId}/profits")
  public ResponseEntity<InvestorProfitDto> getInvestorProfits(
      @PathVariable Long investorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    InvestorProfitDto profits = profitCalculationService.calculateForInvestor(investorId, from, to);
    return ResponseEntity.ok(profits);
  }
}
