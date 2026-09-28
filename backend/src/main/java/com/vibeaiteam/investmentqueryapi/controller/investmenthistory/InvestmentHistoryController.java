package com.vibeaiteam.investmentqueryapi.controller.investmenthistory;

import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.service.investmenthistory.InvestmentHistoryQueryService;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/investment-history")
public class InvestmentHistoryController {

    private final InvestmentHistoryQueryService queryService;

    public InvestmentHistoryController(InvestmentHistoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<Page<InvestmentHistoryDto>> getHistory(
            @RequestParam(required = false) Long investorId,
            @RequestParam(required = false) Long securitiesAccountId,
            @RequestParam(required = false) Long ipoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            throw new InvestmentQueryValidationException("size는 최대 100까지 허용됩니다.");
        }
        if (page < 0) {
            throw new InvestmentQueryValidationException("page는 0 이상이어야 합니다.");
        }

        Page<InvestmentHistoryDto> pageResult = queryService.getHistory(investorId, securitiesAccountId, ipoId, page, size);
        return ResponseEntity.ok(pageResult);
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<InvestmentHistoryDto> getHistoryDetail(@PathVariable Long subscriptionId) {
        InvestmentHistoryDto dto = queryService.getHistoryDetail(subscriptionId);
        return ResponseEntity.ok(dto);
    }
}