package com.ipocalc.ipocatalog.controller.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncRangeRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import com.ipocalc.ipocatalog.exception.IpoSyncRangeValidationException;
import com.ipocalc.ipocatalog.exception.OpenDartKeyMissingException;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoManualSyncResult;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoManualSyncService;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoSyncStateManager;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** POST /ipos/sync (범위 지정) 및 GET /ipos/sync/status 계약 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoSyncRangeControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;
  @MockitoBean private IpoManualSyncService manualSyncService;
  @MockitoBean private IpoSyncStateManager syncStateManager;
  private String email;
  private final String password = "securePass123!";
  private MockHttpSession session;
  private String loginBody;

  @BeforeEach
  void setUp() throws Exception {
    email = "sync-range-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    session = new MockHttpSession();
    loginBody =
        objectMapper.writeValueAsString(
            Map.of("email", email, "password", password, "name", "테스터", "nickname", "테스터닉"));
    mockMvc.perform(
        post("/accounts")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginBody));
  }

  @AfterEach
  void tearDown() {
    Account account = accountRepository.findByEmail(email);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  @Test
  public void adminGetStatusReturnsIdleInitially() throws Exception {
    promoteAndLogin();

    // Ensure the state is IDLE by resetting it.
    syncStateManager.resetToIdle();
    when(manualSyncService.getSyncStatus())
        .thenReturn(
            IpoSyncStatusResponse.builder()
                .state("IDLE")
                .fromMonth(null)
                .toMonth(null)
                .monthsDone(0)
                .monthsTotal(1)
                .startedAt(null)
                .finishedAt(null)
                .result(
                    IpoSyncStatusResponse.SyncResult.builder()
                        .created(0)
                        .updated(0)
                        .skipped(0)
                        .failed(0)
                        .scheduleMerged(0)
                        .detailSaved(0)
                        .aborted(false)
                        .build())
                .error(null)
                .build());

    MockHttpServletResponse res = getSyncStatus();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(res.getContentAsString()).contains("\"state\":\"IDLE\"");
    assertThat(res.getContentAsString()).contains("\"monthsTotal\":1");
  }

  @Test
  void adminEmptyBodyDefaultsToSyncNow() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncNow()).thenReturn(new IpoManualSyncResult(3, 2, 1, 0, 4, 5, false));

    MockHttpServletResponse res = syncRange(null, null);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(res.getContentAsString()).contains("\"created\":3");
    assertThat(res.getContentAsString()).contains("\"detailSaved\":5");
  }

  @Test
  void regularUserRangeGets403() throws Exception {
    login();

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-01", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(res.getContentAsString()).contains("\"error\":\"FORBIDDEN\"");
  }

  @Test
  void unauthenticatedRangeGets401() throws Exception {
    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-01", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(post("/ipos/sync").contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void invalidFromMonthFormatGets400() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(
            argThat(
                req ->
                    req.hasRange()
                        && req.getFromMonth() != null
                        && !req.getFromMonth().matches("\\d{4}-\\d{2}"))))
        .thenThrow(new IpoSyncRangeValidationException("시작 월 형식이 올바르지 않습니다(YYYY-MM)"));

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026/01", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(res.getContentAsString()).contains("INVALID_SYNC_RANGE");
    assertThat(res.getContentAsString()).contains("YYYY-MM");
  }

  @Test
  void invalidToMonthFormatGets400() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(
            argThat(
                req ->
                    req.hasRange()
                        && req.getToMonth() != null
                        && !req.getToMonth().matches("\\d{4}-\\d{2}"))))
        .thenThrow(new IpoSyncRangeValidationException("종료 월 형식이 올바르지 않습니다(YYYY-MM)"));

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-01", "toMonth", "2026/03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(res.getContentAsString()).contains("INVALID_SYNC_RANGE");
  }

  @Test
  void fromMonthAfterToMonthGets400() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(
            argThat(
                req ->
                    req.hasRange()
                        && req.getFromMonth() != null
                        && req.getToMonth() != null
                        && YearMonth.parse(req.getFromMonth())
                            .isAfter(YearMonth.parse(req.getToMonth())))))
        .thenThrow(new IpoSyncRangeValidationException("fromMonth는 toMonth보다 이전이거나 같아야 합니다"));

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-05", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(res.getContentAsString()).contains("INVALID_SYNC_RANGE");
    assertThat(res.getContentAsString()).contains("fromMonth는 toMonth보다 이전이거나 같아야 합니다");
  }

  @Test
  void toMonthAfterThisMonthGets400() throws Exception {
    promoteAndLogin();

    String nextMonth = java.time.YearMonth.now().plusMonths(1).toString(); // YYYY-MM
    when(manualSyncService.syncRange(
            argThat(
                req ->
                    req.hasRange()
                        && req.getToMonth() != null
                        && YearMonth.parse(req.getToMonth()).isAfter(YearMonth.now()))))
        .thenThrow(new IpoSyncRangeValidationException("toMonth는 이번 달 이후일 수 없습니다"));

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-01", "toMonth", nextMonth));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(res.getContentAsString()).contains("INVALID_SYNC_RANGE");
    assertThat(res.getContentAsString()).contains("이번 달");
  }

  @Test
  void fromMonthBefore2015Gets400() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(
            argThat(
                req ->
                    req.hasRange()
                        && req.getFromMonth() != null
                        && YearMonth.parse(req.getFromMonth()).isBefore(YearMonth.of(2015, 1)))))
        .thenThrow(new IpoSyncRangeValidationException("fromMonth는 2015-01 이후여야 합니다"));

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2014-12", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(res.getContentAsString()).contains("INVALID_SYNC_RANGE");
    assertThat(res.getContentAsString()).contains("2015-01");
  }

  @Test
  void rangeExceeds24MonthsGets400() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(
            argThat(
                req ->
                    req.hasRange()
                        && req.getFromMonth() != null
                        && req.getToMonth() != null
                        && java.time.temporal.ChronoUnit.MONTHS.between(
                                    YearMonth.parse(req.getFromMonth()),
                                    YearMonth.parse(req.getToMonth()))
                                + 1
                            > 24)))
        .thenThrow(new IpoSyncRangeValidationException("동기화 범위는 최대 24개월까지 가능합니다"));

    String body =
        objectMapper.writeValueAsString(
            Map.of("fromMonth", "2024-01", "toMonth", "2026-03")); // 27개월
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(res.getContentAsString()).contains("INVALID_SYNC_RANGE");
    assertThat(res.getContentAsString()).contains("24개월");
  }

  @Test
  void alreadyRunningGets409() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(any(IpoSyncRangeRequest.class)))
        .thenThrow(new IpoSyncBusyException());

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-01", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(res.getContentAsString()).contains("IPO_SYNC_RUNNING");
  }

  @Test
  void missingKeyGets503() throws Exception {
    promoteAndLogin();

    when(manualSyncService.syncRange(any(IpoSyncRangeRequest.class)))
        .thenThrow(new OpenDartKeyMissingException());

    String body =
        objectMapper.writeValueAsString(Map.of("fromMonth", "2026-01", "toMonth", "2026-03"));
    MockHttpServletResponse res =
        mockMvc
            .perform(
                post("/ipos/sync")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(res.getContentAsString()).contains("OPENDART_KEY_MISSING");
    assertThat(res.getContentAsString()).contains("OpenDART 키가 설정되지 않았습니다");
  }

  @Test
  void regularUserGetStatusGets403() throws Exception {
    login();

    MockHttpServletResponse res = getSyncStatus();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(res.getContentAsString()).contains("\"error\":\"FORBIDDEN\"");
  }

  @Test
  void unauthenticatedGetStatusGets401() throws Exception {
    MockHttpServletResponse res =
        mockMvc.perform(get("/ipos/sync/status")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  private MockHttpServletResponse syncRange(String fromMonth, String toMonth) throws Exception {
    Map<String, String> bodyMap = new java.util.HashMap<>();
    if (fromMonth != null) {
      bodyMap.put("fromMonth", fromMonth);
    }
    if (toMonth != null) {
      bodyMap.put("toMonth", toMonth);
    }
    String body = objectMapper.writeValueAsString(bodyMap);
    return mockMvc
        .perform(
            post("/ipos/sync")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn()
        .getResponse();
  }

  private MockHttpServletResponse getSyncStatus() throws Exception {
    return mockMvc.perform(get("/ipos/sync/status").session(session)).andReturn().getResponse();
  }

  private void promoteAndLogin() throws Exception {
    Account account = accountRepository.findByEmail(email);
    account.changeRole(AccountRole.ADMIN);
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
