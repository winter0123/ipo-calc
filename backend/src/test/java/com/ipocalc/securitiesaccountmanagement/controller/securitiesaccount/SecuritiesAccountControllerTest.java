package com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * AC 인용: docs/spec/securities-account-management.md, docs/work-log/securities-account-management.md
 * - AC-001: 증권계좌 등록 성공 — 유효한 요청 시 201 Created + 신규 레코드 생성, 응답에 계좌 ID/투자자 ID/증권사 ID/마스킹된
 * 계좌번호/status=ACTIVE/deletedAt=null 포함 - AC-002: 증권계좌 등록 실패 (필수값 누락) —
 * investorId/securitiesFirmId/accountNumber 중 하나 이상 누락 시 400 Bad Request + VALIDATION_ERROR, 레코드
 * 미생성 - AC-003: 증권계좌 등록 실패 (존재하지 않는 투자자) — 404 Not Found, 레코드 미생성 - AC-004: 증권계좌 등록 실패 (존재하지 않는
 * 증권사) — 404 Not Found, 레코드 미생성 - AC-005: 증권계좌 등록 실패 (계정 소유권 없음) — 계정 A가 계정 B의 투자자 ID로 요청 시 403
 * Forbidden + FORBIDDEN - D-SA-003: 투자자의 첫 계좌 등록 시 isDefault=true 자동 설정 (요청의 isDefault와 무관하게 강제) -
 * SF-2: 등록 성공 시 accountNumber는 AES 암호화되어 DB에 저장되고, 응답에는 원본 평문이 아닌 마스킹된 값(뒷 4자리만 노출)이 포함된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class SecuritiesAccountControllerTest {

  private static final String ACCOUNT_NUMBER = "123456789012";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;

  @Autowired private AccountNumberEncryptor accountNumberEncryptor;

  @Autowired private SecuritiesAccountApiCredentialRepository credentialRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @MockitoSpyBean private Clock clock;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;
  private Long accountId;
  private Long firmId;
  private String otherEmail;
  private final List<SecuritiesAccount> createdAccounts = new ArrayList<>();
  private final List<SecuritiesFirm> createdFirms = new ArrayList<>();

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "securities-account-owner-" + nonce + "@example.com";
    testPassword = "securePass123!";
    testName = "계좌테스터";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    cleanupAccount(testEmail);
    cleanupAccount(otherEmail);
    cleanupFirms();
  }

  private void cleanupAccount(String email) {
    if (email == null) {
      return;
    }
    Account account = accountRepository.findByEmail(email);
    if (account != null) {
      Long acctId = account.getId();
      List<Investor> investors =
          investorRepository.findAll().stream()
              .filter(i -> acctId.equals(i.getAccountId()))
              .collect(Collectors.toList());
      for (Investor investor : investors) {
        // 연관된 청약 먼저 삭제 (외래키 제약 때문) - deletedAt 관계없이 모두 삭제
        List<SecuritiesAccount> accounts =
            securitiesAccountRepository.findByInvestorId(investor.getId());
        for (SecuritiesAccount acc : accounts) {
          subscriptionRepository.deleteAll(
              subscriptionRepository.findAllBySecuritiesAccountIdOrderByCreatedAtDesc(acc.getId()));
        }
        securitiesAccountRepository.deleteAll(accounts);
        createdAccounts.removeAll(accounts);
      }
      investorRepository.deleteAll(investors);
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  private void cleanupFirms() {
    List<SecuritiesFirm> toDelete =
        createdFirms.stream().filter(f -> f.getId() != null).collect(Collectors.toList());
    if (!toDelete.isEmpty()) {
      securitiesFirmRepository.deleteAll(toDelete);
    }
  }

  private void signupAndLogin() throws Exception {
    AuthTestSupport.signup(mockMvc, objectMapper, session, testEmail, testPassword, testName);
    accountId = accountRepository.findByEmail(testEmail).getId();
    AuthTestSupport.login(mockMvc, objectMapper, session, testEmail, testPassword);
  }

  private Long registerFirm() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("한국투자증권" + ThreadLocalRandom.current().nextLong())
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdFirms.add(firm);
    return firm.getId();
  }

  private Long registerInvestorAndGetId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "displayName", "계좌투자자",
                                "relationship", "SELF")))
                    .session(session))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("테스트 사전 준비: 투자자 등록은 201이어야 합니다").isEqualTo(201);
    return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
  }

  private Long createOtherAccountInvestor() {
    long nonce = ThreadLocalRandom.current().nextLong();
    otherEmail = "other-account-owner-" + nonce + "@example.com";
    LocalDateTime now = LocalDateTime.now();
    Account other =
        accountRepository.save(
            Account.builder()
                .email(otherEmail)
                .passwordHash("encodedPasswordHash")
                .name("다른계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    Investor saved =
        investorRepository.save(
            Investor.builder()
                .accountId(other.getId())
                .displayName("다른계정투자자")
                .relationship("OTHER")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return saved.getId();
  }

  private MvcResult registerSecuritiesAccount(Map<String, ?> request) throws Exception {
    return mockMvc
        .perform(
            post("/securities-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(session))
        .andReturn();
  }

  private MvcResult updateSecuritiesAccount(Long id, Map<String, ?> request) throws Exception {
    return mockMvc
        .perform(
            patch("/securities-accounts/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(session))
        .andReturn();
  }

  private String utf8Body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  void registerSecuritiesAccountWithMissingRequiredFieldsReturns400ValidationError()
      throws Exception {
    // Given: 인증된 계정이 투자자/증권사를 소유하고 있음 (AC-002)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long securitiesFirmId = registerFirm();

    // When/Then: investorId 누락 시 400 + VALIDATION_ERROR (AC-002)
    MvcResult noInvestor =
        registerSecuritiesAccount(
            Map.of(
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(noInvestor.getResponse().getStatus())
        .as("investorId 누락 시 400 Bad Request를 반환해야 합니다 (AC-002)")
        .isEqualTo(400);
    Map<String, Object> noInvestorBody = objectMapper.readValue(utf8Body(noInvestor), Map.class);
    assertThat(noInvestorBody.get("error"))
        .as("investorId 누락 시 에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
        .isEqualTo("VALIDATION_ERROR");

    // When/Then: securitiesFirmId 누락 시 400 + VALIDATION_ERROR (AC-002)
    MvcResult noFirm =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(noFirm.getResponse().getStatus())
        .as("securitiesFirmId 누락 시 400 Bad Request를 반환해야 합니다 (AC-002)")
        .isEqualTo(400);
    Map<String, Object> noFirmBody = objectMapper.readValue(utf8Body(noFirm), Map.class);
    assertThat(noFirmBody.get("error"))
        .as("securitiesFirmId 누락 시 에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
        .isEqualTo("VALIDATION_ERROR");

    // When/Then: accountNumber 누락/공백 시 신규 생성 또는 재사용 (AC-001, AC-002 변경: 선택 필드)
    MvcResult noNumber =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId));
    assertThat(noNumber.getResponse().getStatus())
        .as("accountNumber 미제공 시 기존 계좌가 없으면 201 Created로 신규 생성(null) (AC-002)")
        .isEqualTo(201);
    Map<String, Object> noNumberBody = objectMapper.readValue(utf8Body(noNumber), Map.class);
    assertThat(noNumberBody.get("accountNumber")).as("계좌번호가 null로 생성되어야 합니다").isNull();

    // And: 레코드가 생성됨 (AC-002)
    assertThat(securitiesAccountRepository.findByInvestorId(investorId))
        .as("accountNumber 미제공 시에도 계좌가 생성되어야 합니다 (AC-002)")
        .hasSize(1);
  }

  @Test
  void registerSecuritiesAccountWithUnknownInvestorReturns404() throws Exception {
    // Given: 인증된 계정이 존재하고, 존재하지 않는 투자자 ID가 전달됨 (AC-003)
    signupAndLogin();
    Long securitiesFirmId = registerFirm();

    var request =
        Map.of(
            "investorId", 99999999L,
            "securitiesFirmId", securitiesFirmId,
            "accountNumber", ACCOUNT_NUMBER);

    // When: POST /securities-accounts 요청을 보냄
    MvcResult result = registerSecuritiesAccount(request);

    // Then: 404 Not Found (AC-003)
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 투자자로 등록 시 404 Not Found를 반환해야 합니다 (AC-003)").isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다 (AC-003)").isEqualTo("NOT_FOUND");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void registerSecuritiesAccountWithUnknownSecuritiesFirmReturns404() throws Exception {
    // Given: 인증된 계정이 유효한 투자자를 소유하고, 존재하지 않는 증권사 ID가 전달됨 (AC-004)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();

    var request =
        Map.of(
            "investorId", investorId,
            "securitiesFirmId", 99999999L,
            "accountNumber", ACCOUNT_NUMBER);

    // When: POST /securities-accounts 요청을 보냄
    MvcResult result = registerSecuritiesAccount(request);

    // Then: 404 Not Found (AC-004)
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 증권사로 등록 시 404 Not Found를 반환해야 합니다 (AC-004)").isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다 (AC-004)").isEqualTo("NOT_FOUND");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void registerSecuritiesAccountWithInvestorOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 다른 계정 B의 투자자 ID가 알려져 있음 (AC-005)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();
    Long securitiesFirmId = registerFirm();

    var request =
        Map.of(
            "investorId", otherInvestorId,
            "securitiesFirmId", securitiesFirmId,
            "accountNumber", ACCOUNT_NUMBER);

    // When: 계정 A가 계정 B의 투자자 ID로 POST /securities-accounts 요청을 보냄
    MvcResult result = registerSecuritiesAccount(request);

    // Then: 403 Forbidden + FORBIDDEN (AC-005)
    int status = result.getResponse().getStatus();
    assertThat(status).as("다른 계정 소유 투자자로 등록 시 403 Forbidden을 반환해야 합니다 (AC-005)").isEqualTo(403);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다 (AC-005)").isEqualTo("FORBIDDEN");
    assertThat(body.get("message")).isNotNull();

    // And: 레코드가 생성되지 않음 (AC-005)
    assertThat(securitiesAccountRepository.findByInvestorId(otherInvestorId))
        .as("소유권 없는 요청은 증권계좌를 생성하면 안 됩니다 (AC-005)")
        .isEmpty();
  }

  @Test
  void listSecuritiesAccountsWithInvestorOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 다른 계정 B의 투자자 ID가 알려져 있음 (목록 소유권 검증)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();

    // When: 계정 A가 계정 B의 투자자 ID로 GET /securities-accounts 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                get("/securities-accounts")
                    .param("investorId", String.valueOf(otherInvestorId))
                    .session(session))
            .andReturn();

    // Then: 403 Forbidden + FORBIDDEN (목록 소유권 검증)
    assertThat(result.getResponse().getStatus())
        .as("다른 계정 소유 투자자의 계좌 목록 조회 시 403 Forbidden을 반환해야 합니다")
        .isEqualTo(403);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다").isEqualTo("FORBIDDEN");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void listSecuritiesAccountsWithNonexistentInvestorReturns404() throws Exception {
    // Given: 인증된 계정이 존재하고, 존재하지 않는 investorId가 주어짐 (등록 AC-003과 동일하게 404)
    signupAndLogin();

    // When: 없는 투자자 ID로 GET /securities-accounts 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(get("/securities-accounts").param("investorId", "999999").session(session))
            .andReturn();

    // Then: 404 Not Found
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 투자자의 계좌 목록 조회 시 404 Not Found를 반환해야 합니다")
        .isEqualTo(404);
  }

  @Test
  void getSecuritiesAccountReturnsOwnedAccountWithMaskedNumberAndFirmName() throws Exception {
    // Given: 인증된 계정이 특정 증권계좌를 소유하고 있음 (AC-007)
    signupAndLogin();
    Long securitiesFirmId = registerFirm();
    String firmName = securitiesFirmRepository.findById(securitiesFirmId).orElseThrow().getName();
    Long investorId = registerInvestorAndGetId();

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "accountName", "개인 MTS 계좌"));
    assertThat(registerResult.getResponse().getStatus())
        .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: GET /securities-accounts/{id} 요청을 보냄 (AC-007)
    MvcResult result =
        mockMvc.perform(get("/securities-accounts/{id}", accountId).session(session)).andReturn();

    // Then: 200 OK + 마스킹된 계좌번호와 증권사명 포함 (AC-007)
    assertThat(result.getResponse().getStatus())
        .as("본인 소유 계좌 상세 조회는 200 OK여야 합니다 (AC-007)")
        .isEqualTo(200);
    Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(((Number) response.get("id")).longValue()).isEqualTo(accountId);
    assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
    String masked = (String) response.get("accountNumber");
    assertThat(masked)
        .as("계좌번호는 마스킹된 값이어야 합니다 (AC-007)")
        .isEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));
    assertThat(masked).as("계좌번호는 원본 평문을 노출하면 안 됩니다 (AC-007)").isNotEqualTo(ACCOUNT_NUMBER);
    assertThat(response.get("securitiesFirmName"))
        .as("응답에 증권사명이 포함되어야 합니다 (SF-3)")
        .isEqualTo(firmName);
    assertThat(response.get("accountName")).isEqualTo("개인 MTS 계좌");
  }

  @Test
  void getSecuritiesAccountWithUnknownIdReturns404() throws Exception {
    // Given: 존재하지 않는 증권계좌 ID가 제공됨
    signupAndLogin();

    // When: GET /securities-accounts/{unknown_id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(get("/securities-accounts/{id}", 99999999L).session(session)).andReturn();

    // Then: 404 Not Found + NOT_FOUND
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 계좌 ID 조회 시 404 Not Found를 반환해야 합니다")
        .isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void getSecuritiesAccountOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 다른 계정 B가 소유한 증권계좌가 등록되어 있음
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();
    Long securitiesFirmId = registerFirm();

    LocalDateTime now = LocalDateTime.now();
    SecuritiesAccount otherAccount =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAccounts.add(otherAccount);

    // When: 계정 A가 계정 B의 증권계좌 ID로 GET /securities-accounts/{id} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(get("/securities-accounts/{id}", otherAccount.getId()).session(session))
            .andReturn();

    // Then: 403 Forbidden + FORBIDDEN
    assertThat(result.getResponse().getStatus())
        .as("다른 계정 소유 계좌 조회 시 403 Forbidden을 반환해야 합니다")
        .isEqualTo(403);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다").isEqualTo("FORBIDDEN");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void getAccountNumberReturnsPlaintextForOwnedAccount() throws Exception {
    // Given: 인증된 계정이 해당 증권계좌의 소유 투자자를 보유하고 있음 (AC-008)
    signupAndLogin();
    Long securitiesFirmId = registerFirm();
    Long investorId = registerInvestorAndGetId();

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(registerResult.getResponse().getStatus())
        .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: GET /securities-accounts/{id}/account-number 요청을 보냄 (AC-008)
    MvcResult result =
        mockMvc
            .perform(get("/securities-accounts/{id}/account-number", accountId).session(session))
            .andReturn();

    // Then: 200 OK + 계좌번호 전체가 평문으로 반환됨 (마스킹되지 않음) (AC-008)
    assertThat(result.getResponse().getStatus())
        .as("본인 소유 계좌의 계좌번호 노출 조회는 200 OK여야 합니다 (AC-008)")
        .isEqualTo(200);
    Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(((Number) response.get("id")).longValue()).isEqualTo(accountId);
    assertThat(response.get("accountNumber"))
        .as("계좌번호 전체가 평문(마스킹 안 됨)으로 반환되어야 합니다 (AC-008)")
        .isEqualTo(ACCOUNT_NUMBER);
    assertThat(response.get("accountNumber"))
        .as("평문은 마스킹된 형태와 달라야 합니다 (AC-008)")
        .isNotEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));

    // And: 노출 시각(exposedAt, ISO 8601)이 포함됨 (AC-008)
    String exposedAt = (String) response.get("exposedAt");
    assertThat(exposedAt).as("응답에 exposedAt이 포함되어야 합니다 (AC-008)").isNotNull();
    assertThat(Instant.parse(exposedAt)).as("exposedAt은 ISO 8601 형식이어야 합니다 (AC-008)").isNotNull();
  }

  @Test
  void getAccountNumberOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 다른 계정 B의 증권계좌 ID가 알려져 있음 (AC-009)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();
    Long securitiesFirmId = registerFirm();
    String otherPlainNumber = "777-666-555444";

    LocalDateTime now = LocalDateTime.now();
    SecuritiesAccount otherAccount =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt(otherPlainNumber))
                .accountName("다른계정계좌")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAccounts.add(otherAccount);

    // When: 계정 A가 계정 B의 증권계좌 ID로 GET /securities-accounts/{id}/account-number 요청을 보냄 (AC-009)
    MvcResult result =
        mockMvc
            .perform(
                get("/securities-accounts/{id}/account-number", otherAccount.getId())
                    .session(session))
            .andReturn();

    // Then: 403 Forbidden + FORBIDDEN (AC-009)
    assertThat(result.getResponse().getStatus())
        .as("다른 계정 소유 계좌의 계좌번호 노출 요청 시 403 Forbidden을 반환해야 합니다 (AC-009)")
        .isEqualTo(403);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다 (AC-009)").isEqualTo("FORBIDDEN");
    assertThat(body.get("message")).isNotNull();

    // And: 계좌번호 전체(평문)가 응답에 포함되지 않음 (AC-009)
    assertThat(utf8Body(result))
        .as("소유권 없는 요청 응답에 계좌번호 평문이 포함되면 안 됩니다 (AC-009)")
        .doesNotContain(otherPlainNumber);
  }

  @Test
  void getAccountNumberWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 계정이 존재하고, 존재하지 않는 증권계좌 ID가 제공됨
    signupAndLogin();

    // When: GET /securities-accounts/{unknown_id}/account-number 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(get("/securities-accounts/{id}/account-number", 99999999L).session(session))
            .andReturn();

    // Then: 404 Not Found + NOT_FOUND
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 계좌의 계좌번호 노출 요청 시 404 Not Found를 반환해야 합니다")
        .isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void detailAndListStillShowSecuritiesFirmNameAfterFirmIsDeactivated() throws Exception {
    // Given: 계좌가 등록된 증권사가 이후 비활성화됨 (Master 수정 검증 - 표시용 조회는
    // deletedAt 필터를 쓰면 안 된다. architecture.md: "증권사 비활성화 시 기존 계좌는 유지")
    signupAndLogin();
    Long securitiesFirmId = registerFirm();
    Long investorId = registerInvestorAndGetId();

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();

    SecuritiesFirm firm = securitiesFirmRepository.findById(securitiesFirmId).orElseThrow();
    firm.deactivate(LocalDateTime.now());
    securitiesFirmRepository.save(firm);

    // When: 상세 조회
    MvcResult detailResult =
        mockMvc.perform(get("/securities-accounts/" + accountId).session(session)).andReturn();

    // Then: 200 OK이며 securitiesFirmName이 null이 아니라 비활성화 전 이름 그대로 표시됨
    assertThat(detailResult.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> detailBody = objectMapper.readValue(utf8Body(detailResult), Map.class);
    assertThat(detailBody.get("securitiesFirmName"))
        .as("증권사가 비활성화돼도 기존 연결 계좌의 securitiesFirmName은 null이 되면 안 됩니다")
        .isEqualTo(firm.getName());

    // And: 목록 조회에서도 동일하게 이름이 유지됨
    MvcResult listResult =
        mockMvc
            .perform(
                get("/securities-accounts")
                    .param("investorId", String.valueOf(investorId))
                    .session(session))
            .andReturn();
    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> listBody = objectMapper.readValue(utf8Body(listResult), List.class);
    assertThat(listBody).hasSize(1);
    assertThat(listBody.get(0).get("securitiesFirmName"))
        .as("목록 조회에서도 증권사 비활성화 후 이름이 유지되어야 합니다")
        .isEqualTo(firm.getName());
  }

  @Test
  void updateSecuritiesAccountWithValidRequestReturns200AndUpdatesAccountNameAndFirm()
      throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있고, 변경 대상 증권사는 ACTIVE 상태임 (SF-5)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long originalFirmId = registerFirm();
    Long newFirmId = registerFirm();

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", originalFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "accountName", "개인 MTS 계좌"));
    assertThat(registerResult.getResponse().getStatus())
        .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: PATCH /securities-accounts/{id}로 accountName과 securitiesFirmId를 변경함 (SF-5)
    MvcResult result =
        updateSecuritiesAccount(
            accountId, Map.of("accountName", "변경된 계좌명", "securitiesFirmId", newFirmId));

    // Then: 200 OK + 변경된 accountName/securitiesFirmId가 응답에 반영됨 (SF-5)
    assertThat(result.getResponse().getStatus())
        .as("정상 수정 요청은 200 OK를 반환해야 합니다 (SF-5)")
        .isEqualTo(200);
    Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(response.get("accountName"))
        .as("accountName이 변경되어야 합니다 (SF-5)")
        .isEqualTo("변경된 계좌명");
    assertThat(((Number) response.get("securitiesFirmId")).longValue())
        .as("securitiesFirmId가 변경되어야 합니다 (SF-5)")
        .isEqualTo(newFirmId);
    String masked = (String) response.get("accountNumber");
    assertThat(masked)
        .as("계좌번호는 여전히 마스킹된 값이어야 합니다 (SF-5)")
        .isEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));

    // And: DB에도 변경 사항이 반영됨 (SF-5)
    SecuritiesAccount updated = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(updated.getAccountName()).isEqualTo("변경된 계좌명");
    assertThat(updated.getSecuritiesFirmId()).isEqualTo(newFirmId);
  }

  @Test
  void updateSecuritiesAccountWithExistingAccountNumberReturns409() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있고 계좌번호가 이미 등록됨 (AC-007)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", firmId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(registerResult.getResponse().getStatus())
        .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: 이미 계좌번호가 있는 계좌에 다른 계좌번호로 PATCH 요청을 보냄 (AC-007)
    MvcResult result =
        updateSecuritiesAccount(
            accountId,
            Map.of(
                "accountName", "새 이름",
                "accountNumber", "000-000-000000"));

    // Then: 409 Conflict + 에러 메시지 (AC-007)
    assertThat(result.getResponse().getStatus())
        .as("기존 계좌번호가 있을 때 다른 번호로 변경 시도 시 409 반환 (AC-007)")
        .isEqualTo(409);
    Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(response.get("error")).isEqualTo("CONFLICT");
    assertThat(response.get("message")).isEqualTo("이미 계좌번호가 등록되어 있습니다");

    // And: DB에 저장된 실제 계좌번호도 변경되지 않음
    SecuritiesAccount updated = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(accountNumberEncryptor.decrypt(updated.getAccountNumber()))
        .as("DB의 계좌번호도 변경되면 안 됩니다")
        .isEqualTo(ACCOUNT_NUMBER);
  }

  @Test
  void updateSecuritiesAccountWithNonexistentSecuritiesFirmReturns400() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있음 (spec AC-011: 수정 시 없는 증권사는 400)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", firmId,
                "accountNumber", ACCOUNT_NUMBER));
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: 존재하지 않는 증권사 ID로 PATCH를 보냄
    MvcResult result = updateSecuritiesAccount(accountId, Map.of("securitiesFirmId", 999999));

    // Then: 400 Bad Request (등록의 404와 다른 것은 spec이 의도한 비대칭)
    assertThat(result.getResponse().getStatus())
        .as("수정 시 존재하지 않는 증권사는 400 Bad Request를 반환해야 합니다 (AC-011)")
        .isEqualTo(400);
  }

  @Test
  void updateSecuritiesAccountWithInactiveSecuritiesFirmReturns400() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있고, 변경하려는 증권사가 INACTIVE 상태임 (SF-5)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long originalFirmId = registerFirm();
    Long inactiveFirmId = registerFirm();
    SecuritiesFirm inactiveFirm = securitiesFirmRepository.findById(inactiveFirmId).orElseThrow();
    inactiveFirm.deactivate(LocalDateTime.now());
    securitiesFirmRepository.save(inactiveFirm);

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", originalFirmId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(registerResult.getResponse().getStatus())
        .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: PATCH로 INACTIVE 증권사로 변경을 시도함 (SF-5)
    MvcResult result =
        updateSecuritiesAccount(accountId, Map.of("securitiesFirmId", inactiveFirmId));

    // Then: 400 Bad Request + VALIDATION_ERROR, 레코드는 변경되지 않음 (SF-5)
    assertThat(result.getResponse().getStatus())
        .as("INACTIVE 증권사로 변경 시도 시 400 Bad Request를 반환해야 합니다 (SF-5)")
        .isEqualTo(400);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다 (SF-5)")
        .isEqualTo("VALIDATION_ERROR");
    assertThat(body.get("message")).isNotNull();

    SecuritiesAccount unchanged = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(unchanged.getSecuritiesFirmId())
        .as("INACTIVE 증권사 변경 시도는 반영되면 안 됩니다 (SF-5)")
        .isEqualTo(originalFirmId);
  }

  @Test
  void updateSecuritiesAccountOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 다른 계정 B가 소유한 증권계좌가 등록되어 있음 (SF-5)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();
    Long securitiesFirmId = registerFirm();

    LocalDateTime now = LocalDateTime.now();
    SecuritiesAccount otherAccount =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAccounts.add(otherAccount);

    // When: 계정 A가 계정 B의 증권계좌 ID로 PATCH /securities-accounts/{id} 요청을 보냄 (SF-5)
    MvcResult result =
        updateSecuritiesAccount(otherAccount.getId(), Map.of("accountName", "탈취 시도"));

    // Then: 403 Forbidden + FORBIDDEN, 레코드는 변경되지 않음 (SF-5)
    assertThat(result.getResponse().getStatus())
        .as("다른 계정 소유 계좌 수정 시도 시 403 Forbidden을 반환해야 합니다 (SF-5)")
        .isEqualTo(403);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다 (SF-5)").isEqualTo("FORBIDDEN");
    assertThat(body.get("message")).isNotNull();

    SecuritiesAccount unchanged =
        securitiesAccountRepository.findById(otherAccount.getId()).orElseThrow();
    assertThat(unchanged.getAccountName())
        .as("소유권 없는 요청은 계좌를 변경하면 안 됩니다 (SF-5)")
        .isEqualTo("다른계정계좌");
  }

  @Test
  void updateSecuritiesAccountWithUnknownIdReturns404() throws Exception {
    // Given: 존재하지 않는 증권계좌 ID가 제공됨 (SF-5)
    signupAndLogin();

    // When: PATCH /securities-accounts/{unknown_id} 요청을 보냄 (SF-5)
    MvcResult result = updateSecuritiesAccount(99999999L, Map.of("accountName", "새 이름"));

    // Then: 404 Not Found + NOT_FOUND (SF-5)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 계좌 수정 시도 시 404 Not Found를 반환해야 합니다 (SF-5)")
        .isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다 (SF-5)").isEqualTo("NOT_FOUND");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void deactivateSecuritiesAccountReturns204AndMarksInactiveInDb() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있음 (SF-6)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult registerResult =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", firmId,
                "accountNumber", ACCOUNT_NUMBER));
    assertThat(registerResult.getResponse().getStatus())
        .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    long accountId =
        ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id"))
            .longValue();
    SecuritiesAccount before = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(before.getStatus()).as("비활성화 전 status는 ACTIVE여야 합니다").isEqualTo(RecordStatus.ACTIVE);
    assertThat(before.getDeletedAt()).as("비활성화 전 deletedAt은 null이어야 합니다").isNull();
    LocalDateTime oldUpdatedAt = LocalDateTime.now().minusDays(1);
    before.changeDetails(null, null, oldUpdatedAt);
    securitiesAccountRepository.save(before);
    createdAccounts.add(before);

    // When: DELETE /securities-accounts/{id} 요청을 보냄 (SF-6)
    MvcResult result =
        mockMvc
            .perform(delete("/securities-accounts/{id}", accountId).session(session))
            .andReturn();

    // Then: 204 No Content, 본문 없음 (SF-6)
    assertThat(result.getResponse().getStatus())
        .as("비활성화 성공 시 204 No Content를 반환해야 합니다 (SF-6)")
        .isEqualTo(204);
    assertThat(result.getResponse().getContentAsByteArray())
        .as("204 응답의 본문은 비어 있어야 합니다 (SF-6)")
        .isEmpty();

    // And: DB에서 status=INACTIVE, deletedAt 설정, updatedAt 갱신 확인 (SF-6)
    SecuritiesAccount inDb = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(inDb.getStatus())
        .as("비활성화 후 status는 INACTIVE여야 합니다 (SF-6)")
        .isEqualTo(RecordStatus.INACTIVE);
    assertThat(inDb.getDeletedAt()).as("비활성화 후 deletedAt이 설정되어야 합니다 (SF-6)").isNotNull();
    assertThat(inDb.getUpdatedAt()).as("비활성화 후 updatedAt이 갱신되어야 합니다 (SF-6)").isAfter(oldUpdatedAt);
  }

  @Test
  void deactivateSecuritiesAccountWithUnknownIdReturns404() throws Exception {
    // Given: 존재하지 않는 증권계좌 ID가 제공됨 (SF-6)
    signupAndLogin();

    // When: DELETE /securities-accounts/{unknown_id} 요청을 보냄 (SF-6)
    MvcResult result =
        mockMvc
            .perform(delete("/securities-accounts/{id}", 99999999L).session(session))
            .andReturn();

    // Then: 404 Not Found + NOT_FOUND (SF-6)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 계좌 비활성화 시도 시 404 Not Found를 반환해야 합니다 (SF-6)")
        .isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다 (SF-6)").isEqualTo("NOT_FOUND");
    assertThat(body.get("message")).isNotNull();
  }

  @Test
  void registerNormalizesAccountNumberAndRejectsInvalidFormat() throws Exception {
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    long id = register(investorId, firmId, " 123 456-7890 123 ");
    SecuritiesAccount saved = securitiesAccountRepository.findById(id).orElseThrow();
    assertThat(accountNumberEncryptor.decrypt(saved.getAccountNumber())).isEqualTo("1234567890123");

    for (String bad : new String[] {"123456789", "12345678901234567", "1234-abcd-56789"}) {
      MvcResult r =
          registerSecuritiesAccount(
              Map.of("investorId", investorId, "securitiesFirmId", firmId, "accountNumber", bad));
      assertThat(r.getResponse().getStatus()).as(bad).isEqualTo(400);
    }
  }

  private long register(Long investorId, Long firmId, String number) throws Exception {
    MvcResult r =
        registerSecuritiesAccount(
            Map.of(
                "investorId", investorId,
                "securitiesFirmId", firmId,
                "accountNumber", number));
    return ((Number) objectMapper.readValue(utf8Body(r), Map.class).get("id")).longValue();
  }

  // === 회귀 테스트: account-presence SF-1 ===

  @Test
  void responseContainsAccountNumberKeyWithNullWhenNoAccountNumber() throws Exception {
    // Given: accountNumber 없이 계좌 생성
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    // When: accountNumber 생략하여 POST
    MvcResult result =
        registerSecuritiesAccount(Map.of("investorId", investorId, "securitiesFirmId", firmId));

    // Then: 201 + accountNumber 키가 null 값으로 포함됨
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body).containsKey("accountNumber");
    assertThat(body.get("accountNumber")).isNull();

    // And: DB에도 null 저장됨
    Long savedId = ((Number) body.get("id")).longValue();
    SecuritiesAccount saved = securitiesAccountRepository.findById(savedId).orElseThrow();
    assertThat(saved.getAccountNumber()).isNull();
  }

  @Test
  void registerOmittedAccountNumberReusesExistingOldestAccount() throws Exception {
    // Given: 기존 활성 계좌가 있는 투자자
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    doReturn(Instant.parse("2026-11-10T00:00:00.123456789Z")).when(clock).instant();

    // 첫 번째 계좌 생성 (번호 있음)
    MvcResult first =
        registerSecuritiesAccount(
            Map.of(
                "investorId",
                investorId,
                "securitiesFirmId",
                firmId,
                "accountNumber",
                "111111111111"));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long firstId =
        ((Number) objectMapper.readValue(utf8Body(first), Map.class).get("id")).longValue();
    // DB에 저장된 시각을 기준으로 비교한다(나노초 입력은 PostgreSQL에서 마이크로초로 반올림된다).
    LocalDateTime firstUpdatedAt =
        securitiesAccountRepository.findById(firstId).orElseThrow().getUpdatedAt();
    doReturn(Instant.parse("2026-11-10T00:00:01.123456789Z")).when(clock).instant();

    // When: accountNumber 생략하여 다시 POST (재사용 경로)
    MvcResult reused =
        registerSecuritiesAccount(Map.of("investorId", investorId, "securitiesFirmId", firmId));

    // Then: 201 + 기존 계좌 재사용 (동일 ID, updatedAt 불변)
    assertThat(reused.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> reusedBody = objectMapper.readValue(utf8Body(reused), Map.class);
    assertThat(((Number) reusedBody.get("id")).longValue()).isEqualTo(firstId);
    assertThat(LocalDateTime.parse((String) reusedBody.get("updatedAt"))).isEqualTo(firstUpdatedAt);
    assertThat(reusedBody.get("accountNumber")).isEqualTo(AccountNumberMasker.mask("111111111111"));

    // And: DB 레코드 수도 1개 유지
    assertThat(securitiesAccountRepository.findByInvestorId(investorId)).hasSize(1);
    assertThat(securitiesAccountRepository.findById(firstId).orElseThrow().getUpdatedAt())
        .isEqualTo(firstUpdatedAt);
  }

  @Test
  void registerBlankAccountNumberReusesExistingAccount() throws Exception {
    // Given: 기존 활성 계좌가 있는 투자자
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    registerSecuritiesAccount(
        Map.of(
            "investorId", investorId, "securitiesFirmId", firmId, "accountNumber", "222222222222"));

    // When: accountNumber를 빈 문자열로 전송
    MvcResult result =
        registerSecuritiesAccount(
            Map.of("investorId", investorId, "securitiesFirmId", firmId, "accountNumber", ""));

    // Then: 재사용됨 (빈 문자열도 미입력으로 처리)
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("accountNumber")).isEqualTo(AccountNumberMasker.mask("222222222222"));
  }

  @Test
  void registerAccountNameTooLongReturns400EvenWithoutAccountNumber() throws Exception {
    // Given: accountNumber 없이 256자 계좌명으로 요청
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    String longName = "n".repeat(256);

    // When: POST
    MvcResult result =
        registerSecuritiesAccount(
            Map.of("investorId", investorId, "securitiesFirmId", firmId, "accountName", longName));

    // Then: 400 (계좌명 길이 검증이 분기 전에 수행됨)
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void patchNullToNumberSucceedsWithSameIdAndMask() throws Exception {
    // Given: accountNumber 없이 생성된 계좌
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    doReturn(Instant.parse("2026-11-10T00:00:00.123456789Z")).when(clock).instant();

    MvcResult created =
        registerSecuritiesAccount(Map.of("investorId", investorId, "securitiesFirmId", firmId));
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesAccount createdAccount =
        securitiesAccountRepository.findById(accountId).orElseThrow();
    LocalDateTime createdAt = createdAccount.getCreatedAt();
    createdAccounts.add(createdAccount);
    doReturn(Instant.parse("2026-11-10T00:00:01.123456789Z")).when(clock).instant();

    // When: null -> 번호로 PATCH
    MvcResult patched = updateSecuritiesAccount(accountId, Map.of("accountNumber", "333333333333"));

    // Then: 200 + 동일 ID + 암호화 저장 + 마스크 응답
    assertThat(patched.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = objectMapper.readValue(utf8Body(patched), Map.class);
    assertThat(((Number) body.get("id")).longValue()).isEqualTo(accountId);
    assertThat(body.get("accountNumber")).isEqualTo(AccountNumberMasker.mask("333333333333"));
    assertThat(LocalDateTime.parse((String) body.get("createdAt"))).isEqualTo(createdAt);

    // And: DB에 암호문 저장됨
    SecuritiesAccount saved = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    assertThat(saved.getAccountNumber()).isNotNull();
    assertThat(accountNumberEncryptor.decrypt(saved.getAccountNumber())).isEqualTo("333333333333");
  }

  @Test
  void patchBlankAccountNumberReturns400() throws Exception {
    // Given: accountNumber 없이 생성된 계좌
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult created =
        registerSecuritiesAccount(Map.of("investorId", investorId, "securitiesFirmId", firmId));
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: 빈 문자열로 PATCH
    MvcResult result = updateSecuritiesAccount(accountId, Map.of("accountNumber", ""));

    // Then: 400
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void patchAccountNumberWhenAlreadyExistsReturns409() throws Exception {
    // Given: 이미 계좌번호가 있는 계좌
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult created =
        registerSecuritiesAccount(
            Map.of(
                "investorId",
                investorId,
                "securitiesFirmId",
                firmId,
                "accountNumber",
                "444444444444"));
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: 다른 번호로 PATCH 시도
    MvcResult result = updateSecuritiesAccount(accountId, Map.of("accountNumber", "555555555555"));

    // Then: 409 + 기존 번호 보존
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("CONFLICT");
    assertThat(body.get("message")).isEqualTo("이미 계좌번호가 등록되어 있습니다");

    SecuritiesAccount saved = securitiesAccountRepository.findById(accountId).orElseThrow();
    assertThat(accountNumberEncryptor.decrypt(saved.getAccountNumber())).isEqualTo("444444444444");
  }

  @Test
  void patchAccountNameTooLongReturns400() throws Exception {
    // Given: accountNumber 없는 계좌
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult created =
        registerSecuritiesAccount(Map.of("investorId", investorId, "securitiesFirmId", firmId));
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: 256자 계좌명으로 PATCH
    String longName = "n".repeat(256);
    MvcResult result = updateSecuritiesAccount(accountId, Map.of("accountName", longName));

    // Then: 400
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  void deleteWithSubscriptionHistoryReturns409EvenIfCancelled() throws Exception {
    // Given: 청약 이력(취소 포함)이 있는 계좌
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult created =
        registerSecuritiesAccount(
            Map.of(
                "investorId",
                investorId,
                "securitiesFirmId",
                firmId,
                "accountNumber",
                "666666666666"));
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // 청약 생성 후 취소 (CANCELLED 상태)
    Long subscriptionId = createSubscription(accountId, investorId);
    Subscription sub = subscriptionRepository.findById(subscriptionId).orElseThrow();
    sub.cancel(LocalDateTime.now());
    subscriptionRepository.save(sub);

    // When: DELETE 시도
    MvcResult result =
        mockMvc
            .perform(delete("/securities-accounts/{id}", accountId).session(session))
            .andReturn();

    // Then: 409 (취소된 청약도 이력으로 간주)
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("CONFLICT");
    assertThat(body.get("message")).isEqualTo("연결된 청약 이력이 있어 비활성화할 수 없습니다");
  }

  @Test
  void deactivateSecuritiesAccountOwnedByOtherAccountReturns403() throws Exception {
    // Given: 다른 계정 소유 계좌
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();
    Long securitiesFirmId = registerFirm();

    LocalDateTime now = LocalDateTime.now();
    SecuritiesAccount otherAccount =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt("123456789012"))
                .accountName("다른계정계좌")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAccounts.add(otherAccount);

    // When: DELETE 시도
    MvcResult result =
        mockMvc
            .perform(delete("/securities-accounts/{id}", otherAccount.getId()).session(session))
            .andReturn();

    // Then: 403
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("FORBIDDEN");
  }

  @Test
  void concurrentRegisterCreatesOnlyOneAccount() throws Exception {
    // Given: 동시 POST 요청 환경
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    // When: 10개 스레드로 동시 POST (accountNumber 없음)
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(10);
    AtomicReference<Integer> successCount = new AtomicReference<>(0);
    AtomicReference<Integer> conflictCount = new AtomicReference<>(0);

    for (int i = 0; i < 10; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              MvcResult result =
                  registerSecuritiesAccount(
                      Map.of("investorId", investorId, "securitiesFirmId", firmId));
              if (result.getResponse().getStatus() == 201) {
                successCount.updateAndGet(v -> v + 1);
              } else if (result.getResponse().getStatus() == 409) {
                conflictCount.updateAndGet(v -> v + 1);
              }
            } catch (Exception e) {
              throw new RuntimeException(e);
            } finally {
              endLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();

    // Then: 모든 POST가 재사용으로 201 성공하고 계좌는 정확히 1개만 생성됨
    assertThat(successCount.get()).isEqualTo(10);
    assertThat(conflictCount.get()).isEqualTo(0);
    List<SecuritiesAccount> accounts = securitiesAccountRepository.findByInvestorId(investorId);
    assertThat(accounts).hasSize(1);
  }

  @Test
  void concurrentPatchNullToNumberOneSuccessOneConflict() throws Exception {
    // Given: accountNumber 없는 계좌
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();

    MvcResult created =
        registerSecuritiesAccount(Map.of("investorId", investorId, "securitiesFirmId", firmId));
    Long accountId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

    // When: 두 스레드가 동시에 null->번호 PATCH (서로 다른 번호)
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(2);
    AtomicReference<Integer> successCount = new AtomicReference<>(0);
    AtomicReference<Integer> conflictCount = new AtomicReference<>(0);

    executor.submit(
        () -> {
          try {
            startLatch.await();
            MvcResult result =
                updateSecuritiesAccount(accountId, Map.of("accountNumber", "888888888888"));
            if (result.getResponse().getStatus() == 200) {
              successCount.updateAndGet(v -> v + 1);
            } else if (result.getResponse().getStatus() == 409) {
              conflictCount.updateAndGet(v -> v + 1);
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            endLatch.countDown();
          }
        });

    executor.submit(
        () -> {
          try {
            startLatch.await();
            MvcResult result =
                updateSecuritiesAccount(accountId, Map.of("accountNumber", "999999999999"));
            if (result.getResponse().getStatus() == 200) {
              successCount.updateAndGet(v -> v + 1);
            } else if (result.getResponse().getStatus() == 409) {
              conflictCount.updateAndGet(v -> v + 1);
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            endLatch.countDown();
          }
        });

    startLatch.countDown();
    assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();

    // Then: 하나 성공(200), 하나 409, 번호 영구 보존
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(1);

    SecuritiesAccount saved = securitiesAccountRepository.findById(accountId).orElseThrow();
    String finalNumber = accountNumberEncryptor.decrypt(saved.getAccountNumber());
    assertThat(finalNumber).isIn("888888888888", "999999999999");
  }

  private Long createSubscription(Long accountId, Long investorId) {
    LocalDateTime now = LocalDateTime.now();
    Subscription sub =
        Subscription.builder()
            .investorId(investorId)
            .securitiesAccountId(accountId)
            .ipoId(999999L)
            .quantity(10L)
            .depositAmount(500000L)
            .subscribedAt(now)
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();
    return subscriptionRepository.save(sub).getId();
  }
}
