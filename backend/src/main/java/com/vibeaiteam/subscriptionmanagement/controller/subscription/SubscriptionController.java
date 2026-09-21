package com.vibeaiteam.subscriptionmanagement.controller.subscription;

import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionRegisterRequest;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionResponse;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionUpdateRequest;
import com.vibeaiteam.subscriptionmanagement.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 청약(Subscription) 기록에 대한 등록/목록조회/상세조회/수정/취소 API를 제공한다. 실시간 청약 접수가 아니라
// 이미 증권사를 통해 완료된 청약 결과를 사용자가 사후에 입력·기록·조회하는 기능이다.
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // 청약 등록 요청을 받아 새 청약 기록을 생성하고, 201 Created와 함께 생성된 청약 정보를 반환한다.
    // 투자자/증권계좌/IPO 참조가 존재하지 않으면 404, 투자자/증권계좌가 현재 계정 소유가 아니면 403,
    // 동일 조합의 ACTIVE 기록이 이미 있으면 409를 반환한다.
    @PostMapping
    public ResponseEntity<SubscriptionResponse> register(@RequestBody SubscriptionRegisterRequest request) {
        SubscriptionResponse response = subscriptionService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 삭제되지 않은 청약 목록을 조회한다. investorId/securitiesAccountId 쿼리 파라미터로 필터링할 수 있고
    // (둘 다 비면 전체), 200 OK와 함께 ACTIVE 청약 목록을 반환한다. 취소된 기록은 포함하지 않는다.
    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> list(
            @RequestParam(required = false) Long investorId,
            @RequestParam(required = false) Long securitiesAccountId) {
        List<SubscriptionResponse> responses = subscriptionService.list(investorId, securitiesAccountId);
        return ResponseEntity.ok(responses);
    }

    // 청약 ID로 단건 청약 정보를 조회한다. 존재하지 않거나 취소된 기록이면 404를 반환한다.
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getById(@PathVariable Long id) {
        SubscriptionResponse response = subscriptionService.getById(id);
        return ResponseEntity.ok(response);
    }

    // 청약 ID로 기록을 부분 수정한다. 요청에 담긴 필드만 갱신하고(미제공 필드는 기존값 유지), 200 OK와 함께
    // 수정된 청약 정보를 반환한다. 존재하지 않거나 이미 취소된 기록이면 404, 새로 제공된 투자자/증권계좌가
    // 현재 계정 소유가 아니면 403, 최종 조합이 자기 자신을 제외한 다른 ACTIVE 기록과 중복되면 409를 반환한다.
    @PatchMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> update(
            @PathVariable Long id, @RequestBody SubscriptionUpdateRequest request) {
        SubscriptionResponse response = subscriptionService.update(id, request);
        return ResponseEntity.ok(response);
    }
}