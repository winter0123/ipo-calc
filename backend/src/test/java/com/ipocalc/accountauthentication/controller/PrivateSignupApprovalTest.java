package com.ipocalc.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** 비공개 운영(APPROVAL) 가입 모드: 승인 전 로그인 차단, 관리자 승인·취소, 권한 경계. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.signup.mode=APPROVAL")
class PrivateSignupApprovalTest {

  private static final String PASSWORD = "securePass123!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private final List<String> createdEmails = new ArrayList<>();
  private String nonce;

  @BeforeEach
  void setUp() {
    nonce = Long.toString(Math.abs(ThreadLocalRandom.current().nextLong()));
  }

  @AfterEach
  void tearDown() {
    for (String email : createdEmails) {
      Account account = accountRepository.findByEmail(email);
      if (account != null) {
        accountRepository.delete(account);
      }
    }
  }

  private String signupAsPending() throws Exception {
    String email = "pending-" + nonce + "@example.com";
    createdEmails.add(email);
    MvcResult result =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", PASSWORD, "name", "대기자"))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<?, ?> body = objectMapper.readValue(utf8(result), Map.class);
    assertThat(body.get("approved")).isEqualTo(false);
    assertThat(result.getResponse().getCookie("JSESSIONID")).as("승인 전에는 세션을 만들지 않는다").isNull();
    return email;
  }

  private String createAccount(String prefix, AccountRole role) {
    String email = prefix + "-" + nonce + "@example.com";
    createdEmails.add(email);
    LocalDateTime now = LocalDateTime.now();
    accountRepository.save(
        Account.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .name(prefix)
            .role(role)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build());
    return email;
  }

  private MvcResult login(String email, String password, MockHttpSession session) throws Exception {
    return mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password)))
                .session(session))
        .andReturn();
  }

  private MockHttpSession loginSession(String email) throws Exception {
    MockHttpSession session = new MockHttpSession();
    assertThat(login(email, PASSWORD, session).getResponse().getStatus()).isEqualTo(200);
    return session;
  }

  private static String utf8(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void pendingAccountCannotLoginWithCorrectPasswordAndWrongPasswordStays401() throws Exception {
    String email = signupAsPending();

    MvcResult correct = login(email, PASSWORD, new MockHttpSession());
    assertThat(correct.getResponse().getStatus()).isEqualTo(403);
    assertThat(utf8(correct)).contains("ACCOUNT_PENDING_APPROVAL");

    MvcResult wrong = login(email, "wrongPass123!", new MockHttpSession());
    assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
    assertThat(accountRepository.findByEmail(email).getFailedLoginAttempts())
        .as("승인 대기 로그인은 실패 횟수에 넣지 않는다")
        .isEqualTo(1);
  }

  @Test
  void adminListsPendingAccountsApprovesAndTheAccountCanThenLogin() throws Exception {
    String pending = signupAsPending();
    MockHttpSession admin = loginSession(createAccount("admin", AccountRole.ADMIN));

    MvcResult list =
        mockMvc
            .perform(get("/admin/accounts").param("approved", "false").session(admin))
            .andReturn();
    assertThat(list.getResponse().getStatus()).isEqualTo(200);
    assertThat(utf8(list))
        .contains(pending)
        .doesNotContain("passwordHash")
        .doesNotContain("password");

    Long pendingId = accountRepository.findByEmail(pending).getId();
    MvcResult approve =
        mockMvc.perform(post("/admin/accounts/{id}/approve", pendingId).session(admin)).andReturn();
    assertThat(approve.getResponse().getStatus()).isEqualTo(204);

    assertThat(login(pending, PASSWORD, new MockHttpSession()).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void revokeBlocksLoginAgainAndEndsExistingSessions() throws Exception {
    MockHttpSession admin = loginSession(createAccount("admin", AccountRole.ADMIN));
    String member = createAccount("member", AccountRole.USER);
    MockHttpSession memberSession = loginSession(member);
    Long memberId = accountRepository.findByEmail(member).getId();

    MvcResult revoke =
        mockMvc.perform(post("/admin/accounts/{id}/revoke", memberId).session(admin)).andReturn();

    assertThat(revoke.getResponse().getStatus()).isEqualTo(204);
    assertThat(login(member, PASSWORD, new MockHttpSession()).getResponse().getStatus())
        .isEqualTo(403);
    MvcResult afterRevoke = mockMvc.perform(get("/investors").session(memberSession)).andReturn();
    assertThat(afterRevoke.getResponse().getStatus())
        .as("취소된 계정의 기존 세션은 더 이상 쓸 수 없다")
        .isEqualTo(401);
  }

  @Test
  void adminCannotRevokeOwnApproval() throws Exception {
    String adminEmail = createAccount("admin", AccountRole.ADMIN);
    MockHttpSession admin = loginSession(adminEmail);
    Long adminId = accountRepository.findByEmail(adminEmail).getId();

    MvcResult result =
        mockMvc.perform(post("/admin/accounts/{id}/revoke", adminId).session(admin)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(accountRepository.findByEmail(adminEmail).isApproved()).isTrue();
  }

  @Test
  void nonAdminAndAnonymousCannotUseAdminApi() throws Exception {
    MockHttpSession user = loginSession(createAccount("user", AccountRole.USER));

    MvcResult listAsUser = mockMvc.perform(get("/admin/accounts").session(user)).andReturn();
    assertThat(listAsUser.getResponse().getStatus()).isEqualTo(403);
    assertThat(
            mockMvc
                .perform(post("/admin/accounts/1/approve").session(user))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(mockMvc.perform(get("/admin/accounts")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
  }

  @Test
  void approveUnknownAccountReturns404() throws Exception {
    MockHttpSession admin = loginSession(createAccount("admin", AccountRole.ADMIN));

    assertThat(
            mockMvc
                .perform(post("/admin/accounts/{id}/approve", 999_999_999L).session(admin))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
  }
}
