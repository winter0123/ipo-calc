package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount;

import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountAccountNumberResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountUpdateRequest;
import com.vibeaiteam.securitiesaccountmanagement.service.securitiesaccount.SecuritiesAccountService;
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
@RequestMapping("/securities-accounts")
@RequiredArgsConstructor
public class SecuritiesAccountController {

    private final SecuritiesAccountService securitiesAccountService;

    @PostMapping
    public ResponseEntity<SecuritiesAccountResponse> register(@RequestBody SecuritiesAccountRegisterRequest request) {
        SecuritiesAccountResponse response = securitiesAccountService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SecuritiesAccountResponse>> list(
            @RequestParam(required = false) Long investorId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(securitiesAccountService.list(investorId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecuritiesAccountResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesAccountService.getById(id));
    }

    @GetMapping("/{id}/account-number")
    public ResponseEntity<SecuritiesAccountAccountNumberResponse> getAccountNumber(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesAccountService.getAccountNumber(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SecuritiesAccountResponse> update(
            @PathVariable Long id, @RequestBody SecuritiesAccountUpdateRequest request) {
        return ResponseEntity.ok(securitiesAccountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        securitiesAccountService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/set-default")
    public ResponseEntity<SecuritiesAccountResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(securitiesAccountService.setDefault(id));
    }
}