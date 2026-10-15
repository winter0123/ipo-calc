package com.ipocalc.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import java.time.LocalDateTime;
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
 * AC 인용: docs/breakdown/account-authentication.md "3. 회원가입 API" - POST /accounts 엔드포인트 구현, 이메일 중복
 * 검증 (AccountRepository.existsByEmail) - 비밀번호 해시 저장 (PasswordEncoder.encode), 성공 시 자동 로그인 (세션 생성) -
 * 입력값 검증 (이메일 형식, 비밀번호 정책)
 *
 * <p>AC 인용: docs/spec/account-authentication.md AC-001: 유효한 이메일/비밀번호/이름으로 회원가입 성공 시 201,
 * accountId/email 반환, 비밀번호 해시 미포함 AC-002: 중복 이메일 시 409 Conflict, DUPLICATE_EMAIL 에러 코드 AC-003: 잘못된
 * 이메일 형식 또는 정책 미달 비밀번호 시 400 Bad Request, VALIDATION_FAILED AC-001(연장): 회원가입 성공 시 세션 생성되어 후속 요청에서
 * 인증된 상태로 인식
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AccountControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private String testEmail;
  private String testPassword;
  private String testName;

  @BeforeEach
  void setUp() {
    testEmail = "testuser@example.com";
    testPassword = "securePass123!";
    testName = "테스트유저";
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
  void signupWithValidCredentialsReturnsCreatedAndAccountInfo() throws Exception {
    // Given: 유효한 회원가입 요청 데이터 (AC-001)
    // - 이메일: RFC 5322 형식, 비밀번호: 최소 8자, 영문+숫자+특수문자 포함
    // - 이름: 선택 사항
    var request =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);

    // When: POST /accounts 요청
    MvcResult result =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    // Then: 201 Created 응답, accountId와 email 반환, 비밀번호 해시 미포함 (AC-001)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("회원가입 성공 시 201 Created를 반환해야 합니다").isEqualTo(201);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap).containsKey("accountId");
    assertThat(responseMap).containsKey("email");
    assertThat(responseMap.get("email")).isEqualTo(testEmail);
    assertThat(responseMap).doesNotContainKey("passwordHash");
    assertThat(responseMap).doesNotContainKey("password");
  }

  @Test
  void signupWithDuplicateEmailReturnsConflict() throws Exception {
    // Given: 이미 존재하는 이메일로 계정 생성 (AC-002)
    Account existingAccount =
        Account.builder()
            .email(testEmail)
            .passwordHash(passwordEncoder.encode("differentPassword123!"))
            .name("기존유저")
            .failedLoginAttempts(0)
            .lockedUntil(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    accountRepository.saveAndFlush(existingAccount);

    var request =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);

    // When: 중복 이메일로 POST /accounts 요청
    MvcResult result =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    // Then: 409 Conflict, DUPLICATE_EMAIL 에러 코드 반환 (AC-002)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("중복 이메일 시 409 Conflict를 반환해야 합니다").isEqualTo(409);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap.get("error")).isEqualTo("DUPLICATE_EMAIL");
    assertThat(responseMap.get("message")).isEqualTo("이미 사용 중인 이메일입니다");
  }

  @Test
  void signupWithInvalidEmailFormatReturnsBadRequest() throws Exception {
    // Given: 잘못된 이메일 형식 (AC-003)
    var request =
        Map.of(
            "email", "invalid-email",
            "password", testPassword,
            "name", testName);

    // When: 잘못된 이메일로 POST /accounts 요청
    MvcResult result =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    // Then: 400 Bad Request, VALIDATION_FAILED 에러 코드 반환 (AC-003)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("잘못된 이메일 형식 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap.get("error")).isEqualTo("VALIDATION_FAILED");
    assertThat(responseMap.get("fieldErrors")).isNotNull();
  }

  @Test
  void signupWithWeakPasswordReturnsBadRequest() throws Exception {
    // Given: 정책 미달 비밀번호 (최소 8자 미만) (AC-003)
    var request =
        Map.of(
            "email", "another@example.com",
            "password", "123",
            "name", testName);

    // When: 약한 비밀번호로 POST /accounts 요청
    MvcResult result =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    // Then: 400 Bad Request, VALIDATION_FAILED 에러 코드 반환 (AC-003)
    int status = result.getResponse().getStatus();
    String responseBody = result.getResponse().getContentAsString();

    assertThat(status).as("정책 미달 비밀번호 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

    var responseMap = objectMapper.readValue(responseBody, Map.class);
    assertThat(responseMap.get("error")).isEqualTo("VALIDATION_FAILED");
    assertThat(responseMap.get("fieldErrors")).isNotNull();
  }

  @Test
  void signupSuccessCreatesSessionForSubsequentRequests() throws Exception {
    // Given: 유효한 회원가입 요청 데이터 (AC-001 연장 - 자동 로그인/세션 생성)
    var request =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);

    // When: POST /accounts 요청으로 회원가입 및 자동 로그인
    MvcResult signupResult =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    // Then: 201 응답과 함께 세션 쿠키(JSESSIONID)가 생성됨
    int status = signupResult.getResponse().getStatus();
    String setCookieHeader = signupResult.getResponse().getHeader("Set-Cookie");

    assertThat(status).as("회원가입 성공 시 201을 반환해야 합니다").isEqualTo(201);
    assertThat(setCookieHeader).as("회원가입 성공 시 세션 쿠키가 발급되어야 합니다").isNotNull();
    assertThat(setCookieHeader).as("JSESSIONID 쿠키가 HttpOnly 속성을 가져야 합니다").contains("HttpOnly");
  }
}
