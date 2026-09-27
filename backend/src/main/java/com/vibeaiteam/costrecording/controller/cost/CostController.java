package com.vibeaiteam.costrecording.controller.cost;

import com.vibeaiteam.costrecording.controller.cost.dto.CostRegisterRequest;
import com.vibeaiteam.costrecording.controller.cost.dto.CostResponse;
import com.vibeaiteam.costrecording.controller.cost.dto.CostUpdateRequest;
import com.vibeaiteam.costrecording.service.cost.CostService;
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

// 비용(Cost) 기록에 대한 등록/목록조회/상세조회/수정 API를 제공한다. 사용자가 청약/취득 단계에서 발생한 비용(청약수수료, 계좌개설비, 이체수수료 등)을
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

    // 삭제되지 않은 비용 목록을 조회한다. subscriptionId, investorId, investorName 쿼리 파라미터로 필터링할 수 있고
    // (셋 다 비면 전체), 200 OK와 함께 ACTIVE 비용 목록을 반환한다. 취소된 기록은 포함하지 않는다.
    // investorId/investorName 필터는 Cost→Subscription→Investor 2단계 JOIN으로 수행한다.
    // 정렬은 incurredAt 내림차순, 동일 incurredAt인 경우 createdAt 내림차순이다.
    @GetMapping
    public ResponseEntity<List<CostResponse>> list(
            @RequestParam(required = false) Long subscriptionId,
            @RequestParam(required = false) Long investorId,
            @RequestParam(required = false) String investorName) {
        List<CostResponse> responses = costService.list(subscriptionId, investorId, investorName);
        return ResponseEntity.ok(responses);
    }

    // 비용 ID로 단건 비용 정보를 조회한다. 존재하지 않거나 취소된 기록이면 404를 반환한다.
    // 조회 시 계정 소유권 검증은 수행하지 않는다.
    @GetMapping("/{id}")
    public ResponseEntity<CostResponse> getById(@PathVariable Long id) {
        CostResponse response = costService.getById(id);
        return ResponseEntity.ok(response);
    }

    // 비용 ID로 기록을 부분 수정한다. 요청에 담긴 필드(costType/amount/incurredAt/memo)만 갱신하고(미제공 필드는 기존값 유지),
    // 200 OK와 함께 수정된 비용 정보를 반환한다. subscriptionId는 요청에 없으므로 소유권/존재 재검증은 하지 않는다.
    // 존재하지 않거나 이미 취소된(CANCELLED) 기록이면 404, amount 0 미만/memo 255자 초과 시 400을 반환한다.
    @PatchMapping("/{id}")
    public ResponseEntity<CostResponse> update(
            @PathVariable Long id, @RequestBody CostUpdateRequest request) {
        CostResponse response = costService.update(id, request);
        return ResponseEntity.ok(response);
    }
}