package com.vibeaiteam.salemanagement.service.sale;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleResponse;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleUpdateRequest;
import com.vibeaiteam.salemanagement.controller.sale.exception.SaleAllocationExceededException;
import com.vibeaiteam.salemanagement.controller.sale.exception.SaleNotFoundException;
import com.vibeaiteam.salemanagement.controller.sale.exception.SaleValidationException;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    Allocation allocation =
        allocationRepository
            .findByIdAndDeletedAtIsNull(request.getAllocationId())
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
          "누적 매도수량이 배정수량을 초과합니다. 기존 합계: "
              + existingQuantitySum
              + ", 신규 수량: "
              + newQuantity
              + ", 배정수량: "
              + allocation.getAllocatedQuantity());
    }

    LocalDateTime now = LocalDateTime.now();
    Sale sale =
        Sale.builder()
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

  @Transactional(readOnly = true)
  public List<SaleResponse> list(Long allocationId, Long investorId, String investorName) {
    List<Sale> sales = saleRepository.findActiveWithFilters(allocationId, investorId, investorName);
    return sales.stream().map(SaleResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public SaleResponse getById(Long id) {
    Sale sale =
        saleRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SaleNotFoundException("매도 기록을 찾을 수 없습니다."));
    return SaleResponse.from(sale);
  }

  // SF-4: 매도 기록을 부분 수정한다. 시점/필드 제약 없이 전체 허용(D-SL-003).
  // 제공된 필드만 갱신하며, 누적 매도수량 재검증을 수행한다.
  // (1) 대상 Sale 조회 (CANCELLED면 404)
  // (2) 참조하는 Allocation의 allocatedQuantity 확인
  // (3) 기존 ACTIVE Sale들의 quantity 합계 조회 (자기 자신은 제외)
  // (4) (기존 합계 - 자기자신 quantity + 수정 quantity) ≤ allocatedQuantity 검증
  @Transactional
  public SaleResponse update(Long id, SaleUpdateRequest request) {
    validateUpdateRequest(request);

    // (1) 대상 Sale 조회 — 없거나 CANCELLED이면 404
    Sale sale =
        saleRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SaleNotFoundException("매도 기록을 찾을 수 없습니다."));

    // (2) Allocation 조회
    Allocation allocation =
        allocationRepository
            .findByIdAndDeletedAtIsNull(sale.getAllocationId())
            .orElseThrow(() -> new SaleNotFoundException("배정 기록을 찾을 수 없습니다."));

    // (3) 기존 ACTIVE Sale들의 quantity 합계 조회 (자기 자신 제외)
    Long existingQuantitySum =
        saleRepository.sumActiveQuantityByAllocationIdExcludingId(allocation.getId(), id);
    if (existingQuantitySum == null) {
      existingQuantitySum = 0L;
    }

    // (4) 누적 매도수량 재검증: (기존 합계(자기자신 제외) + 수정 quantity) ≤ allocatedQuantity
    Long newQuantity = request.getQuantity() != null ? request.getQuantity() : sale.getQuantity();
    if (existingQuantitySum + newQuantity > allocation.getAllocatedQuantity()) {
      throw new SaleAllocationExceededException(
          "누적 매도수량이 배정수량을 초과합니다. 기존 합계(자기자신 제외): "
              + existingQuantitySum
              + ", 수정 수량: "
              + newQuantity
              + ", 배정수량: "
              + allocation.getAllocatedQuantity());
    }

    // 필드 업데이트 (제공된 필드만)
    if (request.getQuantity() != null) {
      sale.setQuantity(request.getQuantity());
    }
    if (request.getPrice() != null) {
      sale.setPrice(request.getPrice());
    }
    if (request.getAmount() != null) {
      sale.setAmount(request.getAmount());
    }
    if (request.getSaleDate() != null) {
      sale.setSaleDate(request.getSaleDate());
    }
    if (request.getFee() != null) {
      sale.setFee(request.getFee());
    }
    if (request.getTax() != null) {
      sale.setTax(request.getTax());
    }
    if (request.getOtherCost() != null) {
      sale.setOtherCost(request.getOtherCost());
    }

    sale.setUpdatedAt(LocalDateTime.now());
    Sale saved = saleRepository.save(sale);
    return SaleResponse.from(saved);
  }

  private void validateUpdateRequest(SaleUpdateRequest request) {
    if (request == null) {
      throw new SaleValidationException("매도 수정 정보는 필수입니다.");
    }
    // quantity가 제공된 경우 0 초과 검증
    if (request.getQuantity() != null && request.getQuantity() <= 0) {
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

  // SF-5: 매도 기록을 논리 취소한다 (AllocationService.cancel과 동일 패턴).
  // status를 CANCELLED로 변경하고 deletedAt/updatedAt을 갱신하며, 물리 삭제는 수행하지 않는다(AC-016).
  // 존재하지 않거나 이미 취소된 기록은 findByIdAndDeletedAtIsNull이 걸러내 404를 반환한다(AC-017, 재취소는 자연 배제).
  @Transactional
  public void cancel(Long id) {
    Sale sale =
        saleRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SaleNotFoundException("매도 기록을 찾을 수 없습니다."));
    LocalDateTime now = LocalDateTime.now();
    sale.setStatus("CANCELLED");
    sale.setDeletedAt(now);
    sale.setUpdatedAt(now);
    saleRepository.save(sale);
  }
}
