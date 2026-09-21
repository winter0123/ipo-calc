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

    @PostMapping
    public ResponseEntity<SecuritiesFirmResponse> register(@RequestBody SecuritiesFirmRegisterRequest request) {
        SecuritiesFirmResponse response = securitiesFirmService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SecuritiesFirmResponse>> list() {
        return ResponseEntity.ok(securitiesFirmService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecuritiesFirmResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesFirmService.getById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SecuritiesFirmResponse> update(@PathVariable Long id,
                                                         @RequestBody SecuritiesFirmUpdateRequest request) {
        return ResponseEntity.ok(securitiesFirmService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        securitiesFirmService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}