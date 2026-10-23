package com.ipocalc.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * VAPID 키가 없는 서버: 구독·테스트는 503, 공개키 조회는 enabled=false, 해제·설정은 동작한다.
 * 실행 환경(예: Jenkins 컨테이너)에 APP_WEBPUSH_* 가 있어도 키 없음 상태를 고정한다.
 */
@SpringBootTest(properties = {"app.webpush.public-key=", "app.webpush.private-key="})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class NotificationDisabledTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbc;

  private String email;

  @AfterEach
  void tearDown() {
    Long accountId = accountRepository.findByEmail(email).getId();
    jdbc.update("DELETE FROM investors WHERE account_id = ?", accountId);
    jdbc.update("DELETE FROM accounts WHERE id = ?", accountId);
  }

  @Test
  void disabledServerAnswers503ForSubscribeAndTest() throws Exception {
    email = "push-off-" + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE) + "@example.com";
    MockHttpSession session = new MockHttpSession();
    AuthTestSupport.signupAndLogin(mockMvc, objectMapper, session, email, "securePass123!", "C");

    mockMvc.perform(get("/notifications/vapid-public-key").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.publicKey").isEmpty());
    mockMvc
        .perform(post("/notifications/subscriptions").session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"endpoint\":\"https://fcm.googleapis.com/x\","
                + "\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("PUSH_DISABLED"));
    mockMvc.perform(post("/notifications/test").session(session))
        .andExpect(status().isServiceUnavailable());
    mockMvc
        .perform(delete("/notifications/subscriptions").session(session)
            .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://x\"}"))
        .andExpect(status().isNotFound());
  }
}
