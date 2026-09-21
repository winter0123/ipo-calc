package com.vibeaiteam.investormanagement.controller.investor;

import com.vibeaiteam.accountauthentication.controller.dto.SuccessResponse;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorRegisterRequest;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorResponse;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorUpdateRequest;
import com.vibeaiteam.investormanagement.service.investor.InvestorService;
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

@RestController
@RequestMapping("/investors")
@RequiredArgsConstructor
public class InvestorController {

    private final InvestorService investorService;

    @PostMapping
    public ResponseEntity<InvestorResponse> register(@RequestBody InvestorRegisterRequest request) {
        InvestorResponse response = investorService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<InvestorResponse>> list(
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(investorService.list(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestorResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(investorService.getById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InvestorResponse> update(
            @PathVariable Long id,
            @RequestBody InvestorUpdateRequest request) {
        return ResponseEntity.ok(investorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse> delete(@PathVariable Long id) {
        return ResponseEntity.ok(investorService.deactivate(id));
    }
}
