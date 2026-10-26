package com.ipocalc.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** 인증 보안 강화 회귀: 로그인 동시성, 실패 횟수 원자성, 잠금 해제, 폼 로그인 차단, 세션 고정, 비밀번호 정책. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.signup.mode=OPEN", "app.rate-limit.enabled=false"})
class AuthHardeningTest {

  private static final String PASSWORD = "securePass123!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SessionRegistry sessionRegistry;
  @Autowired private JdbcIndexedSessionRepository sessionRepository;

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
        investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
        accountRepository.delete(account);
      }
    }
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
                .session(session == null ? new MockHttpSession() : session))
        .andReturn();
  }

  private MockHttpSession loginSession(String email) throws Exception {
    MockHttpSession session = new MockHttpSession();
    assertThat(login(email, PASSWORD, session).getResponse().getStatus()).isEqualTo(200);
    return session;
  }

  private List<Integer> runConcurrently(int count, Callable<Integer> task) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(count);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        futures.add(pool.submit(task));
      }
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS));
      }
      return statuses;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentWrongPasswordsDoNotExhaustTheConnectionPool() throws Exception {
    String email = createAccount("pool", AccountRole.USER);
    // 존재하는 이메일에 오답을 15건 동시에 보내도 풀이 마르지 않고 곧 끝나야 한다(이전엔 ~20초 정지).
    long start = System.nanoTime();
    List<Integer> statuses =
        runConcurrently(15, () -> login(email, "wrongPass123!", null).getResponse().getStatus());
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(statuses).allMatch(status -> status == 401 || status == 403);
    assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
    // 그 사이/직후에도 다른 요청은 정상 응답한다.
    assertThat(mockMvc.perform(get("/ipos")).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void failedAttemptsAreCountedAtomically() throws Exception {
    String email = createAccount("atomic", AccountRole.USER);

    runConcurrently(4, () -> login(email, "wrongPass123!", null).getResponse().getStatus());

    assertThat(accountRepository.findByEmail(email).getFailedLoginAttempts()).isEqualTo(4);
  }

  @Test
  void failureCountRestartsFromZeroAfterTheLockExpires() throws Exception {
    String email = createAccount("expired", AccountRole.USER);
    Account account = accountRepository.findByEmail(email);
    account =
        account.toBuilder()
            .failedLoginAttempts(5)
            .lockedUntil(LocalDateTime.now().minusMinutes(1))
            .build();
    accountRepository.save(account);

    assertThat(login(email, "wrongPass123!", null).getResponse().getStatus()).isEqualTo(401);

    Account after = accountRepository.findByEmail(email);
    assertThat(after.getFailedLoginAttempts()).isEqualTo(1);
    assertThat(after.getLockedUntil()).isNull();
    assertThat(login(email, PASSWORD, new MockHttpSession()).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  void fifthFailureLocksAndEvenTheCorrectPasswordIsRefused() throws Exception {
    String email = createAccount("lock", AccountRole.USER);
    for (int i = 0; i < 5; i++) {
      login(email, "wrongPass123!", null);
    }

    assertThat(accountRepository.findByEmail(email).getLockedUntil()).isNotNull();
    assertThat(login(email, PASSWORD, null).getResponse().getStatus()).isEqualTo(403);
    assertThat(login(email, "wrongPass123!", null).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  void adminUnlocksALockedAccountAndItShowsAsLockedInTheList() throws Exception {
    String email = createAccount("locked", AccountRole.USER);
    for (int i = 0; i < 5; i++) {
      login(email, "wrongPass123!", null);
    }
    MockHttpSession admin = loginSession(createAccount("admin", AccountRole.ADMIN));
    Long id = accountRepository.findByEmail(email).getId();

    MvcResult list = mockMvc.perform(get("/admin/accounts").session(admin)).andReturn();
    assertThat(list.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .containsPattern("\"email\":\"" + email + "\"[^}]*\"locked\":true");

    MvcResult unlock =
        mockMvc.perform(post("/admin/accounts/{id}/unlock", id).session(admin)).andReturn();
    assertThat(unlock.getResponse().getStatus()).isEqualTo(204);
    assertThat(login(email, PASSWORD, new MockHttpSession()).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(accountRepository.findByEmail(email).getFailedLoginAttempts()).isZero();
  }

  @Test
  void nonAdminCannotUnlock() throws Exception {
    MockHttpSession user = loginSession(createAccount("plain", AccountRole.USER));

    int asUser =
        mockMvc
            .perform(post("/admin/accounts/1/unlock").session(user))
            .andReturn()
            .getResponse()
            .getStatus();
    int anonymous =
        mockMvc.perform(post("/admin/accounts/1/unlock")).andReturn().getResponse().getStatus();
    assertThat(asUser).isEqualTo(403);
    assertThat(anonymous).isEqualTo(401);
  }

  @Test
  void formLoginAndRememberMeAreNotLoginRoutes() throws Exception {
    String email = createAccount("form", AccountRole.USER);

    MvcResult form =
        mockMvc
            .perform(
                post("/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("username", email)
                    .param("password", PASSWORD)
                    .param("remember-me", "on"))
            .andReturn();
    assertThat(form.getResponse().getStatus()).isEqualTo(401);
    assertThat(form.getResponse().getCookie("JSESSIONID")).isNull();
    assertThat(form.getResponse().getCookie("remember-me")).isNull();

    MvcResult api =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .param("remember-me", "on")
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", PASSWORD))))
            .andReturn();
    assertThat(api.getResponse().getStatus()).isEqualTo(200);
    assertThat(api.getResponse().getCookie("remember-me")).isNull();

    // 기본 GET /logout은 더 이상 로그아웃 동작이 아니다.
    assertThat(mockMvc.perform(get("/logout")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
  }

  /** 로그인 전에 받은 (익명) 세션 ID를 쿠키로 보낸 요청의 응답에서 새 세션 ID를 꺼낸다. 같은 ID면 세션 고정이 가능하다는 뜻. */
  private String sessionIdAfter(MockHttpServletRequestBuilder request, Session before)
      throws Exception {
    String cookie =
        Base64.getEncoder().encodeToString(before.getId().getBytes(StandardCharsets.UTF_8));
    MvcResult result =
        mockMvc.perform(request.cookie(new Cookie("JSESSIONID", cookie))).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(200, 201);
    String header =
        result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(h -> h.startsWith("JSESSIONID="))
            .findFirst()
            .orElseThrow();
    String value = header.substring("JSESSIONID=".length()).split(";", 2)[0];
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Session anonymousSession() {
    SessionRepository raw = sessionRepository;
    Session session = raw.createSession();
    raw.save(session);
    return session;
  }

  @Test
  void loginChangesTheSessionIdIssuedBeforeLogin() throws Exception {
    String email = createAccount("fixation", AccountRole.USER);
    Session before = anonymousSession();

    String after =
        sessionIdAfter(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))),
            before);

    assertThat(after).isNotEqualTo(before.getId());
    assertThat(sessionRepository.findById(before.getId())).as("로그인 전 세션은 버려진다").isNull();
  }

  @Test
  void signupChangesSessionIdAndRegistersTheSessionInTheRegistry() throws Exception {
    String email = "signup-" + nonce + "@example.com";
    createdEmails.add(email);
    Session before = anonymousSession();

    String after =
        sessionIdAfter(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email",
                            email,
                            "password",
                            PASSWORD,
                            "name",
                            "가입",
                            "nickname",
                            "가입닉"))),
            before);

    assertThat(after).isNotEqualTo(before.getId());
    assertThat(sessionRegistry.getSessionInformation(after)).isNotNull();
  }

  @Test
  void expiredSessionGets401InsteadOfARedirect() throws Exception {
    MockHttpSession admin = loginSession(createAccount("admin2", AccountRole.ADMIN));
    String memberEmail = createAccount("member", AccountRole.USER);
    MockHttpSession member = loginSession(memberEmail);
    Long memberId = accountRepository.findByEmail(memberEmail).getId();
    mockMvc.perform(post("/admin/accounts/{id}/revoke", memberId).session(admin)).andReturn();

    MvcResult result = mockMvc.perform(get("/investors").session(member)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getRedirectedUrl()).isNull();
  }

  @Test
  void passwordsOver72BytesAndWeakChangesAreRejectedWith400() throws Exception {
    String tooLong = "Aa1!" + "x".repeat(70);
    MvcResult signup =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "email",
                                "long-" + nonce + "@example.com",
                                "password",
                                tooLong,
                                "name",
                                "x"))))
            .andReturn();
    assertThat(signup.getResponse().getStatus()).isEqualTo(400);

    MockHttpSession session = loginSession(createAccount("pw", AccountRole.USER));
    for (String weak : List.of("onlyletters", "abcdefg1", tooLong)) {
      MvcResult change =
          mockMvc
              .perform(
                  post("/auth/password-change")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          objectMapper.writeValueAsString(
                              Map.of(
                                  "currentPassword", PASSWORD,
                                  "newPassword", weak,
                                  "confirmPassword", weak)))
                      .session(session))
              .andReturn();
      assertThat(change.getResponse().getStatus()).as(weak).isEqualTo(400);
    }
  }
}
