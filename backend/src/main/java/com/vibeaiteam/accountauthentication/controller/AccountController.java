package com.vibeaiteam.accountauthentication.controller;

import com.vibeaiteam.accountauthentication.application.service.SignupService;
import com.vibeaiteam.accountauthentication.controller.dto.SignupRequest;
import com.vibeaiteam.accountauthentication.controller.dto.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 계정(사용자) 회원가입 등 계정 관리 API를 제공한다.
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final SignupService signupService;

    @PostMapping
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        SignupResponse response = signupService.signup(request, httpServletRequest, httpServletResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}