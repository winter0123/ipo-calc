package com.ipocalc.investormanagement.controller.investor;

import com.ipocalc.accountauthentication.controller.dto.SuccessResponse;
import com.ipocalc.investormanagement.controller.investor.dto.InvestorRegisterRequest;
import com.ipocalc.investormanagement.controller.investor.dto.InvestorResponse;
import com.ipocalc.investormanagement.controller.investor.dto.InvestorUpdateRequest;
import com.ipocalc.investormanagement.service.investor.InvestorService;
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

/** 투자자에 대한 등록/조회/수정/비활성화 API를 제공한다. */
@RestController
@RequestMapping("/investors")
@RequiredArgsConstructor
public class InvestorController {

  private final InvestorService investorService;

  /** 투자자 등록 요청을 받아 현재 계정에 투자자를 생성하고, 201 Created와 함께 생성된 투자자 정보를 반환한다. */
  @PostMapping
  public ResponseEntity<InvestorResponse> register(@RequestBody InvestorRegisterRequest request) {
    InvestorResponse response = investorService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** 현재 계정이 소유한 투자자 목록을 조회한다. status 파라미터(ACTIVE/INACTIVE/BOTH)로 필터링한다. */
  @GetMapping
  public ResponseEntity<List<InvestorResponse>> list(
      @RequestParam(value = "status", required = false) String status) {
    return ResponseEntity.ok(investorService.list(status));
  }

  /** 투자자 ID로 단건 투자자 정보를 조회한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403을 반환한다. */
  @GetMapping("/{id}")
  public ResponseEntity<InvestorResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(investorService.getById(id));
  }

  /** 투자자 ID로 해당 투자자의 이름/관계 등을 수정하고, 수정된 투자자 정보를 반환한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403을 반환한다. */
  @PatchMapping("/{id}")
  public ResponseEntity<InvestorResponse> update(
      @PathVariable Long id, @RequestBody InvestorUpdateRequest request) {
    return ResponseEntity.ok(investorService.update(id, request));
  }

  /** 투자자 ID로 해당 투자자를 비활성화 상태로 전환하고, 비활성화 완료 메시지를 반환한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403을 반환한다. */
  @DeleteMapping("/{id}")
  public ResponseEntity<SuccessResponse> delete(@PathVariable Long id) {
    return ResponseEntity.ok(investorService.deactivate(id));
  }
}
