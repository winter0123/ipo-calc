package com.vibeaiteam.ipocatalog.controller.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.domain.model.account.AccountRole;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.ipocatalog.exception.IpoSyncBusyException;
import com.vibeaiteam.ipocatalog.exception.OpenDartKeyMissingException;
import com.vibeaiteam.ipocatalog.service.ipo.sync.IpoManualSyncResult;
import com.vibeaiteam.ipocatalog.service.ipo.sync.IpoManualSyncService;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** POST /ipos/sync: 관리자 전용 "지금 가져오기" 계약(외부 호출은 서비스를 mock). */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoSyncNowControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @MockitoBean private IpoManualSyncService manualSyncService;

  private String email;
  private final String password = "securePass123!";
  private MockHttpSession session;

  @BeforeEach
  void setUp() throws Exception {
    email = "sync-now-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    session = new MockHttpSession();
    String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    mockMvc.perform(
        post("/accounts")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of("email", email, "password", password, "name", "테스터"))));
    loginBody = body;
  }

  private String loginBody;

  @AfterEach
  void tearDown() {
    Account account = accountRepository.findByEmail(email);
    if (account != null) {
      accountRepository.delete(account);
    }
  }

  @Test
  void adminGets200WithCounts() throws Exception {
    promoteAndLogin();
    when(manualSyncService.syncNow()).thenReturn(new IpoManualSyncResult(3, 2, 1, 0, 4, 5, false));

    MockHttpServletResponse res = sync();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString())
        .contains("\"created\":3", "\"updated\":2", "\"detailSaved\":5", "\"aborted\":false");
  }

  @Test
  void regularUserGets403() throws Exception {
    login();

    assertThat(sync().getStatus()).isEqualTo(403);
  }

  @Test
  void alreadyRunningGets409() throws Exception {
    promoteAndLogin();
    when(manualSyncService.syncNow()).thenThrow(new IpoSyncBusyException());

    MockHttpServletResponse res = sync();

    assertThat(res.getStatus()).isEqualTo(409);
    assertThat(res.getContentAsString()).contains("IPO_SYNC_RUNNING");
  }

  @Test
  void missingKeyGets503() throws Exception {
    promoteAndLogin();
    when(manualSyncService.syncNow()).thenThrow(new OpenDartKeyMissingException());

    MockHttpServletResponse res = sync();

    assertThat(res.getStatus()).isEqualTo(503);
    assertThat(res.getContentAsString()).contains("OpenDART 키가 설정되지 않았습니다.");
  }

  private MockHttpServletResponse sync() throws Exception {
    return mockMvc.perform(post("/ipos/sync").session(session)).andReturn().getResponse();
  }

  private void promoteAndLogin() throws Exception {
    Account account = accountRepository.findByEmail(email);
    account.setRole(AccountRole.ADMIN);
    accountRepository.save(account);
    login();
  }

  private void login() throws Exception {
    mockMvc.perform(
        post("/auth/login")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody));
  }
}
