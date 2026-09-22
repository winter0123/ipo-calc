package com.vibeaiteam.salemanagement.controller.sale;

import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleResponse;
import com.vibeaiteam.salemanagement.service.sale.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 분할 매도 기록(Sale)에 대한 등록 API를 제공한다. Sale은 Allocation을 FK로 참조하며,
// Allocation과 동일한 글로벌 기준정보로 취급되어 계정 소유권 검증을 적용하지 않는다(architecture.md 명시).
@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    // 매도 등록 요청을 받아 새 매도 기록을 생성하고, 201 Created와 함께 생성된 매도 정보를 반환한다.
    // Allocation이 존재하지 않으면 404, quantity가 0 이하면 400, 누적 매도수량이 배정수량을 초과하면 400을 반환한다.
    // 분할 매도 지원: 하나의 Allocation에 여러 건의 Sale 등록이 가능하며, 각 등록 시 기존 ACTIVE 매도량 합계를 재계산해 검증한다.
    @PostMapping
    public ResponseEntity<SaleResponse> register(@RequestBody SaleRegisterRequest request) {
        SaleResponse response = saleService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}