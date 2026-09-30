package com.vibeaiteam.costrecording.controller.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC 인용: docs/spec/cost-recording.md - AC-001: 비용 등록 성공 — 존재하는 Subscription과 유효한 비용종류, 금액, 발생일시가
 * 제공되면 201 + ACTIVE 기록 생성 - AC-002: 비용 등록 실패 (필수값 누락) — subscriptionId, costType, amount,
 * incurredAt 중 하나 이상 누락 시 400 VALIDATION_ERROR - AC-003: 비용 등록 실패 (금액 값 오류) — amount < 0이면 400
 * VALIDATION_ERROR - AC-004: 비용 등록 실패 (Subscription 미존재) — 존재하지 않는 subscriptionId → 404 NOT_FOUND -
 * AC-005: 동일 청약 다건 등록 성공 (1:N) — 하나의 Subscription에 여러 비용 등록 가능 - 소유권 검증: 다른 계정 소유의 subscriptionId로
 * 등록 시도 → 403 FORBIDDEN - 엣지케이스: 취소된 Subscription(CANCELLED) 참조 시 404 - 엣지케이스: memo 255자 초과 시 400
 * VALIDATION_ERROR - 엣지케이스: memo null 허용 - 엣지케이스: incurredAt 미래/과거 무관 허용 - 엣지케이스: status 필드 허용값 외
 * 입력 시 400 (Controller에서 검증).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class CostControllerTest {

  private static final String ACCOUNT_NUMBER = "123-456-789012";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;

  @Autowired private AccountNumberEncryptor accountNumberEncryptor;

  @Autowired private IpoRepository ipoRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private CostRepository costRepository;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;
  private String otherEmail;
  private final List<SecuritiesFirm> createdFirms = new ArrayList<>();
  private final List<Ipo> createdIpos = new ArrayList<>();

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "cost-owner-" + nonce + "@example.com";
    testPassword = "securePass123!";
    testName = "비용테스터";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    cleanupAccount(testEmail);
    cleanupAccount(otherEmail);
    if (!createdFirms.isEmpty()) {
      securitiesFirmRepository.deleteAll(createdFirms);
    }
    if (!createdIpos.isEmpty()) {
      ipoRepository.deleteAll(createdIpos);
    }
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
              .toList();
      for (Investor investor : investors) {
        List<SecuritiesAccount> accounts =
            securitiesAccountRepository.findByInvestorId(investor.getId());
        List<Subscription> subscriptions =
            subscriptionRepository.findAll().stream()
                .filter(s -> investor.getId().equals(s.getInvestorId()))
                .toList();
        // Cost 먼저 삭제 (FK 제약 때문에)
        for (Subscription sub : subscriptions) {
          List<Cost> costs =
              costRepository.findAll().stream()
                  .filter(c -> sub.getId().equals(c.getSubscriptionId()))
                  .toList();
          costRepository.deleteAll(costs);
        }
        subscriptionRepository.deleteAll(subscriptions);
        securitiesAccountRepository.deleteAll(accounts);
      }
      investorRepository.deleteAll(investors);
      accountRepository.delete(account);
    }
  }

  private void signupAndLogin() throws Exception {
    mockMvc
        .perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", testEmail,
                            "password", testPassword,
                            "name", testName)))
                .session(session))
        .andReturn();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", testEmail,
                            "password", testPassword)))
                .session(session))
        .andReturn();
  }

  private Long registerInvestorAndGetId() throws Exception {
    return registerInvestorAndGetId("비용투자자");
  }

  private Long registerInvestorAndGetId(String displayName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("displayName", displayName, "relationship", "SELF")))
                    .session(session))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("테스트 사전 준비: 투자자 등록은 201이어야 합니다").isEqualTo(201);
    return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
  }

  private Long registerFirm() {
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

  private Long registerSecuritiesAccount(Long investorId, Long firmId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/securities-accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "investorId", investorId,
                                "securitiesFirmId", firmId,
                                "accountNumber", ACCOUNT_NUMBER,
                                "accountName", "개인 MTS 계좌")))
                    .session(session))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .as("테스트 사전 준비: 증권계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
  }

  private Long createIpo() {
    return createIpo("기업A", "공모주A", "123456");
  }

  private Long createIpo(String companyName, String ipoName, String stockCode) {
    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        ipoRepository.save(
            Ipo.builder()
                .companyName(companyName)
                .ipoName(ipoName)
                .stockCode(stockCode)
                .lowerBandPrice(10000L)
                .upperBandPrice(12000L)
                .fixedPrice(11000L)
                .subscriptionStartDate(java.time.LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(java.time.LocalDate.of(2026, 10, 2))
                .listingDate(java.time.LocalDate.of(2026, 10, 15))
                .offeringShareCount(1000000L)
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdIpos.add(ipo);
    return ipo.getId();
  }

  private Long createSubscription(Long investorId, Long securitiesAccountId, Long ipoId) {
    LocalDateTime now = LocalDateTime.now();
    Subscription subscription =
        subscriptionRepository.save(
            Subscription.builder()
                .investorId(investorId)
                .securitiesAccountId(securitiesAccountId)
                .ipoId(ipoId)
                .quantity(100L)
                .depositAmount(1000000L)
                .subscribedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .memo("테스트 청약")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return subscription.getId();
  }

  private Long createOtherAccountInvestorAndAccount() {
    long nonce = ThreadLocalRandom.current().nextLong();
    otherEmail = "other-cost-owner-" + nonce + "@example.com";
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
    Long firmId = registerFirm();
    SecuritiesAccount account =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(saved.getId())
                .securitiesFirmId(firmId)
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .isDefault(true)
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return saved.getId();
  }

  private Long createOtherAccountSubscription() throws Exception {
    Long otherInvestorId = createOtherAccountInvestorAndAccount();
    List<SecuritiesAccount> otherAccounts =
        securitiesAccountRepository.findByInvestorId(otherInvestorId);
    assertThat(otherAccounts).hasSize(1);
    Long otherAccountId = otherAccounts.get(0).getId();
    Long ipoId = createIpo("기타기업", "기타공모주", "999999");
    return createSubscription(otherInvestorId, otherAccountId, ipoId);
  }

  private Map<String, Object> validRequest(Long subscriptionId) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("subscriptionId", subscriptionId);
    request.put("costType", "청약수수료");
    request.put("amount", 25000);
    request.put("incurredAt", "2026-09-10T10:00:00");
    request.put("memo", "OO증권 청약수수료");
    return request;
  }

  private MvcResult registerCost(Map<String, Object> request) throws Exception {
    return mockMvc
        .perform(
            post("/costs")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andReturn();
  }

  private Map<String, Object> readBody(MvcResult result) throws Exception {
    return objectMapper.readValue(utf8Body(result), Map.class);
  }

  private void assertValidationError(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertError(result, "VALIDATION_ERROR");
  }

  private void assertError(MvcResult result, String errorCode) throws Exception {
    Map<String, Object> response = readBody(result);
    assertThat(response.get("error")).isEqualTo(errorCode);
    assertThat(response.get("message")).isNotNull();
  }

  private String utf8Body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  void registerCostWithValidRequestReturns201AndSavedCost() throws Exception {
    // Given: 존재하는 Subscription과 유효한 비용종류, 금액, 발생일시가 제공됨 (AC-001)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When: POST /costs 요청을 보냄
    MvcResult result = registerCost(validRequest(subscriptionId));

    // Then: 201 Created와 함께 신규 ACTIVE 기록이 생성됨 (AC-001)
    assertThat(result.getResponse().getStatus())
        .as("유효한 비용 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)")
        .isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("id")).isNotNull();
    assertThat(((Number) response.get("subscriptionId")).longValue()).isEqualTo(subscriptionId);
    assertThat(response.get("costType")).isEqualTo("청약수수료");
    assertThat(((Number) response.get("amount")).longValue()).isEqualTo(25000L);
    assertThat(response.get("incurredAt")).isEqualTo("2026-09-10T10:00:00");
    assertThat(response.get("memo")).isEqualTo("OO증권 청약수수료");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(response.get("deletedAt")).isNull();
    assertThat(response.get("createdAt")).isNotNull();
    assertThat(response.get("updatedAt")).isNotNull();

    Cost saved = costRepository.findById(((Number) response.get("id")).longValue()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(saved.getDeletedAt()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void registerCostWithMissingRequiredFieldsReturns400() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고, 필수 필드 중 하나씩을 제거해 요청함 (AC-002)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When/Then: subscriptionId/costType/amount/incurredAt 각각 누락 시 400 (AC-002)
    for (String field : List.of("subscriptionId", "costType", "amount", "incurredAt")) {
      Map<String, Object> request = validRequest(subscriptionId);
      request.remove(field);
      assertValidationError(registerCost(request));
    }

    // And: 레코드가 생성되지 않음 (AC-002)
    assertThat(costRepository.findAll()).isEmpty();
  }

  @Test
  void registerCostWithNegativeAmountReturns400() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있음 (AC-003)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When/Then: amount < 0 시 400 VALIDATION_ERROR (AC-003)
    Map<String, Object> negativeAmount = validRequest(subscriptionId);
    negativeAmount.put("amount", -1);
    assertValidationError(registerCost(negativeAmount));

    // And: 레코드가 생성되지 않음 (AC-003)
    assertThat(costRepository.findAll()).isEmpty();
  }

  @Test
  void registerCostWithZeroAmountReturns201() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있음 — amount=0은 무료 비용으로 허용됨 (엣지케이스)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When: amount=0으로 요청
    Map<String, Object> zeroAmount = validRequest(subscriptionId);
    zeroAmount.put("amount", 0);
    MvcResult result = registerCost(zeroAmount);

    // Then: 201 Created — amount 0은 허용 (AC-003: 0 미만만 400)
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(((Number) response.get("amount")).longValue()).isEqualTo(0L);
  }

  @Test
  void registerCostWithUnknownSubscriptionReturns404() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고, 존재하지 않는 subscriptionId로 요청함 (AC-004)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When/Then: 존재하지 않는 subscriptionId로 요청 시 404 NOT_FOUND (AC-004)
    MvcResult unknownSub = registerCost(validRequest(99999999L));
    assertThat(unknownSub.getResponse().getStatus())
        .as("존재하지 않는 subscriptionId는 404를 반환해야 합니다 (AC-004)")
        .isEqualTo(404);
    assertError(unknownSub, "NOT_FOUND");

    // And: 레코드가 생성되지 않음 (AC-004)
    assertThat(costRepository.findAll()).isEmpty();
  }

  @Test
  void registerCostWithCancelledSubscriptionReturns404() throws Exception {
    // Given: 취소(CANCELLED)된 Subscription이 존재함 (엣지케이스: 취소된 청약 참조 시 404)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // Subscription을 CANCELLED로 변경
    Subscription cancelled = subscriptionRepository.findById(subscriptionId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    subscriptionRepository.saveAndFlush(cancelled);

    // When: 취소된 Subscription으로 비용 등록 시도
    MvcResult result = registerCost(validRequest(subscriptionId));

    // Then: 404 Not Found (취소된 Subscription 참조 불가)
    assertThat(result.getResponse().getStatus())
        .as("취소된 Subscription 참조 시 404를 반환해야 합니다 (엣지케이스)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");

    // And: 레코드가 생성되지 않음
    assertThat(costRepository.findAll()).isEmpty();
  }

  @Test
  void registerCostWithSubscriptionOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A와 다른 계정 B가 있고, B 소유의 Subscription ID가 알려져 있음 (소유권 검증)
    signupAndLogin();
    Long otherSubscriptionId = createOtherAccountSubscription();
    assertThat(otherSubscriptionId).isNotNull();

    // When/Then: 계정 A가 계정 B의 subscriptionId로 등록 시 403 FORBIDDEN
    MvcResult result = registerCost(validRequest(otherSubscriptionId));
    assertThat(result.getResponse().getStatus())
        .as("다른 계정 소유 Subscription으로 등록 시 403 Forbidden을 반환해야 합니다 (소유권 검증)")
        .isEqualTo(403);
    assertError(result, "FORBIDDEN");

    // And: 레코드가 생성되지 않음
    assertThat(costRepository.findAll()).isEmpty();
  }

  @Test
  void registerCostMultipleCostsForSameSubscriptionReturns201() throws Exception {
    // Given: 하나의 Subscription에 첫 번째 비용이 등록됨 (AC-005)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult first = registerCost(validRequest(subscriptionId));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> firstResponse = readBody(first);
    Long firstCostId = ((Number) firstResponse.get("id")).longValue();
    assertThat(firstResponse.get("costType")).isEqualTo("청약수수료");
    assertThat(firstResponse.get("status")).isEqualTo("ACTIVE");

    // When: 동일 Subscription에 두 번째 비용(계좌개설비)을 등록함 (AC-005)
    Map<String, Object> secondRequest = validRequest(subscriptionId);
    secondRequest.put("costType", "계좌개설비");
    secondRequest.put("amount", 15000);
    secondRequest.put("incurredAt", "2026-09-11T10:00:00");
    secondRequest.put("memo", "계좌 개설 비용");
    MvcResult second = registerCost(secondRequest);

    // Then: 201 Created와 함께 두 번째 비용도 ACTIVE 상태로 생성됨 (AC-005, 유니크 제약 없음)
    assertThat(second.getResponse().getStatus())
        .as("동일 Subscription에 여러 비용 등록은 201을 반환해야 합니다 (AC-005)")
        .isEqualTo(201);
    Map<String, Object> secondResponse = readBody(second);
    assertThat(secondResponse.get("id")).isNotNull();
    assertThat(secondResponse.get("costType")).isEqualTo("계좌개설비");
    assertThat(((Number) secondResponse.get("amount")).longValue()).isEqualTo(15000L);
    assertThat(secondResponse.get("status")).isEqualTo("ACTIVE");
    assertThat(((Number) secondResponse.get("subscriptionId")).longValue())
        .isEqualTo(subscriptionId);

    // DB에 두 건 모두 저장됨
    assertThat(costRepository.findAll()).hasSize(2);
  }

  @Test
  void registerCostWithNullMemoReturns201() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고 memo를 null로 요청함 (엣지케이스: memo null 허용)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When: memo 없이 요청 (또는 memo=null)
    Map<String, Object> request = validRequest(subscriptionId);
    request.put("memo", null);
    MvcResult result = registerCost(request);

    // Then: 201 Created — memo는 선택 필드로 null 허용
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("memo")).isNull();
  }

  @Test
  void registerCostWithMemoExceeding255CharsReturns400() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고 memo 256자로 요청함 (엣지케이스: memo 255자 상한)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When: memo 256자로 요청
    Map<String, Object> request = validRequest(subscriptionId);
    request.put("memo", "a".repeat(256));
    MvcResult result = registerCost(request);

    // Then: 400 VALIDATION_ERROR
    assertThat(result.getResponse().getStatus())
        .as("memo 255자 초과 시 400을 반환해야 합니다 (엣지케이스)")
        .isEqualTo(400);
    assertError(result, "VALIDATION_ERROR");
  }

  @Test
  void registerCostWithFutureIncurredAtReturns201() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고 발생일시를 미래로 설정함 (엣지케이스: incurredAt 미래/과거 무관)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When: 미래 발생일시로 요청
    Map<String, Object> request = validRequest(subscriptionId);
    request.put("incurredAt", "2030-12-31T23:59:59");
    MvcResult result = registerCost(request);

    // Then: 201 Created — incurredAt은 사후 기록이므로 과거/미래 구분 없이 허용
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("incurredAt")).isEqualTo("2030-12-31T23:59:59");
  }

  @Test
  void registerCostWithPastIncurredAtReturns201() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고 발생일시를 과거로 설정함 (엣지케이스: incurredAt 과거 허용)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // When: 과거 발생일시로 요청
    Map<String, Object> request = validRequest(subscriptionId);
    request.put("incurredAt", "2020-01-01T00:00:00");
    MvcResult result = registerCost(request);

    // Then: 201 Created
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("incurredAt")).isEqualTo("2020-01-01T00:00:00");
  }

  // ==================== SF-3: 비용 목록/상세 조회 테스트 (AC-006~AC-010, AC-016) ====================

  private MvcResult listCosts(Long subscriptionId, Long investorId, String investorName)
      throws Exception {
    StringBuilder url = new StringBuilder("/costs?");
    if (subscriptionId != null) {
      url.append("subscriptionId=").append(subscriptionId).append("&");
    }
    if (investorId != null) {
      url.append("investorId=").append(investorId).append("&");
    }
    if (investorName != null) {
      url.append("investorName=").append(investorName).append("&");
    }
    // 마지막 & 제거
    if (url.charAt(url.length() - 1) == '&') {
      url.deleteCharAt(url.length() - 1);
    }
    return mockMvc.perform(get(url.toString()).session(session)).andReturn();
  }

  private MvcResult getCostById(Long id) throws Exception {
    return mockMvc.perform(get("/costs/" + id).session(session)).andReturn();
  }

  private List<Map<String, Object>> readList(MvcResult result) throws Exception {
    return objectMapper.readValue(
        utf8Body(result),
        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
  }

  @Test
  void listCostsReturnsOnlyActiveExcludingCancelled() throws Exception {
    // Given: 여러 비용이 등록되고 그중 하나가 취소됨 (AC-006, AC-016)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult first = registerCost(validRequest(subscriptionId));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long costId1 = ((Number) readBody(first).get("id")).longValue();

    Map<String, Object> secondRequest = validRequest(subscriptionId);
    secondRequest.put("costType", "계좌개설비");
    secondRequest.put("amount", 15000);
    secondRequest.put("incurredAt", "2026-09-11T10:00:00");
    MvcResult second = registerCost(secondRequest);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    Long costId2 = ((Number) readBody(second).get("id")).longValue();

    // 첫 번째 비용을 취소(CANCELLED)
    Cost cancelled = costRepository.findById(costId1).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    costRepository.saveAndFlush(cancelled);

    // When: GET /costs 요청을 보냄
    MvcResult result = listCosts(null, null, null);

    // Then: 200 OK와 함께 ACTIVE 비용만 반환되고 CANCELLED 기록은 제외됨 (AC-006, AC-016)
    assertThat(result.getResponse().getStatus())
        .as("목록 조회는 200 OK를 반환해야 합니다 (AC-006)")
        .isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue())
        .as("취소된 기록은 목록에서 제외되어야 합니다 (AC-016)")
        .isEqualTo(costId2);
    assertThat(body.get(0).get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void listCostsFiltersBySubscriptionId() throws Exception {
    // Given: 서로 다른 두 청약이 각각 하나의 비용 기록을 가짐 (AC-007)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    Long subscriptionId1 = createSubscription(investorId, securitiesAccountId, ipoId);

    Long firmId2 = registerFirm();
    Long securitiesAccountId2 = registerSecuritiesAccount(investorId, firmId2);
    Long ipoId2 = createIpo("기업B", "공모주B", "654321");
    Long subscriptionId2 = createSubscription(investorId, securitiesAccountId2, ipoId2);

    MvcResult first = registerCost(validRequest(subscriptionId1));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long costId1 = ((Number) readBody(first).get("id")).longValue();

    Map<String, Object> secondRequest = validRequest(subscriptionId2);
    secondRequest.put("costType", "이체수수료");
    secondRequest.put("amount", 5000);
    MvcResult second = registerCost(secondRequest);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    Long costId2 = ((Number) readBody(second).get("id")).longValue();

    // When: GET /costs?subscriptionId={subscriptionId1} 요청을 보냄
    MvcResult result = listCosts(subscriptionId1, null, null);

    // Then: 200 OK와 함께 해당 청약의 비용만 반환됨 (AC-007)
    assertThat(result.getResponse().getStatus())
        .as("청약별 필터 조회는 200 OK를 반환해야 합니다 (AC-007)")
        .isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue())
        .as("subscriptionId 필터에 해당하는 기록만 반환되어야 합니다 (AC-007)")
        .isEqualTo(costId1);
    assertThat(((Number) body.get(0).get("subscriptionId")).longValue()).isEqualTo(subscriptionId1);
  }

  @Test
  void listCostsFiltersByInvestorId() throws Exception {
    // Given: 서로 다른 두 투자자가 각각 하나의 비용 기록을 가짐 (AC-008)
    signupAndLogin();
    Long investorId1 = registerInvestorAndGetId();
    Long firmId1 = registerFirm();
    Long securitiesAccountId1 = registerSecuritiesAccount(investorId1, firmId1);
    Long ipoId1 = createIpo();
    Long subscriptionId1 = createSubscription(investorId1, securitiesAccountId1, ipoId1);

    MvcResult first = registerCost(validRequest(subscriptionId1));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long costId1 = ((Number) readBody(first).get("id")).longValue();

    // 다른 계정의 투자자로 비용 등록 (다른 계정 세션 필요하지만 여기서는 같은 계정에서 다른 투자자 생성)
    Long investorId2 = registerInvestorAndGetId();
    Long firmId2 = registerFirm();
    Long securitiesAccountId2 = registerSecuritiesAccount(investorId2, firmId2);
    Long ipoId2 = createIpo("기업B", "공모주B", "654321");
    Long subscriptionId2 = createSubscription(investorId2, securitiesAccountId2, ipoId2);

    Map<String, Object> secondRequest = validRequest(subscriptionId2);
    secondRequest.put("costType", "이체수수료");
    secondRequest.put("amount", 5000);
    MvcResult second = registerCost(secondRequest);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    Long costId2 = ((Number) readBody(second).get("id")).longValue();

    // When: GET /costs?investorId={investorId1} 요청을 보냄
    MvcResult result = listCosts(null, investorId1, null);

    // Then: 200 OK와 함께 해당 투자자의 비용만 반환됨 (AC-008)
    assertThat(result.getResponse().getStatus())
        .as("투자자별 필터 조회는 200 OK를 반환해야 합니다 (AC-008)")
        .isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue())
        .as("investorId 필터에 해당하는 기록만 반환되어야 합니다 (AC-008)")
        .isEqualTo(costId1);
  }

  @Test
  void listCostsFiltersByInvestorName() throws Exception {
    // Given: 서로 다른 투자자명으로 비용 기록이 존재함 (AC-008 investorName 필터)
    signupAndLogin();
    Long investorId1 = registerInvestorAndGetId("투자자김철수");
    Long firmId1 = registerFirm();
    Long securitiesAccountId1 = registerSecuritiesAccount(investorId1, firmId1);
    Long ipoId1 = createIpo();
    Long subscriptionId1 = createSubscription(investorId1, securitiesAccountId1, ipoId1);

    MvcResult first = registerCost(validRequest(subscriptionId1));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long costId1 = ((Number) readBody(first).get("id")).longValue();

    Long investorId2 = registerInvestorAndGetId("투자자이영희");
    Long firmId2 = registerFirm();
    Long securitiesAccountId2 = registerSecuritiesAccount(investorId2, firmId2);
    Long ipoId2 = createIpo("기업B", "공모주B", "654321");
    Long subscriptionId2 = createSubscription(investorId2, securitiesAccountId2, ipoId2);

    Map<String, Object> secondRequest = validRequest(subscriptionId2);
    secondRequest.put("costType", "이체수수료");
    secondRequest.put("amount", 5000);
    MvcResult second = registerCost(secondRequest);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    Long costId2 = ((Number) readBody(second).get("id")).longValue();

    // 투자자1의 displayName으로 필터링 (like 검색이므로 앞부분만)
    String prefix = "투자자김"; // "투자자김철수"의 앞 3글자

    // When: GET /costs?investorName={prefix} 요청을 보냄
    MvcResult result = listCosts(null, null, prefix);

    // Then: 200 OK와 함께 해당 투자자명의 비용만 반환됨 (investorName like 검색)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue()).isEqualTo(costId1);
  }

  @Test
  void listCostsFiltersBySubscriptionIdAndInvestorId() throws Exception {
    // Given: subscriptionId와 investorId를 동시에 필터링 (둘 다 주어진 경우)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult first = registerCost(validRequest(subscriptionId));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(first).get("id")).longValue();

    // When: GET /costs?subscriptionId={id}&investorId={id} 요청을 보냄
    MvcResult result = listCosts(subscriptionId, investorId, null);

    // Then: 200 OK와 함께 해당하는 기록만 반환됨
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue()).isEqualTo(costId);
  }

  @Test
  void listCostsReturnsSortedByIncurredAtDescThenCreatedAtDesc() throws Exception {
    // Given: 여러 비용이 다른 incurredAt으로 등록됨 (정렬 확인)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    // 과거 날짜로 먼저 등록
    Map<String, Object> firstRequest = validRequest(subscriptionId);
    firstRequest.put("incurredAt", "2026-09-01T10:00:00");
    firstRequest.put("costType", "첫번째비용");
    MvcResult first = registerCost(firstRequest);
    assertThat(first.getResponse().getStatus()).isEqualTo(201);

    // 이후 날짜로 등록
    Map<String, Object> secondRequest = validRequest(subscriptionId);
    secondRequest.put("incurredAt", "2026-09-10T10:00:00");
    secondRequest.put("costType", "두번째비용");
    MvcResult second = registerCost(secondRequest);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);

    // 더 과거 날짜로 등록
    Map<String, Object> thirdRequest = validRequest(subscriptionId);
    thirdRequest.put("incurredAt", "2026-08-01T10:00:00");
    thirdRequest.put("costType", "세번째비용");
    MvcResult third = registerCost(thirdRequest);
    assertThat(third.getResponse().getStatus()).isEqualTo(201);

    // When: GET /costs 요청을 보냄
    MvcResult result = listCosts(null, null, null);

    // Then: incurredAt 내림차순, 동일 시 createdAt 내림차순으로 정렬됨
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(3);
    // 최신 incurredAt(2026-09-10)이 첫 번째
    assertThat(body.get(0).get("costType")).isEqualTo("두번째비용");
    // 그 다음 2026-09-01
    assertThat(body.get(1).get("costType")).isEqualTo("첫번째비용");
    // 가장 오래된 2026-08-01이 마지막
    assertThat(body.get(2).get("costType")).isEqualTo("세번째비용");
  }

  @Test
  void getCostByIdReturnsDetail() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (AC-009)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: GET /costs/{id} 요청을 보냄
    MvcResult result = getCostById(costId);

    // Then: 200 OK와 함께 해당 비용의 상세 정보가 반환됨 (AC-009)
    assertThat(result.getResponse().getStatus())
        .as("상세 조회는 200 OK를 반환해야 합니다 (AC-009)")
        .isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(((Number) response.get("id")).longValue()).isEqualTo(costId);
    assertThat(((Number) response.get("subscriptionId")).longValue()).isEqualTo(subscriptionId);
    assertThat(response.get("costType")).isEqualTo("청약수수료");
    assertThat(((Number) response.get("amount")).longValue()).isEqualTo(25000L);
    assertThat(response.get("incurredAt")).isEqualTo("2026-09-10T10:00:00");
    assertThat(response.get("memo")).isEqualTo("OO증권 청약수수료");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(response.get("deletedAt")).isNull();
    assertThat(response.get("createdAt")).isNotNull();
    assertThat(response.get("updatedAt")).isNotNull();
  }

  @Test
  void getCostByIdWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 세션이 준비되어 있고 대상 ID가 존재하지 않는 값임 (AC-010)
    signupAndLogin();

    // When: GET /costs/{존재하지 않는 id} 요청을 보냄
    MvcResult result = getCostById(99999999L);

    // Then: 404 Not Found + NOT_FOUND (AC-010)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 ID의 상세 조회는 404를 반환해야 합니다 (AC-010)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void getCostByIdForCancelledCostReturns404() throws Exception {
    // Given: 비용이 등록 후 취소(CANCELLED + deletedAt)됨 (엣지케이스: 취소된 기록 조회 불가)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    Cost cancelled = costRepository.findById(costId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    costRepository.saveAndFlush(cancelled);

    // When: 취소된 비용에 GET /costs/{id} 요청을 보냄
    MvcResult result = getCostById(costId);

    // Then: 404 Not Found + NOT_FOUND — 이미 삭제된(deletedAt 설정) 기록은 조회되지 않음 (AC-016)
    assertThat(result.getResponse().getStatus())
        .as("CANCELLED 비용의 상세 조회는 404를 반환해야 합니다 (AC-016)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void listCostsExcludesCostsOfCancelledSubscription() throws Exception {
    // Given: Subscription이 CANCELLED되었으나 연결된 Cost는 ACTIVE인 경우 (엣지케이스: 청약 취소와 비용의 관계)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // Subscription을 CANCELLED로 변경 (Cost는 여전히 ACTIVE)
    Subscription cancelled = subscriptionRepository.findById(subscriptionId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    subscriptionRepository.saveAndFlush(cancelled);

    // When: GET /costs 요청을 보냄 (subscriptionId 필터 없음)
    MvcResult result = listCosts(null, null, null);

    // Then: 비용은 여전히 조회됨 — 청약이 취소되어도 비용 기록은 보존됨 (spec 엣지케이스)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue()).isEqualTo(costId);
    assertThat(body.get(0).get("status")).isEqualTo("ACTIVE");
  }

  // ==================== SF-4: 비용 수정 테스트 (AC-011, AC-012, AC-013) ====================

  private MvcResult updateCost(Long id, Map<String, Object> request) throws Exception {
    return mockMvc
        .perform(
            patch("/costs/" + id)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andReturn();
  }

  @Test
  void updateCostWithPartialFieldsReturns200AndUpdatedFields() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (AC-011)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: costType과 amount만 담긴 수정 요청을 보냄 (나머지 필드는 미제공)
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("costType", "수정된청약수수료");
    updateRequest.put("amount", 30000);
    MvcResult result = updateCost(costId, updateRequest);

    // Then: 200 OK + 제공된 필드는 갱신, 미제공 필드는 기존값 유지, updatedAt 갱신 (AC-011)
    assertThat(result.getResponse().getStatus())
        .as("유효한 부분 수정은 200 OK를 반환해야 합니다 (AC-011)")
        .isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("costType")).isEqualTo("수정된청약수수료");
    assertThat(((Number) response.get("amount")).longValue()).isEqualTo(30000L);
    assertThat(response.get("incurredAt")).isEqualTo("2026-09-10T10:00:00"); // 기존값 유지
    assertThat(response.get("memo")).isEqualTo("OO증권 청약수수료"); // 기존값 유지
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(response.get("updatedAt")).isNotNull();

    Cost updated = costRepository.findById(costId).orElseThrow();
    assertThat(updated.getCostType()).isEqualTo("수정된청약수수료");
    assertThat(updated.getAmount()).isEqualTo(30000L);
    assertThat(updated.getIncurredAt())
        .isEqualTo(java.time.LocalDateTime.parse("2026-09-10T10:00:00"));
    assertThat(updated.getMemo()).isEqualTo("OO증권 청약수수료");
    assertThat(updated.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(updated.getDeletedAt()).isNull();
  }

  @Test
  void updateCostWithInvalidAmountReturns400() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (AC-012)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When/Then: amount < 0 시 400 VALIDATION_ERROR (AC-012)
    Map<String, Object> negativeAmount = new LinkedHashMap<>();
    negativeAmount.put("amount", -1);
    assertValidationError(updateCost(costId, negativeAmount));

    // And: 기존 값이 변경되지 않음 (AC-012)
    Cost unchanged = costRepository.findById(costId).orElseThrow();
    assertThat(unchanged.getAmount()).isEqualTo(25000L);
  }

  @Test
  void updateCostWithMemoExceeding255CharsReturns400() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (AC-012)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: memo 256자로 수정 시도
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("memo", "a".repeat(256));
    MvcResult result = updateCost(costId, request);

    // Then: 400 VALIDATION_ERROR
    assertThat(result.getResponse().getStatus())
        .as("memo 255자 초과 시 400을 반환해야 합니다 (AC-012)")
        .isEqualTo(400);
    assertError(result, "VALIDATION_ERROR");
  }

  @Test
  void updateCostWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 세션이 준비되어 있고 대상 ID가 존재하지 않는 값임 (AC-013)
    signupAndLogin();

    // When: PATCH /costs/{존재하지 않는 id} 요청을 보냄
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("memo", "수정된 메모");
    MvcResult result = updateCost(99999999L, updateRequest);

    // Then: 404 Not Found + NOT_FOUND (AC-013)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 ID의 수정은 404를 반환해야 합니다 (AC-013)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateCostForCancelledRecordReturns404() throws Exception {
    // Given: 비용이 등록 후 취소(CANCELLED + deletedAt)됨 (엣지케이스: 취소된 기록 재수정 금지)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    Cost cancelled = costRepository.findById(costId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    costRepository.saveAndFlush(cancelled);

    // When: 취소된 기록에 PATCH 수정 요청을 보냄
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("memo", "수정된 메모");
    MvcResult result = updateCost(costId, updateRequest);

    // Then: 404 Not Found + NOT_FOUND (엣지케이스 — 조회 API에서 노출되지 않는 기록이므로)
    assertThat(result.getResponse().getStatus())
        .as("CANCELLED 기록의 수정은 404를 반환해야 합니다 (엣지케이스)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateCostWithAllFieldsReturns200() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (전체 필드 수정 확인)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: 모든 필드(costType/amount/incurredAt/memo)를 수정 요청
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("costType", "이체수수료");
    updateRequest.put("amount", 5000);
    updateRequest.put("incurredAt", "2026-09-15T14:30:00");
    updateRequest.put("memo", "수정된 메모");
    MvcResult result = updateCost(costId, updateRequest);

    // Then: 200 OK + 모든 필드 갱신됨
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("costType")).isEqualTo("이체수수료");
    assertThat(((Number) response.get("amount")).longValue()).isEqualTo(5000L);
    assertThat(response.get("incurredAt")).isEqualTo("2026-09-15T14:30:00");
    assertThat(response.get("memo")).isEqualTo("수정된 메모");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void updateCostWithZeroAmountReturns200() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (amount=0 허용 확인)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: amount=0으로 수정
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("amount", 0);
    MvcResult result = updateCost(costId, updateRequest);

    // Then: 200 OK — amount 0은 허용 (0 미만만 400)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(((Number) response.get("amount")).longValue()).isEqualTo(0L);
  }

  @Test
  void updateCostWithEmptyMemoReturns200() throws Exception {
    // Given: memo가 있는 비용 기록이 존재함 (빈 문자열로 메모 비우기 확인)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: memo를 빈 문자열로 수정
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("memo", "");
    MvcResult result = updateCost(costId, updateRequest);

    // Then: 200 OK — memo는 빈 문자열 허용
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("memo")).isEqualTo("");
  }

  // ==================== SF-5: 비용 취소(논리 삭제) 테스트 (AC-014, AC-015) ====================

  private MvcResult cancelCost(Long id) throws Exception {
    return mockMvc.perform(delete("/costs/" + id).session(session)).andReturn();
  }

  @Test
  void cancelCostWithValidIdReturns204AndCancelsRecord() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (AC-014)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: DELETE /costs/{id} 요청을 보냄
    MvcResult result = cancelCost(costId);

    // Then: 204 No Content가 반환되고, status가 CANCELLED로 변경되며 deletedAt이 설정됨 (물리 삭제하지 않음) (AC-014)
    assertThat(result.getResponse().getStatus())
        .as("유효한 비용 취소 요청 시 204 No Content를 반환해야 합니다 (AC-014)")
        .isEqualTo(204);
    Cost cancelled = costRepository.findById(costId).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(RecordStatus.CANCELLED);
    assertThat(cancelled.getDeletedAt()).isNotNull();
    assertThat(cancelled.getUpdatedAt()).isNotNull();
  }

  @Test
  void cancelCostWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 세션이 준비되어 있고 대상 ID가 존재하지 않는 값임 (AC-015)
    signupAndLogin();

    // When: DELETE /costs/{존재하지 않는 id} 요청을 보냄
    MvcResult result = cancelCost(99999999L);

    // Then: 404 Not Found + NOT_FOUND (AC-015)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 ID의 취소는 404를 반환해야 합니다 (AC-015)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void cancelCostAlreadyCancelledReturns404() throws Exception {
    // Given: 등록된 비용이 이미 한 번 취소됨 (엣지케이스: 재취소는 자연 배제)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    MvcResult firstCancel = cancelCost(costId);
    assertThat(firstCancel.getResponse().getStatus()).isEqualTo(204);

    // When: 이미 취소된 기록을 다시 취소 시도함
    MvcResult result = cancelCost(costId);

    // Then: 404 Not Found + NOT_FOUND — 이미 삭제된(deletedAt 설정) 기록은 조회되지 않음 (엣지케이스)
    assertThat(result.getResponse().getStatus())
        .as("이미 취소된 기록의 재취소는 404를 반환해야 합니다 (엣지케이스)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void cancelCostExcludesFromListAndDetailView() throws Exception {
    // Given: 등록된 ACTIVE 비용 기록이 존재함 (AC-016)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();
    Long subscriptionId = createSubscription(investorId, securitiesAccountId, ipoId);

    MvcResult registerResult = registerCost(validRequest(subscriptionId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long costId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: 비용을 취소한 뒤 목록/상세 조회를 수행함
    MvcResult cancelResult = cancelCost(costId);
    assertThat(cancelResult.getResponse().getStatus()).isEqualTo(204);

    MvcResult listResult = listCosts(null, null, null);
    MvcResult detailResult = getCostById(costId);

    // Then: 목록 조회에서는 제외되고(AC-016), 상세 조회는 404를 반환함
    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = readList(listResult);
    assertThat(body).as("취소된 비용은 목록 조회에서 제외되어야 합니다 (AC-016)").isEmpty();

    assertThat(detailResult.getResponse().getStatus())
        .as("취소된 비용의 상세 조회는 404를 반환해야 합니다")
        .isEqualTo(404);
    assertError(detailResult, "NOT_FOUND");
  }
}
