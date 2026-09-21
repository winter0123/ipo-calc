package com.vibeaiteam.allocationmanagement.controller.allocation;

import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationRegisterRequest;
import com.vibeaiteam.allocationmanagement.controller.allocation.dto.AllocationResponse;
import com.vibeaiteam.allocationmanagement.service.allocation.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 배정(Allocation) 결과 기록에 대한 등록 API를 제공한다. 이미 증권사를 통해 확정된 배정 결과를
// 사용자가 사후에 수동으로 입력·기록하는 기능이며, Allocation은 SecuritiesFirm/Ipo와 동일한 글로벌
// 기준정보로 취급되어 계정 소유권 검증을 적용하지 않는다(architecture.md 명시).
@RestController
@RequestMapping("/allocations")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;

    // 배정 등록 요청을 받아 새 배정 기록을 생성하고, 201 Created와 함께 생성된 배정 정보를 반환한다.
    // 참조하는 Subscription이 존재하지 않으면 404, 동일 subscriptionId에 이미 ACTIVE 배정 기록이 있으면
    // 409를 반환한다(CANCELLED 기록이 있어도 재등록은 허용).
    @PostMapping
    public ResponseEntity<AllocationResponse> register(@RequestBody AllocationRegisterRequest request) {
        AllocationResponse response = allocationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
