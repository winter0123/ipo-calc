package com.ipocalc.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.announcements.domain.Announcement;
import com.ipocalc.announcements.repository.AnnouncementDismissalRepository;
import com.ipocalc.announcements.repository.AnnouncementRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.ObjectMapper;

/** 공지사항 API의 관리자 권한, 게시기간 노출, 계정별 영구 숨김을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AnnouncementControllerTest {

  private static final String PASSWORD = "securePass123!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private AnnouncementRepository announcementRepository;
  @Autowired private AnnouncementDismissalRepository dismissalRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private final List<String> createdEmails = new ArrayList<>();
  private final List<Long> createdAnnouncementIds = new ArrayList<>();

  @AfterEach
  void tearDown() {
    createdAnnouncementIds.forEach(
        id -> {
          dismissalRepository.deleteAll(dismissalRepository.findAll().stream()
              .filter(dismissal -> dismissal.getAnnouncementId().equals(id))
              .toList());
          announcementRepository.deleteById(id);
        });
    createdEmails.forEach(email -> accountRepository.delete(accountRepository.findByEmail(email)));
  }

  @Test
  void adminCreatesActiveAnnouncementThatUserCanDismissAcrossPopupRequests() throws Exception {
    MockHttpSession adminSession = loginAs(AccountRole.ADMIN);
    MockHttpSession userSession = loginAs(AccountRole.USER);
    String request =
        objectMapper.writeValueAsString(
            Map.of(
                "title", "점검 안내",
                "content", "서비스 점검",
                "startAt", LocalDate.now().minusDays(1).toString(),
                "endAt", LocalDate.now().plusDays(1).toString(),
                "status", "ACTIVE"));

    var created =
        mockMvc
            .perform(
                post("/admin/announcements")
                    .session(adminSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andReturn();

    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    Map<?, ?> createdBody =
        objectMapper.readValue(created.getResponse().getContentAsString(), Map.class);
    Long announcementId = ((Number) createdBody.get("id")).longValue();
    createdAnnouncementIds.add(announcementId);

    var popup = mockMvc.perform(get("/announcements/popup").session(userSession)).andReturn();
    assertThat(popup.getResponse().getStatus()).isEqualTo(200);
    assertThat(popup.getResponse().getContentAsString()).contains("점검 안내");

    var dismissed =
        mockMvc
            .perform(post("/announcements/{id}/dismiss", announcementId).session(userSession))
            .andReturn();
    assertThat(dismissed.getResponse().getStatus()).isEqualTo(200);

    var hiddenPopup =
        mockMvc.perform(get("/announcements/popup").session(userSession)).andReturn();
    assertThat(hiddenPopup.getResponse().getStatus()).isEqualTo(204);

    var forbidden =
        mockMvc
            .perform(
                post("/admin/announcements")
                    .session(userSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andReturn();
    assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
  }

  private MockHttpSession loginAs(AccountRole role) throws Exception {
    String email = "announcement-" + UUID.randomUUID() + "@example.com";
    createdEmails.add(email);
    LocalDateTime now = LocalDateTime.now();
    accountRepository.save(
        Account.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .name("계정 테스트")
            .nickname("공지 테스트")
            .role(role)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build());
    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD)))
                .session(session))
        .andReturn();
    return session;
  }
}
