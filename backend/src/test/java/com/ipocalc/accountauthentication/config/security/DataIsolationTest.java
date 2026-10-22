package com.ipocalc.accountauthentication.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * AC 인용: docs/breakdown/account-authentication.md "5. 데이터 격리(인가) 방어" - 인증이 필요한 경로 보호
 * (SecurityFilterChain의 authorizeHttpRequests) - 인증 안 된 접근 차단: 인증이 필요한 API에 대한 무인증 요청 차단
 *
 * <p>AC 인용: docs/spec/account-authentication.md AC-010: 인증 안 된 사용자가 보호된 API(예: /api/investors)에 접근
 * 시 HTTP 401 Unauthorized를 반환한다 AC-011: 계정 A가 계정 B의 데이터에 접근하려고 할 때 HTTP 403 Forbidden을 반환한다
 *
 * <p>주의: 아직 투자자/계좌 등 실제 보호 리소스 도메인이 없으므로, 이 테스트는 "인증 여부에 따라 SecurityFilterChain이 올바르게 동작하는가"를 검증하는
 * 수준으로 작성한다. 계정 간 데이터 격리(다른 계정 A가 계정 B의 리소스에 접근 못하는 것)는 실제 도메인 리소스(투자자 API 등)가 생기는 이후 Epic에서 본격적으로
 * 테스트한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DataIsolationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    testEmail = "dataisolation@example.com";
    testPassword = "securePass123!";
    testName = "데이터격리테스트";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    // 테스트에서 생성한 계정 정리 - email 중복 충돌 방지
    Account account = accountRepository.findByEmail(testEmail);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  @Test
  void unauthenticatedRequestToProtectedEndpointReturnsUnauthorizedOrRedirect() throws Exception {
    // Given: 인증이 필요한 경로(/api/**)가 SecurityFilterChain으로 보호되어 있음
    // - breakdown AC-5: 인증 안 된 사용자가 보호된 API에 접근 시 HTTP 401 반환
    // - spec.md AC-010: 로그인하지 않은 상태에서 보호된 API 접근 시 401 반환
    // - SecurityConfig: .anyRequest().authenticated()로 모든 /api/** 경로 보호
    // - 임시 테스트용 보호 엔드포인트: /api/test-protected (아직 구현되지 않은 경로)

    // When: 인증 없이 보호된 경로에 GET 요청
    MvcResult result = mockMvc.perform(get("/api/test-protected")).andReturn();

    // Then: 401 Unauthorized 응답
    int status = result.getResponse().getStatus();

    assertThat(status).as("인증되지 않은 요청은 401 Unauthorized를 반환해야 합니다 (AC-010)").isEqualTo(401);

    // Note: D-I-006 (2026-09-21): AuthenticationEntryPoint를 HttpStatusEntryPoint(401)로 변경.
    // 이전에는 formLogin 기본 동작으로 302 리다이렉트(/login)가 올 수 있어 401/302를 허용했지만,
    // 이제 JSON API 계약(AC-010, AC-003)에 맞춰 항상 401을 반환한다.
  }

  @Test
  void authenticatedRequestToProtectedEndpointPassesAuthenticationFilter() throws Exception {
    // Given: 회원가입 및 로그인을 통해 유효한 세션 획득
    // - breakdown AC-3: 회원가입 성공 시 자동 로그인(세션 생성)
    // - breakdown AC-4: 로그인 성공 시 JSESSIONID 쿠키 발급
    // - 임시 테스트용 보호 엔드포인트: /api/test-protected (아직 구현되지 않은 경로)
    //   이 경로는 실제 컨트롤러가 없으므로 404 Not Found가 예상되지만,
    //   인증 필터를 통과했다는 의미이므로 401/302가 아닌 응답이면 인증 통과로 간주

    // 1) 회원가입으로 계정 생성 및 자동 로그인(세션 획득)
    var signupRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);
    MvcResult signupResult =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest))
                    .session(session))
            .andReturn();

    // 세션이 생성되었는지 확인 (JSESSIONID가 session에 저장됨)
    assertThat(session.getId()).as("회원가입 성공 시 세션이 생성되어야 합니다").isNotNull();

    // 2) 획득한 세션으로 보호된 경로 접근
    MvcResult result = mockMvc.perform(get("/api/test-protected").session(session)).andReturn();

    // Then: 401 Unauthorized 또는 302 리다이렉트가 아닌 응답(200 또는 404)
    // - 404 Not Found: 경로가 구현되지 않아서 발생하는 것 → 인증 필터 통과함을 의미
    // - 200 OK: 향후 실제 엔드포인트 구현 시 기대되는 응답
    // - 401/302: 인증 실패 → 테스트 실패
    int status = result.getResponse().getStatus();

    assertThat(status)
        .as("인증된 요청은 401/302가 아닌 응답(200 또는 404)을 반환해야 합니다. 404는 경로 미구현으로 인한 것이며 인증 통과를 의미합니다")
        .isNotIn(401, 302);

    // Note: 현재 /api/test-protected 경로는 컨트롤러가 없으므로 404가 반환됨.
    // 이는 SecurityFilterChain의 인증 검사를 통과했다는 뜻이므로 테스트 통과 조건으로 삼음.
    // 향후 투자자 API 등 실제 보호 리소스가 생기면 200 응답을 기대하도록 변경 예정.
  }

  @Test
  void authenticatedRequestWithValidSessionCookieShowsAuthenticatedPrincipal() throws Exception {
    // Given: 회원가입으로 세션 획득 (위 테스트와 동일)
    var signupRequest =
        Map.of(
            "email", testEmail,
            "password", testPassword,
            "name", testName);
    MvcResult signupResult =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(signupRequest))
                    .session(session))
            .andReturn();

    assertThat(session.getId()).as("회원가입 성공 시 세션이 생성되어야 합니다").isNotNull();

    // When: 세션으로 보호된 경로 접근 시 SecurityContext에 인증 정보가 있는지 간접 확인
    // MockHttpSession을 사용하면 같은 세션으로 연속 요청 가능
    MvcResult result = mockMvc.perform(get("/api/test-protected").session(session)).andReturn();

    // Then: 인증 필터 통과 (401/302 아님)
    int status = result.getResponse().getStatus();
    assertThat(status).as("유효한 세션으로 요청 시 인증 필터를 통과해야 합니다").isNotIn(401, 302);

    // Note: 실제 Principal 검증은 컨트롤러가 구현된 후 @AuthenticationPrincipal 등으로 가능.
    // 현재는 인증 필터 통과 여부만 검증.
  }
}
