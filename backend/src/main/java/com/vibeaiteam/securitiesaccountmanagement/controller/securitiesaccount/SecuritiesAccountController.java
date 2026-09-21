package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount;

import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountAccountNumberResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountUpdateRequest;
import com.vibeaiteam.securitiesaccountmanagement.service.securitiesaccount.SecuritiesAccountService;
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

import java.util.List;

// 증권계좌에 대한 등록/조회/계좌번호조회/수정/비활성화/기본계좌설정 API를 제공한다.
@RestController
@RequestMapping("/securities-accounts")
@RequiredArgsConstructor
public class SecuritiesAccountController {

    private final SecuritiesAccountService securitiesAccountService;

    // 증권계좌 등록 요청을 받아 새 증권계좌를 생성하고, 201 Created와 함께 생성된 계좌 정보를 반환한다.
    @PostMapping
    public ResponseEntity<SecuritiesAccountResponse> register(@RequestBody SecuritiesAccountRegisterRequest request) {
        SecuritiesAccountResponse response = securitiesAccountService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 현재 계정이 소유한 증권계좌 목록을 조회한다. investorId/status 파라미터로 필터링하고 계좌번호는 마스킹해 반환한다.
    @GetMapping
    public ResponseEntity<List<SecuritiesAccountResponse>> list(
            @RequestParam(required = false) Long investorId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(securitiesAccountService.list(investorId, status));
    }

    // 증권계좌 ID로 단건 계좌 정보를 조회한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403을 반환한다.
    @GetMapping("/{id}")
    public ResponseEntity<SecuritiesAccountResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesAccountService.getById(id));
    }

    // 증권계좌 ID로 원본 계좌번호를 복호화해 반환한다. 계좌번호 노출 시점을 감사 로그에 기록한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403을 반환한다.
    @GetMapping("/{id}/account-number")
    public ResponseEntity<SecuritiesAccountAccountNumberResponse> getAccountNumber(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesAccountService.getAccountNumber(id));
    }

    // 증권계좌 ID로 해당 계좌의 계좌명/증권사 등을 수정하고, 수정된 계좌 정보를 반환한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403, 존재하지 않거나 비활성화된 증권사로 변경 시도하면 400을 반환한다.
    @PatchMapping("/{id}")
    public ResponseEntity<SecuritiesAccountResponse> update(
            @PathVariable Long id, @RequestBody SecuritiesAccountUpdateRequest request) {
        return ResponseEntity.ok(securitiesAccountService.update(id, request));
    }

    // 증권계좌 ID로 해당 계좌를 비활성화 상태로 전환하고, 204 No Content를 반환한다. 기본 계좌였다면 기본 계좌 지정도 함께 해제한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403을 반환한다.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        securitiesAccountService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // 증권계좌 ID로 해당 계좌를 기본 계좌로 지정하고, 갱신된 계좌 정보를 반환한다. 존재하지 않으면 404, 현재 계정 소유가 아니면 403, 비활성화된 계좌면 400, 이미 기본 계좌면 409를 반환한다.
    @PatchMapping("/{id}/set-default")
    public ResponseEntity<SecuritiesAccountResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesAccountService.setDefault(id));
    }
}