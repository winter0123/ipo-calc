package com.ipocalc.admin.controller;

import com.ipocalc.admin.dto.AdminUserDetailResponse;
import com.ipocalc.admin.dto.AdminUserListResponse;
import com.ipocalc.admin.dto.AllocationSummaryResponse;
import com.ipocalc.admin.dto.InvestorSummaryResponse;
import com.ipocalc.admin.dto.SaleSummaryResponse;
import com.ipocalc.admin.dto.SubscriptionSummaryResponse;
import com.ipocalc.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 회원 관리 API. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final AdminUserService adminUserService;

  /**
   * 회원 목록 조회 (페이징, 검색, 정렬 지원).
   *
   * @param pageable 페이지 번호, 크기, 정렬 정보
   * @param search 검색어 (이메일/닉네임/이름 부분 검색)
   * @param role 역할 필터 (USER, ADMIN)
   * @param approved 승인 여부 필터
   * @param locked 잠금 여부 필터
   * @return 페이징된 회원 목록
   */
  @GetMapping
  public ResponseEntity<Page<AdminUserListResponse>> list(
      @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Boolean approved,
      @RequestParam(required = false) Boolean locked) {

    var result =
        adminUserService.getUserList(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            search,
            role,
            approved,
            locked,
            pageable.getSort());

    return ResponseEntity.ok(result);
  }

  /**
   * 회원 상세 조회.
   *
   * @param accountId 회원 계정 ID
   * @return 회원 상세 정보
   */
  @GetMapping("/{accountId}")
  public ResponseEntity<AdminUserDetailResponse> detail(@PathVariable Long accountId) {

    var detail = adminUserService.getUserDetail(accountId);
    return ResponseEntity.ok(detail);
  }

  /** 회원의 투자자 목록 조회. */
  @GetMapping("/{accountId}/investors")
  public ResponseEntity<java.util.List<InvestorSummaryResponse>> getInvestors(
      @PathVariable Long accountId) {

    var investors = adminUserService.getInvestors(accountId);
    return ResponseEntity.ok(investors);
  }

  /** 회원의 청약 내역 조회. */
  @GetMapping("/{accountId}/subscriptions")
  public ResponseEntity<Page<SubscriptionSummaryResponse>> getSubscriptions(
      @PathVariable Long accountId, @PageableDefault(page = 0, size = 20) Pageable pageable) {

    var result = adminUserService.getSubscriptions(accountId, pageable);
    return ResponseEntity.ok(result);
  }

  /** 회원의 배정 내역 조회. */
  @GetMapping("/{accountId}/allocations")
  public ResponseEntity<Page<AllocationSummaryResponse>> getAllocations(
      @PathVariable Long accountId, @PageableDefault(page = 0, size = 20) Pageable pageable) {

    var result = adminUserService.getAllocations(accountId, pageable);
    return ResponseEntity.ok(result);
  }

  /** 회원의 매도 내역 조회. */
  @GetMapping("/{accountId}/sales")
  public ResponseEntity<Page<SaleSummaryResponse>> getSales(
      @PathVariable Long accountId, @PageableDefault(page = 0, size = 20) Pageable pageable) {

    var result = adminUserService.getSales(accountId, pageable);
    return ResponseEntity.ok(result);
  }
}
