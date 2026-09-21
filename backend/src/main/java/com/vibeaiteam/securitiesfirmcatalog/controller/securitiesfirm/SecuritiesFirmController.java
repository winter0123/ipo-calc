package com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm;

import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmRegisterRequest;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmResponse;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmUpdateRequest;
import com.vibeaiteam.securitiesfirmcatalog.service.securitiesfirm.SecuritiesFirmService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 증권사 기준정보에 대한 등록/조회/수정/비활성화 API를 제공한다.
@RestController
@RequestMapping("/securities-firms")
@RequiredArgsConstructor
public class SecuritiesFirmController {

    private final SecuritiesFirmService securitiesFirmService;

    // 증권사 등록 요청을 받아 새 증권사를 생성하고, 201 Created와 함께 생성된 증권사 정보를 반환한다.
    @PostMapping
    public ResponseEntity<SecuritiesFirmResponse> register(@RequestBody SecuritiesFirmRegisterRequest request) {
        SecuritiesFirmResponse response = securitiesFirmService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 비활성화되지 않은(삭제되지 않은) 증권사 목록을 조회해 반환한다.
    @GetMapping
    public ResponseEntity<List<SecuritiesFirmResponse>> list() {
        return ResponseEntity.ok(securitiesFirmService.list());
    }

    // 증권사 ID로 단건 증권사 정보를 조회한다. 존재하지 않거나 비활성화되었으면 404를 반환한다.
    @GetMapping("/{id}")
    public ResponseEntity<SecuritiesFirmResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesFirmService.getById(id));
    }

    // 증권사 ID로 해당 증권사의 이름/외부코드 등을 수정하고, 수정된 증권사 정보를 반환한다. 존재하지 않으면 404, 중복된 이름이면 409를 반환한다.
    @PatchMapping("/{id}")
    public ResponseEntity<SecuritiesFirmResponse> update(@PathVariable Long id,
                                                         @RequestBody SecuritiesFirmUpdateRequest request) {
        return ResponseEntity.ok(securitiesFirmService.update(id, request));
    }

    // 증권사 ID로 해당 증권사를 비활성화 상태로 전환하고, 204 No Content를 반환한다. 존재하지 않으면 404를 반환한다.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        securitiesFirmService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}