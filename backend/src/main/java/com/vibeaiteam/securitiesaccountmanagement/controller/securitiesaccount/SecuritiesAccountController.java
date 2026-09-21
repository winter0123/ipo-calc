package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount;

import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.vibeaiteam.securitiesaccountmanagement.service.securitiesaccount.SecuritiesAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/securities-accounts")
@RequiredArgsConstructor
public class SecuritiesAccountController {

    private final SecuritiesAccountService securitiesAccountService;

    @PostMapping
    public ResponseEntity<SecuritiesAccountResponse> register(@RequestBody SecuritiesAccountRegisterRequest request) {
        SecuritiesAccountResponse response = securitiesAccountService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}