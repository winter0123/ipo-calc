package com.ipocalc.accountauthentication.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** CSRF 토큰 부트스트랩/검증/Origin 검증 통합 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.csrf.enabled=true",
      "app.csrf.trusted-origin=http://localhost:5173",
      "app.rate-limit.enabled=false"
    })
class CsrfIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private static final String EMAIL = "csrf-test@example.com";
  private static final String PASSWORD = "securePass123!";
  private static final String ADMIN_EMAIL = "csrf-admin@example.com";
  private static final String ADMIN_PASSWORD = "adminPass123!";
  private String xsrfCookie;
  private MockHttpSession adminSession;

  @BeforeEach
  void setUp() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    accountRepository.save(
        Account.builder()
            .email(EMAIL)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .name("CSRF User")
            .role(AccountRole.USER)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build());

    Account admin =
        Account.builder()
            .email(ADMIN_EMAIL)
            .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
            .name("CSRF Admin")
            .role(AccountRole.ADMIN)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
    accountRepository.save(admin);

    // 부트스트랩: GET /auth/csrf로 토큰 획득
    MvcResult csrfResult =
        mockMvc
            .perform(get("/auth/csrf").session(new MockHttpSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();

    xsrfCookie = extractCookie(csrfResult.getResponse(), "XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();

    // 관리자 로그인
    adminSession = new MockHttpSession();
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)))
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk())
        .andReturn();

    // 로그인 후 쿠키 갱신
    MockHttpServletResponse loginResp =
        mockMvc
            .perform(
                get("/auth/me")
                    .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                    .header("X-XSRF-TOKEN", xsrfCookie)
                    .session(adminSession))
            .andReturn()
            .getResponse();
    String newCookie = extractCookie(loginResp, "XSRF-TOKEN");
    if (newCookie != null) {
      xsrfCookie = newCookie;
    }
  }

  @AfterEach
  void tearDown() {
    investorRepository.deleteAll();
    accountRepository.deleteAll();
  }

  private static String extractCookie(MockHttpServletResponse response, String name) {
    List<String> headers =
        response.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).toList();
    if (headers.isEmpty()) {
      return null;
    }
    return headers.get(0).substring(name.length() + 1).split(";", 2)[0];
  }

  @Test
  void csrfBootstrapReturnsRawTokenAndSetsCookie() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/auth/csrf").session(new MockHttpSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();

    String cookie = extractCookie(result.getResponse(), "XSRF-TOKEN");
    String token =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    assertThat(cookie).isEqualTo(token);
  }

  @Test
  void loginRequiresValidCsrfToken() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String freshCookie =
        extractCookie(
            mockMvc.perform(get("/auth/csrf").session(session)).andReturn().getResponse(),
            "XSRF-TOKEN");

    // 유효한 토큰으로 로그인 성공
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD)))
                .cookie(new Cookie("XSRF-TOKEN", freshCookie))
                .header("X-XSRF-TOKEN", freshCookie)
                .session(session))
        .andExpect(status().isOk());

    // 토큰 없이 로그인 실패
    MockHttpSession session2 = new MockHttpSession();
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD)))
                .session(session2))
        .andExpect(status().isForbidden());
  }

  @Test
  void originValidationRejectsMismatch() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String freshCookie =
        extractCookie(
            mockMvc.perform(get("/auth/csrf").session(session)).andReturn().getResponse(),
            "XSRF-TOKEN");

    // Origin이 신뢰 Origin과 다름 -> 403
    mockMvc
        .perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email",
                            "new@example.com",
                            "password",
                            "pass1234!",
                            "nickname",
                            "New",
                            "name",
                            "New",
                            "displayName",
                            "New",
                            "relationship",
                            "SELF")))
                .cookie(new Cookie("XSRF-TOKEN", freshCookie))
                .header("X-XSRF-TOKEN", freshCookie)
                .header("Origin", "http://evil.com")
                .session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void originValidationAcceptsMatchingTrustedOrigin() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String freshCookie =
        extractCookie(
            mockMvc.perform(get("/auth/csrf").session(session)).andReturn().getResponse(),
            "XSRF-TOKEN");

    // Origin이 일치하면 허용
    mockMvc
        .perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email",
                            "new2@example.com",
                            "password",
                            "pass1234!",
                            "nickname",
                            "New2",
                            "name",
                            "New2",
                            "displayName",
                            "New2",
                            "relationship",
                            "SELF")))
                .cookie(new Cookie("XSRF-TOKEN", freshCookie))
                .header("X-XSRF-TOKEN", freshCookie)
                .header("Origin", "http://localhost:5173")
                .session(session))
        .andExpect(status().isCreated());
  }

  @Test
  void originNullHeaderRejected() throws Exception {
    MockHttpSession session = new MockHttpSession();
    String freshCookie =
        extractCookie(
            mockMvc.perform(get("/auth/csrf").session(session)).andReturn().getResponse(),
            "XSRF-TOKEN");

    mockMvc
        .perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email",
                            "new3@example.com",
                            "password",
                            "pass1234!",
                            "nickname",
                            "New3",
                            "name",
                            "New3",
                            "displayName",
                            "New3",
                            "relationship",
                            "SELF")))
                .cookie(new Cookie("XSRF-TOKEN", freshCookie))
                .header("X-XSRF-TOKEN", freshCookie)
                .header("Origin", "null")
                .session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void refererHostSuffixIsRejectedEvenWhenCsrfTokenIsValid() throws Exception {
    mockMvc
        .perform(
            post("/auth/logout")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .header("Referer", "http://localhost:5173.evil.example/path")
                .session(adminSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingOriginAndRefererAreAllowedWhenCsrfTokenIsValid() throws Exception {
    mockMvc
        .perform(
            post("/auth/logout")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk());
  }

  @Test
  void logoutClearsCsrfCookie() throws Exception {
    mockMvc
        .perform(
            post("/auth/logout")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String cookie = extractCookie(result.getResponse(), "XSRF-TOKEN");
              assertThat(cookie).isEmpty();
            });
  }
}
