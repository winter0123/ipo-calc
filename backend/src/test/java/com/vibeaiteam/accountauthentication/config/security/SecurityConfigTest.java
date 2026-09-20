package com.vibeaiteam.accountauthentication.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC 인용: docs/breakdown/account-authentication.md
 * "2. Account 도메인 및 보안 기초 설정"
 * - SecurityFilterChain이 설정되어 인증이 필요한 경로를 보호한다
 * - PasswordEncoder 빈이 DelegatingPasswordEncoder를 반환한다
 * - form login / logout / session management / CSRF / Remember Me 설정
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void passwordEncoderBeanShouldExist() {
        // Given: Spring 컨텍스트에 SecurityConfig가 로드되어 있음
        // - breakdown AC-2: PasswordEncoder 빈이 DelegatingPasswordEncoder를 반환한다
        // - architecture.md: config.security 패키지 소유

        // When & Then: PasswordEncoder 빈이 존재한다
        assertThat(passwordEncoder).as("PasswordEncoder 빈이 존재해야 합니다").isNotNull();
    }

    @Test
    void passwordEncoderShouldEncodeAndMatch() {
        // Given: PasswordEncoder 빈이 존재하고
        // - breakdown AC-2: PasswordEncoder 빈이 DelegatingPasswordEncoder를 반환한다
        // - architecture.md: password_hash는 BCrypt로 해시된 비밀번호

        String rawPassword = "securePassword123!";

        // When: 원본 비밀번호로 인코딩
        String encoded = passwordEncoder.encode(rawPassword);

        // Then: 인코딩된 결과가 원문과 다르며, matches로 검증 가능
        assertThat(encoded).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, encoded)).isTrue();
    }

    @Test
    void unauthenticatedRequestToProtectedApiReturnsUnauthorizedOrRedirect() throws Exception {
        // Given: 인증이 필요한 경로가 보호되어 있음
        // - architecture.md: 인증 필요 경로 보호 (authorizeHttpRequests)
        // - breakdown AC-2: SecurityFilterChain이 인증이 필요한 경로를 보호한다
        // - breakdown AC-5: 인증 안 된 사용자가 보호된 API에 접근 시 HTTP 401 반환

        // When: 인증 없이 /api/** 경로 요청
        var result = mockMvc.perform(get("/api/test"))
                .andReturn().getResponse();

        int status = result.getStatus();

        // Then: 401 (인증 필요) 또는 302 (로그인 리다이렉트) 응답
        assertThat(status)
                .as("인증되지 않은 요청은 401 또는 302를 반환해야 합니다")
                .isIn(401, 302);
    }
}
