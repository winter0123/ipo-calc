package com.vibeaiteam.salemanagement.service.sale;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleResponse;
import com.vibeaiteam.salemanagement.controller.sale.exception.SaleAllocationExceededException;
import com.vibeaiteam.salemanagement.controller.sale.exception.SaleNotFoundException;
import com.vibeaiteam.salemanagement.controller.sale.exception.SaleValidationException;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SaleService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SaleRepository saleRepository;
    private final AllocationRepository allocationRepository;

    @Transactional
    public SaleResponse register(SaleRegisterRequest request) {
        validateRequest(request);

        // (1) Allocation 조회 — 존재하지 않으면 404
        Allocation allocation = allocationRepository.findByIdAndDeletedAtIsNull(request.getAllocationId())
                .orElseThrow(() -> new SaleNotFoundException("배정 기록을 찾을 수 없습니다."));

        // (2) 기존 ACTIVE Sale들의 quantity 합계 조회
        Long existingQuantitySum = saleRepository.sumActiveQuantityByAllocationId(allocation.getId());
        if (existingQuantitySum == null) {
            existingQuantitySum = 0L;
        }

        // (3) 누적 매도수량 검증: (기존 합계 + 신규 quantity) ≤ allocatedQuantity
        Long newQuantity = request.getQuantity();
        if (existingQuantitySum + newQuantity > allocation.getAllocatedQuantity()) {
            throw new SaleAllocationExceededException(
                    "누적 매도수량이 배정수량을 초과합니다. 기존 합계: " + existingQuantitySum +
                            ", 신규 수량: " + newQuantity + ", 배정수량: " + allocation.getAllocatedQuantity());
        }

        LocalDateTime now = LocalDateTime.now();
        Sale sale = Sale.builder()
                .allocationId(allocation.getId())
                .quantity(newQuantity)
                .price(request.getPrice())
                .amount(request.getAmount())
                .saleDate(request.getSaleDate())
                .fee(request.getFee())
                .tax(request.getTax())
                .otherCost(request.getOtherCost())
                .status(STATUS_ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Sale saved = saleRepository.save(sale);
        return SaleResponse.from(saved);
    }

    private void validateRequest(SaleRegisterRequest request) {
        if (request == null) {
            throw new SaleValidationException("매도 등록 정보는 필수입니다.");
        }
        if (request.getAllocationId() == null) {
            throw new SaleValidationException("allocationId는 필수입니다.");
        }
        if (request.getQuantity() == null) {
            throw new SaleValidationException("quantity는 필수입니다.");
        }
        if (request.getSaleDate() == null) {
            throw new SaleValidationException("saleDate는 필수입니다.");
        }
        // AC-003: quantity는 0 초과여야 함
        if (request.getQuantity() <= 0) {
            throw new SaleValidationException("quantity는 0 초과여야 합니다.");
        }
        // price, amount, fee, tax, otherCost가 제공된 경우 0 이상 검증
        if (request.getPrice() != null && request.getPrice() < 0) {
            throw new SaleValidationException("price는 0 이상이어야 합니다.");
        }
        if (request.getAmount() != null && request.getAmount() < 0) {
            throw new SaleValidationException("amount는 0 이상이어야 합니다.");
        }
        if (request.getFee() != null && request.getFee() < 0) {
            throw new SaleValidationException("fee는 0 이상이어야 합니다.");
        }
        if (request.getTax() != null && request.getTax() < 0) {
            throw new SaleValidationException("tax는 0 이상이어야 합니다.");
        }
        if (request.getOtherCost() != null && request.getOtherCost() < 0) {
            throw new SaleValidationException("otherCost는 0 이상이어야 합니다.");
        }
    }
}