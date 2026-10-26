package com.ipocalc.accountauthentication.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인 유지: 세션이 DB(spring_session)에 있고 쿠키만으로 다시 인증되며, 쿠키·세션 만료가 30일로 슬라이딩하고, 로그아웃하면 행이 지워진다. 서버 재시작은
 * 메모리 상태 없이 쿠키 값만으로 인증되는지로 갈음한다(실제 재기동은 별도로 확인).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.rate-limit.enabled=false")
class SessionPersistenceTest {

  private static final String EMAIL = "persist-session@example.com";
  private static final String PASSWORD = "securePass123!";
  private static final long THIRTY_DAYS_MS = 30L * 24 * 3600 * 1000;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", EMAIL);
    Account account = accountRepository.findByEmail(EMAIL);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  private MockHttpServletResponse login() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    accountRepository.save(
        Account.builder()
            .email(EMAIL)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .name("유지")
            .role(AccountRole.USER)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build());
    return mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
        .andReturn()
        .getResponse();
  }

  private static String setCookie(MockHttpServletResponse response) {
    List<String> headers =
        response.getHeaders("Set-Cookie").stream()
            .filter(h -> h.startsWith("JSESSIONID="))
            .toList();
    assertThat(headers).hasSize(1);
    return headers.get(0);
  }

  private static Cookie cookieOf(String header) {
    return new Cookie("JSESSIONID", header.substring("JSESSIONID=".length()).split(";", 2)[0]);
  }

  private Long expiry() {
    return jdbc.queryForObject(
        "SELECT expiry_time FROM spring_session WHERE principal_name = ?", Long.class, EMAIL);
  }

  @Test
  void loginStoresSessionInDbWith30DayCookieAndRequestsSlideIt() throws Exception {
    String header = setCookie(login());

    assertThat(header).contains("Max-Age=2592000").contains("HttpOnly").contains("SameSite=Lax");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class,
                EMAIL))
        .isEqualTo(1);
    Long first = expiry();
    assertThat(first - System.currentTimeMillis())
        .isBetween(THIRTY_DAYS_MS - 60_000, THIRTY_DAYS_MS);

    // 쿠키만으로(서버 메모리·MockHttpSession 없이) 인증되고, 응답마다 쿠키 Max-Age를 다시 쓰며 DB 만료가 뒤로 밀린다.
    Thread.sleep(1100);
    MockHttpServletResponse me =
        mockMvc.perform(get("/auth/me").cookie(cookieOf(header))).andReturn().getResponse();
    assertThat(me.getStatus()).isEqualTo(200);
    assertThat(setCookie(me)).contains("Max-Age=2592000");
    assertThat(expiry()).isGreaterThan(first);
  }

  @Test
  void logoutDeletesTheRowAndExpiresTheCookie() throws Exception {
    Cookie cookie = cookieOf(setCookie(login()));

    MockHttpServletResponse out =
        mockMvc.perform(post("/auth/logout").cookie(cookie)).andReturn().getResponse();

    assertThat(out.getHeaders("Set-Cookie")).allMatch(h -> h.contains("Max-Age=0"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class,
                EMAIL))
        .isZero();
    MockHttpServletResponse after =
        mockMvc.perform(get("/auth/me").cookie(cookie)).andReturn().getResponse();
    assertThat(after.getStatus()).isEqualTo(401);
  }

  @Test
  void anonymousRequestsDoNotCreateSessionRows() throws Exception {
    int before = jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);

    MockHttpServletResponse response = mockMvc.perform(get("/auth/me")).andReturn().getResponse();

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
        .isEqualTo(before);
  }
}
