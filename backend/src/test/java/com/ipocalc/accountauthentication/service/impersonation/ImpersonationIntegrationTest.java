package com.ipocalc.accountauthentication.service.impersonation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.domain.model.impersonation.ImpersonationSession;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.accountauthentication.repository.impersonation.ImpersonationSessionRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 임퍼소네이션 시작/상태/종료/읽기전용/타겟격리 통합 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.csrf.enabled=true",
      "app.csrf.trusted-origin=http://localhost:5173",
      "app.rate-limit.enabled=false"
    })
class ImpersonationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;
  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Autowired
  private com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor
      accountNumberEncryptor;

  @Autowired private ImpersonationSessionRepository impersonationRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private static final String ADMIN_EMAIL = "imp-admin@example.com";
  private static final String ADMIN_PASSWORD = "adminPass123!";
  private static final String TARGET_EMAIL = "imp-target@example.com";
  private static final String TARGET_PASSWORD = "targetPass123!";
  private static final String THIRD_EMAIL = "imp-third@example.com";
  private static final String THIRD_PASSWORD = "thirdPass123!";

  private String xsrfCookie;
  private MockHttpSession adminSession;
  private Long adminId;
  private Long targetId;
  private Long thirdId;
  private Long targetInvestorId;
  private Long thirdInvestorId;
  private Long targetSecuritiesAccountId;
  private Long thirdSecuritiesAccountId;

  @BeforeEach
  void setUp() throws Exception {
    LocalDateTime now = LocalDateTime.now();

    // 관리자 계정
    Account admin =
        Account.builder()
            .email(ADMIN_EMAIL)
            .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
            .name("Impersonation Admin")
            .role(AccountRole.ADMIN)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
    admin = accountRepository.save(admin);
    adminId = admin.getId();

    // 타겟 계정
    Account target =
        Account.builder()
            .email(TARGET_EMAIL)
            .passwordHash(passwordEncoder.encode(TARGET_PASSWORD))
            .name("Target User")
            .role(AccountRole.USER)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
    target = accountRepository.save(target);
    targetId = target.getId();

    // 제3자 계정
    Account third =
        Account.builder()
            .email(THIRD_EMAIL)
            .passwordHash(passwordEncoder.encode(THIRD_PASSWORD))
            .name("Third User")
            .role(AccountRole.USER)
            .approved(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
    third = accountRepository.save(third);
    thirdId = third.getId();

    // 증권사 (기존 시드 사용)
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByNameAndStatus("한국투자증권", RecordStatus.ACTIVE)
            .orElseGet(
                () -> {
                  SecuritiesFirm f =
                      SecuritiesFirm.builder()
                          .name("한국투자증권")
                          .externalCode("KIS")
                          .businessRegistrationNumber("1234567890")
                          .status(RecordStatus.ACTIVE)
                          .createdAt(now)
                          .updatedAt(now)
                          .build();
                  return securitiesFirmRepository.save(f);
                });

    // 타겟 투자자 & 증권계좌
    Investor targetInvestor =
        Investor.builder()
            .accountId(targetId)
            .displayName("타겟 투자자")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
    targetInvestor = investorRepository.save(targetInvestor);
    targetInvestorId = targetInvestor.getId();

    SecuritiesAccount targetAccount =
        SecuritiesAccount.builder()
            .investorId(targetInvestorId)
            .securitiesFirmId(firm.getId())
            .accountNumber(accountNumberEncryptor.encrypt("110-123-456789"))
            .accountName("타겟 계좌")
            .isDefault(true)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
    targetAccount = securitiesAccountRepository.save(targetAccount);
    targetSecuritiesAccountId = targetAccount.getId();

    // 제3자 투자자 & 증권계좌
    Investor thirdInvestor =
        Investor.builder()
            .accountId(thirdId)
            .displayName("제3자 투자자")
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
    thirdInvestor = investorRepository.save(thirdInvestor);
    thirdInvestorId = thirdInvestor.getId();

    SecuritiesAccount thirdAccount =
        SecuritiesAccount.builder()
            .investorId(thirdInvestorId)
            .securitiesFirmId(firm.getId())
            .accountNumber(accountNumberEncryptor.encrypt("110-987-654321"))
            .accountName("제3자 계좌")
            .isDefault(true)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();
    thirdAccount = securitiesAccountRepository.save(thirdAccount);
    thirdSecuritiesAccountId = thirdAccount.getId();

    // CSRF 부트스트랩
    MvcResult csrfResult =
        mockMvc
            .perform(get("/auth/csrf").session(new MockHttpSession()))
            .andExpect(status().isOk())
            .andReturn();
    xsrfCookie = extractCookie(csrfResult.getResponse(), "XSRF-TOKEN");

    // 관리자 로그인
    adminSession = new MockHttpSession();
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)))
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk())
        .andReturn();

    // 로그인 후 토큰 갱신
    MockHttpServletResponse loginResp =
        mockMvc
            .perform(
                get("/auth/me")
                    .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                    .header("X-XSRF-TOKEN", xsrfCookie)
                    .session(adminSession))
            .andReturn()
            .getResponse();
    String newCookie = extractCookie(loginResp, "XSRF-TOKEN");
    if (newCookie != null) {
      xsrfCookie = newCookie;
    }
  }

  @AfterEach
  void tearDown() {
    securitiesAccountRepository.deleteAll();
    investorRepository.deleteAll();
    accountRepository.deleteAll();
    impersonationRepository.deleteAll();
  }

  private static String extractCookie(MockHttpServletResponse response, String name) {
    List<String> headers =
        response.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(name + "=")).toList();
    if (headers.isEmpty()) {
      return null;
    }
    return headers.get(0).substring(name.length() + 1).split(";", 2)[0];
  }

  @Test
  void startImpersonationSetsMarkerAndReturnsSessionId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/admin/impersonation/start/" + targetId)
                    .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                    .header("X-XSRF-TOKEN", xsrfCookie)
                    .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").exists())
            .andExpect(jsonPath("$.readOnly").value(true))
            .andReturn();

    String sessionId = extractJsonField(result, "sessionId");
    assertThat(sessionId).isNotNull();

    // 상태 엔드포인트로 임퍼소네이션 활성 확인
    MvcResult statusResult =
        mockMvc
            .perform(
                get("/admin/impersonation/status")
                    .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                    .header("X-XSRF-TOKEN", xsrfCookie)
                    .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.impersonating").value(true))
            .andExpect(jsonPath("$.targetAccountId").value(targetId.intValue()))
            .andReturn();

    // DB 레코드 확인
    ImpersonationSession dbSession =
        impersonationRepository.findBySessionId(sessionId).orElseThrow();
    assertThat(dbSession.getActorAdminId()).isEqualTo(adminId);
    assertThat(dbSession.getSubjectAccountId()).isEqualTo(targetId);
    assertThat(dbSession.getSpringSessionId()).isNotBlank();
    assertThat(dbSession.getEndedAt()).isNull();
  }

  @Test
  void statusReturnsImpersonatingTrueDuringSession() throws Exception {
    startImpersonation();

    MvcResult result =
        mockMvc
            .perform(
                get("/admin/impersonation/status")
                    .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                    .header("X-XSRF-TOKEN", xsrfCookie)
                    .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.impersonating").value(true))
            .andExpect(jsonPath("$.targetAccountId").value(targetId.intValue()))
            .andReturn();

    assertThat(extractJsonField(result, "expiresAt")).isNotNull();
  }

  @Test
  void endImpersonationClearsMarkerAndReturns204() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            post("/admin/impersonation/end")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isNoContent());

    assertThat(adminSession.getAttribute(ImpersonationService.SESSION_ATTRIBUTE)).isNull();

    // 마커 제거 확인
    assertThat(adminSession.getAttribute("IMPERSONATION_SESSION_ID")).isNull();

    // 상태 확인 -> false
    mockMvc
        .perform(
            get("/admin/impersonation/status")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.impersonating").value(false));
  }

  @Test
  void impersonationReadOnlyAllowsGetDashboard() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            get("/dashboard/summary")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk());
  }

  @Test
  void impersonationReadOnlyAllowsGetInvestmentHistory() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            get("/investment-history")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk());
  }

  @Test
  void impersonationReadOnlyAllowsGetSecuritiesAccounts() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            get("/securities-accounts")
                .param("investorId", targetInvestorId.toString())
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk());
  }

  @Test
  void impersonationReadOnlyDeniesSecuritiesAccountNumber() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            get("/securities-accounts/" + targetSecuritiesAccountId + "/account-number")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void impersonationReadOnlyDeniesWriteOperations() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            post("/investors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("displayName", "New", "relationship", "SELF")))
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void impersonationReadOnlyDeniesOtherAdminApis() throws Exception {
    startImpersonation();

    mockMvc
        .perform(
            get("/admin/accounts")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void impersonationTargetDataIsolationOtherAccountReturns403() throws Exception {
    startImpersonation();

    // 타겟의 계좌는 조회 가능
    mockMvc
        .perform(
            get("/securities-accounts/" + targetSecuritiesAccountId)
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk());

    // 제3자의 계좌는 403
    mockMvc
        .perform(
            get("/securities-accounts/" + thirdSecuritiesAccountId)
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void cannotStartImpersonationOnSelf() throws Exception {
    mockMvc
        .perform(
            post("/admin/impersonation/start/" + adminId)
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cannotStartImpersonationOnUnapprovedAccount() throws Exception {
    // 미승인 계정 생성
    Account unapproved =
        Account.builder()
            .email("unapproved@example.com")
            .passwordHash(passwordEncoder.encode("pass1234!"))
            .name("Unapproved")
            .role(AccountRole.USER)
            .approved(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    unapproved = accountRepository.save(unapproved);

    mockMvc
        .perform(
            post("/admin/impersonation/start/" + unapproved.getId())
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isNotFound());
  }

  @Test
  void secondStartIsRejectedWhileImpersonationIsActive() throws Exception {
    // 첫 번째 임퍼소네이션 시작
    MvcResult result1 =
        mockMvc
            .perform(
                post("/admin/impersonation/start/" + targetId)
                    .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                    .header("X-XSRF-TOKEN", xsrfCookie)
                    .session(adminSession))
            .andExpect(status().isOk())
            .andReturn();
    String firstSessionId = extractJsonField(result1, "sessionId");

    // 활성 세션 중복 시작은 읽기 전용 정책에 따라 거부되고 기존 세션은 유지된다.
    mockMvc
        .perform(
            post("/admin/impersonation/start/" + thirdId)
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isForbidden());

    ImpersonationSession first =
        impersonationRepository.findBySessionId(firstSessionId).orElseThrow();
    assertThat(first.getEndedAt()).isNull();

    // 상태 엔드포인트는 기존 세션의 대상으로 유지된다.
    mockMvc
        .perform(
            get("/admin/impersonation/status")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.impersonating").value(true))
        .andExpect(jsonPath("$.targetAccountId").value(targetId.intValue()));
  }

  @Test
  void endIsIdempotent() throws Exception {
    startImpersonation();

    // 첫 번째 종료
    mockMvc
        .perform(
            post("/admin/impersonation/end")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isNoContent());

    // 두 번째 종료도 204 (에러 없음)
    mockMvc
        .perform(
            post("/admin/impersonation/end")
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isNoContent());
  }

  private void startImpersonation() throws Exception {
    mockMvc
        .perform(
            post("/admin/impersonation/start/" + targetId)
                .cookie(new Cookie("XSRF-TOKEN", xsrfCookie))
                .header("X-XSRF-TOKEN", xsrfCookie)
                .session(adminSession))
        .andExpect(status().isOk());
  }

  private String extractJsonField(MvcResult result, String field) throws Exception {
    String json = result.getResponse().getContentAsString();
    JsonNode node = objectMapper.readTree(json).get(field);
    String resultStr = node != null ? node.asText() : null;
    return resultStr;
  }
}
