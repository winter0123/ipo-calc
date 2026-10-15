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

/** GET /auth/me: 현재 로그인 계정의 역할을 화면이 알 수 있게 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CurrentAccountTest {

  private static final String PASSWORD = "securePass123!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private final List<String> createdEmails = new ArrayList<>();

  @AfterEach
  void tearDown() {
    for (String email : createdEmails) {
      Account account = accountRepository.findByEmail(email);
      if (account != null) {
        accountRepository.delete(account);
      }
    }
  }

  private MockHttpSession loginAs(AccountRole role) throws Exception {
    String email = "me-" + Math.abs(ThreadLocalRandom.current().nextLong()) + "@example.com";
    createdEmails.add(email);
    LocalDateTime now = LocalDateTime.now();
    accountRepository.save(
        Account.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .name("나")
            .role(role)
            .createdAt(now)
            .updatedAt(now)
            .build());
    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD)))
                .session(session))
        .andReturn();
    return session;
  }

  @Test
  void returnsRoleAndProfileWithoutSecrets() throws Exception {
    MvcResult user =
        mockMvc.perform(get("/auth/me").session(loginAs(AccountRole.USER))).andReturn();
    MvcResult admin =
        mockMvc.perform(get("/auth/me").session(loginAs(AccountRole.ADMIN))).andReturn();

    assertThat(user.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> userBody =
        objectMapper.readValue(
            user.getResponse().getContentAsString(StandardCharsets.UTF_8),
            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
    assertThat(userBody.get("role")).isEqualTo("USER");
    assertThat(userBody).containsOnlyKeys("accountId", "email", "name", "role");
    String adminJson = admin.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(objectMapper.readValue(adminJson, Map.class).get("role")).isEqualTo("ADMIN");
  }

  @Test
  void anonymousGets401() throws Exception {
    assertThat(mockMvc.perform(get("/auth/me")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
  }
}
