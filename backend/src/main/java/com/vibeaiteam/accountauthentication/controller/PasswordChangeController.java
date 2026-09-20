package com.vibeaiteam.accountauthentication.controller;

import com.vibeaiteam.accountauthentication.application.service.PasswordChangeService;
import com.vibeaiteam.accountauthentication.controller.dto.PasswordChangeRequest;
import com.vibeaiteam.accountauthentication.controller.dto.SuccessResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;
    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @PostMapping(value = "/password-change", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        passwordChangeService.changePassword(request, httpServletRequest, httpServletResponse);
        httpServletResponse.setContentType(JSON_UTF8.toString());
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(SuccessResponse.of("비밀번호가 변경되었습니다"));
    }
}