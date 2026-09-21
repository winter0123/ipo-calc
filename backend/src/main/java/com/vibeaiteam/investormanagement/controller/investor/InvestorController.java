package com.vibeaiteam.investormanagement.controller.investor;

import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorRegisterRequest;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorResponse;
import com.vibeaiteam.investormanagement.service.investor.InvestorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}