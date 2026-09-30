package com.vibeaiteam.salemanagement.controller.sale;

import com.vibeaiteam.salemanagement.controller.sale.dto.SaleRegisterRequest;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleResponse;
import com.vibeaiteam.salemanagement.controller.sale.dto.SaleUpdateRequest;
import com.vibeaiteam.salemanagement.service.sale.SaleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분할 매도 기록(Sale)에 대한 등록/조회 API를 제공한다. Sale은 Allocation을 FK로 참조하며,
 * Allocation과 동일한 글로벌 기준정보로 취급되어 계정 소유권 검증을 적용하지 않는다(architecture.md 명시).
 */
@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {

  private final SaleService saleService;

  /**
   * 매도 등록 요청을 받아 새 매도 기록을 생성하고, 201 Created와 함께 생성된 매도 정보를 반환한다.
   * Allocation이 존재하지 않으면 404, quantity가 0 이하면 400, 누적 매도수량이 배정수량을 초과하면 400을 반환한다.
   * 분할 매도 지원: 하나의 Allocation에 여러 건의 Sale 등록이 가능하며, 각 등록 시 기존 ACTIVE 매도량 합계를 재계산해 검증한다.
   */
  @PostMapping
  public ResponseEntity<SaleResponse> register(@RequestBody SaleRegisterRequest request) {
    SaleResponse response = saleService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 매도 목록을 조회한다. deletedAt이 NULL인 ACTIVE 기록만 반환하며, allocationId, investorId, investorName
   * 쿼리 파라미터로 필터링 가능하다. investorId/investorName 필터는 Sale→Allocation→Subscription→Investor
   * 3단계 JOIN으로 수행한다. 필터 미지정 시 전체 목록을 saleDate 내림차순, createdAt 내림차순으로 반환한다.
   */
  @GetMapping
  public ResponseEntity<List<SaleResponse>> list(
      @RequestParam(required = false) Long allocationId,
      @RequestParam(required = false) Long investorId,
      @RequestParam(required = false) String investorName) {
    List<SaleResponse> response = saleService.list(allocationId, investorId, investorName);
    return ResponseEntity.ok(response);
  }

  /**
   * 지정한 ID의 매도 상세 정보를 조회한다. deletedAt이 NULL인 ACTIVE 기록만 조회되며,
   * 존재하지 않거나 취소된 매도는 404를 반환한다.
   */
  @GetMapping("/{id}")
  public ResponseEntity<SaleResponse> getById(@PathVariable Long id) {
    SaleResponse response = saleService.getById(id);
    return ResponseEntity.ok(response);
  }

  /**
   * 매도 기록을 부분 수정한다. 제공된 필드만 갱신하며(시점/필드 제약 없음 — D-SL-003),
   * 누적 매도수량 재검증을 수행한다(자기 자신 제외). 존재하지 않거나 취소된 매도는 404를 반환한다.
   */
  @PatchMapping("/{id}")
  public ResponseEntity<SaleResponse> update(
      @PathVariable Long id, @RequestBody SaleUpdateRequest request) {
    SaleResponse response = saleService.update(id, request);
    return ResponseEntity.ok(response);
  }

  /**
   * 매도 기록을 논리 취소한다 (AllocationController.cancel과 동일 패턴).
   * status를 CANCELLED로 변경하고 deletedAt을 설정하며, 204 No Content를 반환한다(AC-016).
   * 존재하지 않는 ID는 404를 반환한다(AC-017). 물리 삭제는 수행하지 않는다.
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancel(@PathVariable Long id) {
    saleService.cancel(id);
    return ResponseEntity.noContent().build();
  }
}
