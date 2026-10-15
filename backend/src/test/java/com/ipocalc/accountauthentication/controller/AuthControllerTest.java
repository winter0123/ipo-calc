package com.ipocalc.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * AC 인용: docs/breakdown/account-authentication.md "4. 로그인/로그아웃 API" - 포함: POST /auth/login 엔드포인트
 * (이메일/비밀번호 인증, 실패 횟수 증가, 5회 실패 시 계정 잠금) - 포함: POST /auth/logout 엔드포인트 (세션 무효화, 쿠키 삭제)
 *
 * <p>AC 인용: docs/spec/account-authentication.md AC-004: 올바른 이메일과 비밀번호로 요청 시 세션이 생성되고 JSESSIONID 쿠키가
 * 발급되며 대시보드로 리다이렉트된다 AC-005: 틀린 비밀번호로 요청 시 실패 횟수가 1 증가하고 "이메일 또는 비밀번호가 올바르지 않습니다" 메시지와 함께 HTTP 401
 * Unauthorized를 반환한다 AC-006: 존재하지 않는 계정으로 요청 시 계정 존재 여부 노출 없이 동일한 오류 메시지와 함께 HTTP 401 Unauthorized를
 * 반환한다 AC-007: 5회 실패 시 계정이 잠금 상태가 되고 이후 로그인 시도 시 "계정이 잠겼습니다. 관리자에게 문의하세요" 메시지와 함께 HTTP 403
 * Forbidden을 반환한다 AC-008: 세션이 무효화되고 JSESSIONID 쿠키가 삭제된다 (Max-Age=0)
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private String testEmail;
  private String testPassword;
  private String testName;

  @BeforeEach
  void setUp() {
    testEmail = "authuser@example.com";
    testPassword = "securePass123!";
    testName = "인증테스터";
  }

  @AfterEach
  void tearDown() {
    // 테스트에서 생성한 계정 정리 - email 중복 충돌 방지
    Account account = accountRepository.findByEmail(testEmail);
    if (account != null) {
      accountRepository.delete(account);
    }
  }

  @Test
  void loginWithValidCredentialsReturnsOkAndSessionCookie() throws Exception {
    // Given: 가입된 계정이 존재하고, 계정 잠금 상태가 아니다 (AC-004)
    // 먼저 계정을 생성한다 (회원가입 API를 통해)
    var signupRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);
    mockMvc.perform(
        post("/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)));

    // When: 올바른 이메일과 비밀번호를 입력하고 로그인한다
    var loginRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword);
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn();

    // Then: 세션이 생성되고 JSESSIONID 쿠키가 발급된다 (AC-004)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();
    String setCookieHeader = result.getResponse().getHeader("Set-Cookie");

    assertThat(status).as("올바른 자격증명으로 로그인 시 200을 반환해야 합니다").isEqualTo(200);
    assertThat(setCookieHeader).as("로그인 성공 시 세션 쿠키(JSESSIONID)가 발급되어야 합니다").isNotNull();
    assertThat(setCookieHeader).as("JSESSIONID 쿠키는 HttpOnly 속성을 가져야 합니다").contains("HttpOnly");
    // Note: 실제 구현에서는 리다이렉트(302)가 될 수 있지만, 명세에서는 200을 기대한다고 해석
    // AC-004에서는 대시보드로 리다이렉트한다고 되어있으므로 302일 수 있음
    // 하지만 테스트 요구사항에서는 200과 세션 쿠키 반환이라고 명시했으므로 200으로 맞춤
  }

  @Test
  void loginWithInvalidPasswordReturnsUnauthorizedAndIncrementsFailureCount() throws Exception {
    // Given: 계정이 존재하고, 계정 잠금 상태가 아니다 (AC-005)
    // 먼저 계정을 생성한다
    var signupRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);
    mockMvc.perform(
        post("/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)));

    // When: 올바른 이메일과 틀린 비밀번호를 입력하고 로그인한다
    var loginRequest = Map.of("email", testEmail, "password", "wrongPassword123!");
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn();

    // Then: "이메일 또는 비밀번호가 올바르지 않습니다" 메시지와 함께 HTTP 401 Unauthorized를 반환한다 (AC-005)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("틀린 비밀번호로 로그인 시 401 Unauthorized를 반환해야 합니다").isEqualTo(401);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap.get("error")).isEqualTo("INVALID_CREDENTIALS");
    assertThat(responseMap.get("message")).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다");

    // 실패 횟수가 1 증가했는지 확인 (직접 DB 조회)
    Account account = accountRepository.findByEmail(testEmail);
    assertThat(account.getFailedLoginAttempts()).isEqualTo(1);
  }

  @Test
  void loginWithNonExistentAccountReturnsSameErrorMessage() throws Exception {
    // Given: 존재하지 않는 계정으로 로그인 시도 (AC-006)
    // Given 절에 이미 계정이 존재하지 않음 (setUp에서 새로운 이메일 사용)

    // When: 존재하지 않는 계정으로 로그인을 시도한다
    var loginRequest =
        Map.of(
            "email", "nonexistent@example.com",
            "password", "anyPassword123!");
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andReturn();

    // Then: 계정 존재 여부 노출 없이 동일한 오류 메시지와 함께 HTTP 401 Unauthorized를 반환한다 (AC-006)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("존재하지 않는 계정으로 로그인 시 401 Unauthorized를 반환해야 합니다").isEqualTo(401);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap.get("error")).isEqualTo("INVALID_CREDENTIALS");
    assertThat(responseMap.get("message")).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다");
    // 계정 존재 여부가 노출되지 않음 (같은 메시지 반환)
  }

  @Test
  void loginAfterFiveFailedAttemptsReturnsAccountLocked() throws Exception {
    // Given: 계정이 존재하고, 5회 로그인 실패로 잠금 상태가 될 때까지 실패 비밀번호로 시도 (AC-007)
    // 먼저 계정을 생성한다
    var signupRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);
    mockMvc.perform(
        post("/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)));

    // When: 5번 연속으로 틀린 비밀번호로 로그인을 시도한다
    for (int i = 0; i < 5; i++) {
      var loginRequest = Map.of("email", testEmail, "password", "wrongPassword" + i);
      mockMvc.perform(
          post("/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(loginRequest)));
    }

    // Then: 6번째 시도(올바른 비밀번호라도) 시 403 Forbidden과 "계정이 잠겼습니다" 메시지를 반환한다 (AC-007)
    var finalLoginRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword // 올바른 비밀번호
            );
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(finalLoginRequest)))
            .andReturn();

    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("5회 실패 후 로그인 시도 시 403 Forbidden을 반환해야 합니다").isEqualTo(403);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap.get("error")).isEqualTo("ACCOUNT_LOCKED");
    assertThat(responseMap.get("message")).isEqualTo("계정이 잠겼습니다. 관리자에게 문의하세요");

    // 계정이 실제로 잠금 상태인지 확인
    Account account = accountRepository.findByEmail(testEmail);
    assertThat(account.getLockedUntil()).isNotNull();
    assertThat(account.getFailedLoginAttempts()).isEqualTo(5);

    // 잠긴 동안은 틀린 비밀번호도 동일한 응답을 받는다
    var wrongRequest = Map.of("email", testEmail, "password", "wrongPassword");
    MvcResult wrongResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(wrongRequest)))
            .andReturn();
    assertThat(wrongResult.getResponse().getStatus()).isEqualTo(403);
    assertThat(wrongResult.getResponse().getContentAsString()).isEqualTo(responseBody);
  }

  @Test
  void logoutReturnsSessionDeletionCookie() throws Exception {
    // Given: 로그아웃 엔드포인트를 테스트한다 (AC-008)
    // Given: 특별히 사전 조건이 필요 없음 (로그아웃은 언제든 호출 가능)

    // When: 로그아웃 엔드포인트를 호출한다
    MvcResult result = mockMvc.perform(post("/auth/logout")).andReturn();

    // Then: 세션이 무효화되고 JSESSIONID 쿠키가 삭제된다 (Max-Age=0) (AC-008)
    int status = result.getResponse().getStatus();
    String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("로그아웃 성공 시 200을 반환해야 합니다").isEqualTo(200);
    assertThat(setCookieHeader).as("로그아웃 시 세션 삭제 쿠키가 포함되어야 합니다").isNotNull();
    assertThat(setCookieHeader).as("JSESSIONID 쿠키의 Max-Age가 0이어야 합니다").contains("Max-Age=0");
    // Note: 실제 구현에서는 리다이렉트(302)가 될 수 있음
    // AC-008에서는 로그인 페이지로 리다이렉트한다고 되어있으므로 302일 수 있음
    // 하지만 테스트 요구사항에서는 세션 무효화와 쿠키 삭제에 중점을 두었음
  }
}
