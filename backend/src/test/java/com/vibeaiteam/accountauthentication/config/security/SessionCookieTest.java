package com.vibeaiteam.accountauthentication.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 세션 쿠키 속성(HttpOnly·SameSite·Secure)과 JSESSIONID Set-Cookie 중복 방지를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SessionCookieTest {

  private static final String EMAIL = "sessioncookie@example.com";
  private static final String PASSWORD = "securePass123!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;

  @AfterEach
  void tearDown() {
    Account account = accountRepository.findByEmail(EMAIL);
    if (account != null) {
      accountRepository.delete(account);
    }
  }

  @Test
  void loginIssuesSingleHttpOnlySameSiteLaxSessionCookie() throws Exception {
    mockMvc.perform(
        post("/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of("email", EMAIL, "password", PASSWORD, "name", "쿠키테스터"))));

    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", EMAIL, "password", PASSWORD))))
            .andReturn()
            .getResponse();

    List<String> sessionCookies =
        response.getHeaders("Set-Cookie").stream()
            .filter(header -> header.startsWith("JSESSIONID="))
            .toList();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(sessionCookies).as("JSESSIONID Set-Cookie는 정확히 1개").hasSize(1);
    assertThat(sessionCookies.get(0)).contains("HttpOnly").contains("SameSite=Lax");
    assertThat(sessionCookies.get(0)).doesNotContain("Secure");
  }

  @Test
  void signupIssuesHttpOnlySameSiteLaxSessionCookie() throws Exception {
    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", EMAIL, "password", PASSWORD, "name", "쿠키테스터"))))
            .andReturn()
            .getResponse();

    List<String> sessionCookies =
        response.getHeaders("Set-Cookie").stream()
            .filter(header -> header.startsWith("JSESSIONID="))
            .toList();
    assertThat(sessionCookies).hasSize(1);
    assertThat(sessionCookies.get(0)).contains("HttpOnly").contains("SameSite=Lax");
  }

  @Test
  void logoutExpiresSessionCookieWithSameAttributes() throws Exception {
    MockHttpServletResponse response =
        mockMvc.perform(post("/auth/logout")).andReturn().getResponse();

    String cookie = response.getHeader("Set-Cookie");
    assertThat(cookie)
        .startsWith("JSESSIONID=;")
        .contains("Max-Age=0")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  void secureFlagFollowsConfiguration() {
    MockHttpServletResponse secureLogout = new MockHttpServletResponse();
    new SessionCookieWriter(true, "Lax").expire(secureLogout);
    assertThat(secureLogout.getHeader("Set-Cookie")).contains("Secure").contains("Max-Age=0");
  }
}
