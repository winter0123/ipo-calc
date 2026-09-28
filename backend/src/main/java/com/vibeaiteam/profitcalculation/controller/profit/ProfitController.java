package com.vibeaiteam.profitcalculation.controller.profit;

import com.vibeaiteam.profitcalculation.dto.profit.InvestorProfitDto;
import com.vibeaiteam.profitcalculation.dto.profit.ProfitDto;
import com.vibeaiteam.profitcalculation.service.profit.ProfitCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 수익 계산 API를 제공한다. 단일 매도건 및 투자자별 전체 수익을 조회 시점에 계산해 반환한다.
// 계산 결과는 데이터베이스에 저장하지 않는 파생값이다 (D-P-001, D-P-006).
@RestController
@RequiredArgsConstructor
public class ProfitController {

    private final ProfitCalculationService profitCalculationService;

    // 특정 매도건의 순수익을 계산해 반환한다.
    // CANCELLED 상태의 Sale이나 존재하지 않는 Sale이면 404를 반환한다.
    @GetMapping("/sales/{saleId}/profit")
    public ResponseEntity<ProfitDto> getSaleProfit(@PathVariable Long saleId) {
        ProfitDto profit = profitCalculationService.calculateForSale(saleId);
        return ResponseEntity.ok(profit);
    }

    // 투자자의 모든 ACTIVE 매도건 수익 목록과 합계를 계산해 반환한다.
    // 존재하지 않는 Investor면 404를 반환하고, 매도 건이 없으면 빈 목록과 합계 0을 반환한다.
    @GetMapping("/investors/{investorId}/profits")
    public ResponseEntity<InvestorProfitDto> getInvestorProfits(@PathVariable Long investorId) {
        InvestorProfitDto profits = profitCalculationService.calculateForInvestor(investorId);
        return ResponseEntity.ok(profits);
    }
}