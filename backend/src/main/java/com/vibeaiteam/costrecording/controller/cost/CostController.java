package com.vibeaiteam.costrecording.controller.cost;

import com.vibeaiteam.costrecording.controller.cost.dto.CostRegisterRequest;
import com.vibeaiteam.costrecording.controller.cost.dto.CostResponse;
import com.vibeaiteam.costrecording.service.cost.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 비용(Cost) 기록에 대한 등록 API를 제공한다. 사용자가 청약/취득 단계에서 발생한 비용(청약수수료, 계좌개설비, 이체수수료 등)을
// 사후에 수동으로 입력·기록하는 기능이다. 실시간 비용 처리가 아니라 이미 발생한 비용 결과를 기록했다(사실 기록)는 성격이다.
@RestController
@RequestMapping("/costs")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    // 비용 등록 요청을 받아 새 비용 기록을 생성하고, 201 Created와 함께 생성된 비용 정보를 반환한다.
    // subscriptionId가 존재하지 않거나 취소(CANCELLED)된 청약을 참조하면 404,
    // 참조하는 청약이 현재 계정 소유가 아니면 403,
    // 필수값 누락/amount 0 미만/memo 255자 초과 시 400을 반환한다.
    @PostMapping
    public ResponseEntity<CostResponse> register(@RequestBody CostRegisterRequest request) {
        CostResponse response = costService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}