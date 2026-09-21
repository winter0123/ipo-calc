package com.vibeaiteam.subscriptionmanagement.controller.subscription;

import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionRegisterRequest;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionResponse;
import com.vibeaiteam.subscriptionmanagement.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}