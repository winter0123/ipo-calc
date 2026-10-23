package com.ipocalc.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.notification.service.PushSender;
import com.ipocalc.testsupport.AuthTestSupport;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 구독 API: 미인증 401, 소유권(남의 endpoint 해제 불가), 입력 검증, 설정, 테스트 발송. 발송기는 mock이다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
@TestPropertySource(
    properties = {
      "app.webpush.public-key=BPublicKeyForTest",
      "app.webpush.private-key=priv",
      "app.webpush.subject=mailto:test@example.com"
    })
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private PushSender sender;

  private String nonce;
  private String emailA;
  private String emailB;
  private MockHttpSession sessionA;
  private MockHttpSession sessionB;

  @BeforeEach
  void setUp() throws Exception {
    nonce = Long.toString(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), 36);
    emailA = "push-a-" + nonce + "@example.com";
    emailB = "push-b-" + nonce + "@example.com";
    sessionA = new MockHttpSession();
    sessionB = new MockHttpSession();
    AuthTestSupport.signupAndLogin(mockMvc, objectMapper, sessionA, emailA, "securePass123!", "A");
    AuthTestSupport.signupAndLogin(mockMvc, objectMapper, sessionB, emailB, "securePass123!", "B");
  }

  @AfterEach
  void tearDown() {
    for (String email : new String[] {emailA, emailB}) {
      Long id = accountRepository.findByEmail(email).getId();
      jdbc.update("DELETE FROM notification_preferences WHERE account_id = ?", id);
      jdbc.update("DELETE FROM push_subscriptions WHERE account_id = ?", id);
      jdbc.update("DELETE FROM investors WHERE account_id = ?", id);
      jdbc.update("DELETE FROM accounts WHERE id = ?", id);
    }
  }

  private String endpoint(String suffix) {
    return "https://fcm.googleapis.com/fcm/send/" + nonce + suffix;
  }

  private String body(String endpoint) throws Exception {
    return objectMapper.writeValueAsString(
        java.util.Map.of(
            "endpoint", endpoint, "keys", java.util.Map.of("p256dh", "BKey_-1", "auth", "au_-1")));
  }

  private void subscribe(MockHttpSession session, String endpoint, int expected) throws Exception {
    mockMvc
        .perform(
            post("/notifications/subscriptions")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(endpoint)))
        .andExpect(status().is(expected));
  }

  private String statusOf(String endpoint) {
    return jdbc.queryForObject(
        "SELECT status FROM push_subscriptions WHERE endpoint = ?", String.class, endpoint);
  }

  @Test
  void unauthenticatedRequestsReturn401() throws Exception {
    mockMvc.perform(get("/notifications/vapid-public-key")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/notifications/preferences")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/notifications/subscriptions").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void vapidKeyIsReturnedWhenConfigured() throws Exception {
    mockMvc
        .perform(get("/notifications/vapid-public-key").session(sessionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.publicKey").value("BPublicKeyForTest"));
  }

  @Test
  void subscribeUpsertsAndTakesOverEndpointFromOtherAccount() throws Exception {
    String ep = endpoint("-up");
    subscribe(sessionA, ep, 200);
    subscribe(sessionA, ep, 200);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?",
        Integer.class, ep)).isEqualTo(1);

    subscribe(sessionB, ep, 200);
    assertThat(jdbc.queryForObject("SELECT account_id FROM push_subscriptions WHERE endpoint = ?",
        Long.class, ep)).isEqualTo(accountRepository.findByEmail(emailB).getId());
  }

  @Test
  void invalidEndpointsAreRejected() throws Exception {
    subscribe(sessionA, "http://fcm.googleapis.com/x", 400);
    subscribe(sessionA, "https://evil.example.com/x", 400);
    subscribe(sessionA, "https://127.0.0.1/x", 400);
    subscribe(sessionA, "https://google.com.evil.example/x", 400);
    subscribe(sessionA, "https://fcm.googleapis.com/" + "a".repeat(1000), 400);
  }

  @Test
  void cannotUnsubscribeOthersEndpoint() throws Exception {
    String ep = endpoint("-own");
    subscribe(sessionA, ep, 200);

    mockMvc
        .perform(delete("/notifications/subscriptions").session(sessionB)
            .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ep + "\"}"))
        .andExpect(status().isNotFound());
    assertThat(statusOf(ep)).isEqualTo("ACTIVE");

    mockMvc
        .perform(delete("/notifications/subscriptions").session(sessionA)
            .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ep + "\"}"))
        .andExpect(status().isOk());
    assertThat(statusOf(ep)).isEqualTo("CANCELLED");
  }

  @Test
  void preferencesDefaultToOnAndPersist() throws Exception {
    mockMvc.perform(get("/notifications/preferences").session(sessionA))
        .andExpect(jsonPath("$.subscriptionDeadline").value(true))
        .andExpect(jsonPath("$.listingDay").value(true));
    mockMvc
        .perform(put("/notifications/preferences").session(sessionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"subscriptionDeadline\":false,\"listingDay\":true}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/notifications/preferences").session(sessionA))
        .andExpect(jsonPath("$.subscriptionDeadline").value(false))
        .andExpect(jsonPath("$.listingDay").value(true));
    mockMvc.perform(get("/notifications/preferences").session(sessionB))
        .andExpect(jsonPath("$.subscriptionDeadline").value(true));
  }

  @Test
  void testNotificationNeedsDeviceAndReportsSentCount() throws Exception {
    mockMvc.perform(post("/notifications/test").session(sessionA))
        .andExpect(status().isBadRequest());

    when(sender.send(any(), any())).thenReturn(PushSender.Result.OK);
    subscribe(sessionA, endpoint("-t1"), 200);
    subscribe(sessionA, endpoint("-t2"), 200);
    mockMvc.perform(post("/notifications/test").session(sessionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sent").value(2));
  }
}
