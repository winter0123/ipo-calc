package com.vibeaiteam.accountauthentication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC 인용: docs/breakdown/account-authentication.md
 * "6. 비밀번호 변경 API"
 * - 포함: POST /auth/password-change 엔드포인트 구현, 현재 비밀번호 검증, 새 비밀번호 유효성 검사 (정책 충족),
 *         비밀번호 변경 시 다른 세션 전체 무효화 (현재 세션 제외 옵션은 결정 필요 사항에 따름), 비밀번호 해시 저장
 *
 * AC 인용: docs/spec/account-authentication.md
 * AC-012: 현재 비밀번호가 일치하면 새 비밀번호가 BCrypt로 해시되어 업데이트된다. "비밀번호가 변경되었습니다" 메시지.
 *         현재 비밀번호 불일치 시 "현재 비밀번호가 올바르지 않습니다" 에러.
 * EC-008: 비밀번호 변경 중 동시 세션 존재 — 변경 후 기존 세션 유지(편의성) vs 전체 세션 무효화(보안) — 결정 필요.
 *
 * Master 결정 (work-log D-007): 비밀번호 변경 후 변경한 계정의 다른 모든 세션 무효화
 *
 * 비밀번호 정책 (D-001): 최소 8자 + 영문/숫자/특수문자 중 2종류 이상 포함
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PasswordChangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String testEmail;
    private String testPassword;
    private String testName;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        testEmail = "pwdchange@example.com";
        testPassword = "securePass123!"; // 정책 충족: 8자 이상, 영문+숫자+특수문자 포함
        testName = "비밀번호변경테스터";
        session = new MockHttpSession();
    }

    @AfterEach
    void tearDown() {
        Account account = accountRepository.findByEmail(testEmail);
        if (account != null) {
            accountRepository.delete(account);
        }
    }

    private void signupAndLogin() throws Exception {
        // 회원가입
        var signupRequest = Map.of(
                "email", testEmail,
                "password", testPassword,
                "name", testName
        );
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest))
                        .session(session))
                .andReturn();

        // 로그인 (세션 유지)
        var loginRequest = Map.of(
                "email", testEmail,
                "password", testPassword
        );
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .session(session))
                .andReturn();
    }

    @Test
    void passwordChangeWithValidCredentialsReturnsOkAndSuccessMessage() throws Exception {
        // Given: 로그인된 사용자 (AC-012)
        signupAndLogin();

        // When: 올바른 현재 비밀번호와 정책을 충족하는 새 비밀번호로 변경 요청
        var changeRequest = Map.of(
                "currentPassword", testPassword,
                "newPassword", "newSecurePass456@",
                "confirmPassword", "newSecurePass456@"
        );
        MvcResult result = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(session))
                .andReturn();

        // Then: 200 OK와 "비밀번호가 변경되었습니다" 메시지 반환 (AC-012)
        int status = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();

        assertThat(status).as("유효한 자격증명으로 비밀번호 변경 시 200을 반환해야 합니다").isEqualTo(200);

        var responseMap = objectMapper.readValue(responseBody, Map.class);
        assertThat(responseMap.get("message")).as("성공 메시지가 포함되어야 합니다").isEqualTo("비밀번호가 변경되었습니다");

        // 비밀번호가 실제로 변경되었는지 검증 (새 비밀번호로 로그인 가능해야 함)
        var newLoginRequest = Map.of(
                "email", testEmail,
                "password", "newSecurePass456@"
        );
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginRequest)))
                .andReturn();

        assertThat(loginResult.getResponse().getStatus())
                .as("새 비밀번호로 로그인이 가능해야 합니다").isEqualTo(200);

        // 이전 비밀번호로는 로그인 실패해야 함
        var oldLoginRequest = Map.of(
                "email", testEmail,
                "password", testPassword
        );
        MvcResult oldLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginRequest)))
                .andReturn();

        assertThat(oldLoginResult.getResponse().getStatus())
                .as("이전 비밀번호로는 로그인할 수 없어야 합니다").isEqualTo(401);
    }

    @Test
    void passwordChangeWithInvalidCurrentPasswordReturnsError() throws Exception {
        // Given: 로그인된 사용자
        signupAndLogin();

        // When: 틀린 현재 비밀번호로 변경 요청
        var changeRequest = Map.of(
                "currentPassword", "wrongCurrentPassword123!",
                "newPassword", "newSecurePass456@",
                "confirmPassword", "newSecurePass456@"
        );
        MvcResult result = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(session))
                .andReturn();

        // Then: "현재 비밀번호가 올바르지 않습니다" 에러와 함께 400 또는 401 반환 (AC-012)
        int status = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();

        assertThat(status).as("잘못된 현재 비밀번호 시 400 또는 401을 반환해야 합니다").isIn(400, 401);

        var responseMap = objectMapper.readValue(responseBody, Map.class);
        assertThat(responseMap.get("message")).as("현재 비밀번호 불일치 에러 메시지").isEqualTo("현재 비밀번호가 올바르지 않습니다");
    }

    @Test
    void passwordChangeWithWeakNewPasswordReturnsValidationError() throws Exception {
        // Given: 로그인된 사용자
        signupAndLogin();

        // When: 새 비밀번호가 정책 위반 (최소 8자 미만)
        var changeRequest = Map.of(
                "currentPassword", testPassword,
                "newPassword", "123", // 8자 미만
                "confirmPassword", "123"
        );
        MvcResult result = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(session))
                .andReturn();

        // Then: 400 Bad Request, 필드별 검증 에러 반환 (AC-012)
        int status = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString();

        assertThat(status).as("정책 미달 비밀번호 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

        var responseMap = objectMapper.readValue(responseBody, Map.class);
        assertThat(responseMap.get("error")).isEqualTo("VALIDATION_FAILED");
        assertThat(responseMap.get("fieldErrors")).isNotNull();
        // newPassword 필드에 대한 에러가 포함되어야 함
        var fieldErrors = (java.util.List<Map<String, String>>) responseMap.get("fieldErrors");
        assertThat(fieldErrors).as("newPassword 필드 에러가 포함되어야 합니다")
                .anyMatch(fe -> "newPassword".equals(fe.get("field")));
    }

    @Test
    void passwordChangeWithNewPasswordMissingCharacterTypesReturnsValidationError() throws Exception {
        // Given: 로그인된 사용자
        signupAndLogin();

        // When: 새 비밀번호가 영문/숫자/특수문자 중 2종류 미만 (영문만)
        var changeRequest = Map.of(
                "currentPassword", testPassword,
                "newPassword", "onlyletters", // 영문만 - 2종류 미만
                "confirmPassword", "onlyletters"
        );
        MvcResult result = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(session))
                .andReturn();

        // Then: 400 Bad Request, 필드별 검증 에러 반환 (D-001 정책)
        int status = result.getResponse().getStatus();

        assertThat(status).as("문자 종류 2종류 미만 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

        String responseBody = result.getResponse().getContentAsString();
        var responseMap = objectMapper.readValue(responseBody, Map.class);
        assertThat(responseMap.get("error")).isEqualTo("VALIDATION_FAILED");
        var fieldErrors = (java.util.List<Map<String, String>>) responseMap.get("fieldErrors");
        assertThat(fieldErrors).anyMatch(fe -> "newPassword".equals(fe.get("field")));
    }

    @Test
    void passwordChangeWithMismatchedConfirmPasswordReturnsValidationError() throws Exception {
        // Given: 로그인된 사용자
        signupAndLogin();

        // When: 새 비밀번호와 확인이 불일치
        var changeRequest = Map.of(
                "currentPassword", testPassword,
                "newPassword", "newSecurePass456@",
                "confirmPassword", "differentPassword789#"
        );
        MvcResult result = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(session))
                .andReturn();

        // Then: 400 Bad Request, confirmPassword 필드 검증 에러
        int status = result.getResponse().getStatus();

        assertThat(status).as("비밀번호 확인 불일치 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

        String responseBody = result.getResponse().getContentAsString();
        var responseMap = objectMapper.readValue(responseBody, Map.class);
        assertThat(responseMap.get("error")).isEqualTo("VALIDATION_FAILED");
        var fieldErrors = (java.util.List<Map<String, String>>) responseMap.get("fieldErrors");
        assertThat(fieldErrors).anyMatch(fe -> "confirmPassword".equals(fe.get("field")));
    }

    @Test
    void passwordChangeWithoutAuthenticationReturnsUnauthorizedOrRedirect() throws Exception {
        // Given: 인증되지 않은 상태 (세션 없음)
        MockHttpSession unauthenticatedSession = new MockHttpSession();

        // When: 인증 없이 비밀번호 변경 요청
        var changeRequest = Map.of(
                "currentPassword", testPassword,
                "newPassword", "newSecurePass456@",
                "confirmPassword", "newSecurePass456@"
        );
        MvcResult result = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(unauthenticatedSession))
                .andReturn();

        // Then: 401 Unauthorized 또는 302 리다이렉트 반환 (DataIsolationTest 패턴, AC-010)
        int status = result.getResponse().getStatus();

        assertThat(status).as("인증되지 않은 요청은 401 또는 302를 반환해야 합니다 (AC-010)").isIn(401, 302);
    }

    @Test
    void passwordChangeInvalidatesOtherSessions() throws Exception {
        // Given: 로그인된 사용자가 두 개의 세션을 보유 (D-007: 변경 후 다른 세션 무효화)
        signupAndLogin();

        // 첫 번째 세션(session)은 이미 로그인됨
        // 두 번째 세션(session2)도 같은 계정으로 로그인
        MockHttpSession session2 = new MockHttpSession();
        var loginRequest = Map.of(
                "email", testEmail,
                "password", testPassword
        );
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .session(session2))
                .andReturn();

        // 두 세션 모두 인증된 상태인지 확인
        MvcResult check1 = mockMvc.perform(get("/api/test-protected").session(session)).andReturn();
        MvcResult check2 = mockMvc.perform(get("/api/test-protected").session(session2)).andReturn();
        assertThat(check1.getResponse().getStatus()).as("세션1이 인증되어야 함").isNotIn(401, 302);
        assertThat(check2.getResponse().getStatus()).as("세션2가 인증되어야 함").isNotIn(401, 302);

        // When: 첫 번째 세션에서 비밀번호 변경
        var changeRequest = Map.of(
                "currentPassword", testPassword,
                "newPassword", "newSecurePass456@",
                "confirmPassword", "newSecurePass456@"
        );
        MvcResult changeResult = mockMvc.perform(post("/auth/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest))
                        .session(session))
                .andReturn();

        // Then: 비밀번호 변경 성공
        assertThat(changeResult.getResponse().getStatus()).as("비밀번호 변경 성공").isEqualTo(200);

        // 현재 세션(session)은 유지되어야 함 (변경을 수행한 세션)
        MvcResult afterChange1 = mockMvc.perform(get("/api/test-protected").session(session)).andReturn();
        assertThat(afterChange1.getResponse().getStatus()).as("변경을 수행한 세션은 유지되어야 함").isNotIn(401, 302);

        // 다른 세션(session2)은 무효화되어야 함 (D-007 결정)
        MvcResult afterChange2 = mockMvc.perform(get("/api/test-protected").session(session2)).andReturn();
        assertThat(afterChange2.getResponse().getStatus()).as("다른 세션은 무효화되어 401/302를 반환해야 함 (D-007)").isIn(401, 302);

        // 새 비밀번호로 로그인하면 새로운 세션 생성 가능
        var newLoginRequest = Map.of(
                "email", testEmail,
                "password", "newSecurePass456@"
        );
        MockHttpSession newSession = new MockHttpSession();
        MvcResult newLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginRequest))
                        .session(newSession))
                .andReturn();
        assertThat(newLoginResult.getResponse().getStatus()).as("새 비밀번호로 새 세션 생성 가능").isEqualTo(200);
    }
}