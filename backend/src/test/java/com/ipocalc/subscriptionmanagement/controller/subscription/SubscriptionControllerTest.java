package com.ipocalc.subscriptionmanagement.controller.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.testsupport.AuthTestSupport;
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
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * AC 인용: docs/spec/subscription-management.md - AC-001: 청약 등록 성공 — 유효한 투자자/계좌/IPO 참조와 수량·증거금·청약일시가
 * 제공되면 201 + ACTIVE 기록 생성 - AC-002: 청약 등록 실패 (필수값 누락) —
 * investorId/securitiesAccountId/ipoId/quantity/depositAmount/subscribedAt 중 하나 이상 누락 시 400
 * VALIDATION_ERROR, 레코드 미생성 - AC-003: 청약 등록 실패 (수량/증거금 값 오류) — quantity <= 0 또는 depositAmount < 0이면
 * 400 VALIDATION_ERROR - AC-004: 청약 등록 실패 (기준정보 존재하지 않음) — 존재하지 않는
 * investorId/securitiesAccountId/ipoId → 404 NOT_FOUND - AC-005: 청약 등록 실패 (중복) — 동일 (investorId,
 * securitiesAccountId, ipoId) 조합의 ACTIVE 기록 존재 시 409 - AC-006: 동일 조합의 CANCELLED 기록이 있어도 재등록 성공 —
 * 201 + 새 ACTIVE 기록 - AC-007: 청약 목록 조회 성공 — 200 OK + ACTIVE 청약 목록 - AC-008: 투자자별 목록 조회 성공 —
 * investorId 필터로 해당 투자자 기록만 - AC-009: 계좌별 목록 조회 성공 — securitiesAccountId 필터로 해당 계좌 기록만 - AC-010: 청약
 * 상세 조회 성공 — 200 OK + 상세 정보 - AC-011: 청약 상세 조회 실패 (존재하지 않는 ID) — 404 NOT_FOUND - AC-012: 청약 수정 성공 —
 * 제공된 필드만 부분 수정, 미제공 필드는 기존값 유지, updatedAt 갱신 - AC-013: 청약 수정 실패 (유효성 오류) — quantity <= 0 또는
 * depositAmount < 0 제공 시 400 VALIDATION_ERROR - AC-014: 청약 수정 실패 (중복) — 다른 ACTIVE 기록과 (investorId,
 * securitiesAccountId, ipoId) 조합 중복 시 409, 자기 자신과 동일 조합으로는 충돌하지 않음 - AC-015: 청약 수정 실패 (존재하지 않는 ID)
 * — 404 NOT_FOUND - AC-016: 청약 취소 성공 — DELETE /subscriptions/{id} 시 204 No Content, status가
 * CANCELLED로 변경되고 deletedAt이 설정됨 (물리 삭제하지 않음) - AC-017: 청약 취소 실패 (존재하지 않는 ID) — 404 NOT_FOUND -
 * AC-018: 취소된 청약은 목록 조회에서 제외 - 소유권 검증: 다른 계정 소유의 investorId/securitiesAccountId로 등록 시도 → 403
 * FORBIDDEN - 수정 시 새로 제공된 investorId/securitiesAccountId는 소유권 재검증(403), ipoId는 존재 재검증(404) - 엣지케이스:
 * 이미 CANCELLED된 기록 수정 시도 → 404 - 조회(목록/상세)는 소유권 필터를 적용하지 않는다 — spec/architecture에 조회 시 소유권 검증이 명시되어
 * 있지 않음.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SubscriptionControllerTest {

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
    testEmail = "subscription-owner-" + nonce + "@example.com";
    testPassword = "securePass123!";
    testName = "청약테스터";
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
        subscriptionRepository.deleteAll(subscriptions);
        securitiesAccountRepository.deleteAll(accounts);
      }
      investorRepository.deleteAll(investors);
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  private void signupAndLogin() throws Exception {
    AuthTestSupport.signupAndLogin(
        mockMvc, objectMapper, session, testEmail, testPassword, testName);
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
                                "displayName", "청약투자자",
                                "relationship", "SELF")))
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

  private Long createOtherAccountInvestorAndAccount() {
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

  private Map<String, Object> validRequest(Long investorId, Long securitiesAccountId, Long ipoId) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("investorId", investorId);
    request.put("securitiesAccountId", securitiesAccountId);
    request.put("ipoId", ipoId);
    request.put("quantity", 100);
    request.put("depositAmount", 1000000);
    request.put("subscribedAt", "2026-10-01T09:00:00");
    request.put("memo", "비대면 청약");
    return request;
  }

  private MvcResult registerSubscription(Map<String, Object> request) throws Exception {
    return mockMvc
        .perform(
            post("/subscriptions")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andReturn();
  }

  private MvcResult updateSubscription(Long id, Map<String, Object> request) throws Exception {
    return mockMvc
        .perform(
            patch("/subscriptions/" + id)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andReturn();
  }

  private MvcResult cancelSubscription(Long id) throws Exception {
    return mockMvc.perform(delete("/subscriptions/" + id).session(session)).andReturn();
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
  void registerSubscriptionWithValidRequestReturns201AndSavedSubscription() throws Exception {
    // Given: 유효한 투자자/계좌/IPO 참조와 수량·증거금·청약일시가 제공됨 (AC-001)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    // When: POST /subscriptions 요청을 보냄
    MvcResult result = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));

    // Then: 201 Created와 함께 신규 ACTIVE 기록이 생성됨 (AC-001)
    assertThat(result.getResponse().getStatus())
        .as("유효한 청약 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)")
        .isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("id")).isNotNull();
    assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
    assertThat(((Number) response.get("securitiesAccountId")).longValue())
        .isEqualTo(securitiesAccountId);
    assertThat(((Number) response.get("ipoId")).longValue()).isEqualTo(ipoId);
    assertThat(((Number) response.get("quantity")).longValue()).isEqualTo(100L);
    assertThat(((Number) response.get("depositAmount")).longValue()).isEqualTo(1000000L);
    assertThat(response.get("subscribedAt")).isEqualTo("2026-10-01T09:00:00");
    assertThat(response.get("memo")).isEqualTo("비대면 청약");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(response.get("createdAt")).isNotNull();
    assertThat(response.get("updatedAt")).isNotNull();

    Subscription saved =
        subscriptionRepository.findById(((Number) response.get("id")).longValue()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(saved.getDeletedAt()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void registerSubscriptionWithMissingRequiredFieldsReturns400() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고, 필수 필드 중 하나씩을 제거해 요청함 (AC-002)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    // When/Then: investorId/securitiesAccountId/ipoId/quantity/depositAmount/subscribedAt 각각 누락 시
    // 400 (AC-002)
    for (String field :
        List.of(
            "investorId",
            "securitiesAccountId",
            "ipoId",
            "quantity",
            "depositAmount",
            "subscribedAt")) {
      Map<String, Object> request = validRequest(investorId, securitiesAccountId, ipoId);
      request.remove(field);
      assertValidationError(registerSubscription(request));
    }

    // And: 레코드가 생성되지 않음 (AC-002)
    assertThat(subscriptionRepository.findAll()).isEmpty();
  }

  @Test
  void registerSubscriptionWithQuantityZeroOrNegativeOrNegativeDepositReturns400()
      throws Exception {
    // Given: 유효한 등록 준비가 끝나 있음 (AC-003)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    // When/Then: quantity=0, quantity<0, depositAmount<0 각각 400 (AC-003)
    Map<String, Object> zeroQuantity = validRequest(investorId, securitiesAccountId, ipoId);
    zeroQuantity.put("quantity", 0);
    assertValidationError(registerSubscription(zeroQuantity));

    Map<String, Object> negativeQuantity = validRequest(investorId, securitiesAccountId, ipoId);
    negativeQuantity.put("quantity", -1);
    assertValidationError(registerSubscription(negativeQuantity));

    Map<String, Object> negativeDeposit = validRequest(investorId, securitiesAccountId, ipoId);
    negativeDeposit.put("depositAmount", -1);
    assertValidationError(registerSubscription(negativeDeposit));

    // And: 레코드가 생성되지 않음 (AC-003)
    assertThat(subscriptionRepository.findAll()).isEmpty();
  }

  @Test
  void registerSubscriptionWithUnknownReferenceReturns404() throws Exception {
    // Given: 유효한 등록 준비가 끝나 있고, 참조 ID 하나씩을 존재하지 않는 값으로 바꿔 요청함 (AC-004)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    // When/Then: 존재하지 않는 investorId/securitiesAccountId/ipoId 각각 404 (AC-004)
    MvcResult unknownInvestor =
        registerSubscription(validRequest(99999999L, securitiesAccountId, ipoId));
    assertThat(unknownInvestor.getResponse().getStatus())
        .as("존재하지 않는 investorId는 404를 반환해야 합니다 (AC-004)")
        .isEqualTo(404);
    assertError(unknownInvestor, "NOT_FOUND");

    MvcResult unknownAccount = registerSubscription(validRequest(investorId, 99999999L, ipoId));
    assertThat(unknownAccount.getResponse().getStatus())
        .as("존재하지 않는 securitiesAccountId는 404를 반환해야 합니다 (AC-004)")
        .isEqualTo(404);
    assertError(unknownAccount, "NOT_FOUND");

    MvcResult unknownIpo =
        registerSubscription(validRequest(investorId, securitiesAccountId, 99999999L));
    assertThat(unknownIpo.getResponse().getStatus())
        .as("존재하지 않는 ipoId는 404를 반환해야 합니다 (AC-004)")
        .isEqualTo(404);
    assertError(unknownIpo, "NOT_FOUND");

    // And: 레코드가 생성되지 않음 (AC-004)
    assertThat(subscriptionRepository.findAll()).isEmpty();
  }

  @Test
  void registerSubscriptionWithInvestorOrAccountOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A와 다른 계정 B가 있고, B 소유의 투자자/계좌 ID가 알려져 있음 (소유권 검증)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestorAndAccount();
    java.util.List<SecuritiesAccount> otherAccounts =
        securitiesAccountRepository.findByInvestorId(otherInvestorId);
    assertThat(otherAccounts).hasSize(1);
    Long otherAccountId = otherAccounts.get(0).getId();
    Long ipoId = createIpo();

    // When/Then: 계정 A가 계정 B의 투자자 ID로 등록 시 403 FORBIDDEN
    MvcResult otherInvestorResult =
        registerSubscription(validRequest(otherInvestorId, otherAccountId, ipoId));
    assertThat(otherInvestorResult.getResponse().getStatus())
        .as("다른 계정 소유 투자자로 등록 시 403 Forbidden을 반환해야 합니다 (소유권 검증)")
        .isEqualTo(403);
    assertError(otherInvestorResult, "FORBIDDEN");

    // When/Then: 계정 A가 계정 B의 증권계좌 ID로 등록 시 403 FORBIDDEN
    Long ownInvestorId = registerInvestorAndGetId();
    MvcResult otherAccountResult =
        registerSubscription(validRequest(ownInvestorId, otherAccountId, ipoId));
    assertThat(otherAccountResult.getResponse().getStatus())
        .as("다른 계정 소유 증권계좌로 등록 시 403 Forbidden을 반환해야 합니다 (소유권 검증)")
        .isEqualTo(403);
    assertError(otherAccountResult, "FORBIDDEN");

    // And: 레코드가 생성되지 않음 (소유권 검증)
    assertThat(subscriptionRepository.findAll()).isEmpty();
  }

  @Test
  void registerSubscriptionWithDuplicateActiveComboReturns409() throws Exception {
    // Given: 유효한 투자자/계좌/IPO와 동일 조합의 ACTIVE 청약이 이미 존재함 (AC-005)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult first = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);

    // When: 동일 (investorId, securitiesAccountId, ipoId) 조합으로 다시 등록 요청
    MvcResult result = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));

    // Then: 409 Conflict + DUPLICATE_SUBSCRIPTION, 레코드는 1건만 유지됨 (AC-005)
    assertThat(result.getResponse().getStatus())
        .as("동일 조합 ACTIVE 중복 등록 시 409 Conflict를 반환해야 합니다 (AC-005)")
        .isEqualTo(409);
    assertError(result, "DUPLICATE_SUBSCRIPTION");
    assertThat(subscriptionRepository.findAll()).hasSize(1);
  }

  @Test
  void registerSubscriptionAfterCancelAllowsReregisterReturns201() throws Exception {
    // Given: 동일 조합의 청약이 등록 후 취소(CANCELLED + deletedAt)됨 (AC-006)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult first = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Subscription existing =
        subscriptionRepository
            .findById(((Number) readBody(first).get("id")).longValue())
            .orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    existing.cancel(now);
    subscriptionRepository.saveAndFlush(existing);

    // When: 동일 조합으로 새 청약을 등록함
    MvcResult result = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));

    // Then: 201 Created + 새 ACTIVE 기록이 생성됨 (AC-006)
    assertThat(result.getResponse().getStatus())
        .as("CANCELLED 기록이 있어도 동일 조합 재등록은 201을 반환해야 합니다 (AC-006)")
        .isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(subscriptionRepository.findAll()).hasSize(2);
  }

  private List<Map<String, Object>> readList(MvcResult result) throws Exception {
    return objectMapper.readValue(
        utf8Body(result),
        new tools.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
  }

  @Test
  void listSubscriptionsReturnsOnlyActiveExcludingCancelled() throws Exception {
    // Given: 동일 조합의 청약이 등록 후 취소되고, 같은 조합으로 새 ACTIVE 청약이 다시 등록됨 (AC-007, AC-018)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult first = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long cancelledId = ((Number) readBody(first).get("id")).longValue();

    Subscription cancelled = subscriptionRepository.findById(cancelledId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.cancel(now);
    subscriptionRepository.saveAndFlush(cancelled);

    MvcResult reregister =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(reregister.getResponse().getStatus()).isEqualTo(201);
    Long activeId = ((Number) readBody(reregister).get("id")).longValue();
    assertThat(subscriptionRepository.findAll()).hasSize(2);

    // When: GET /subscriptions 요청을 보냄
    MvcResult result = mockMvc.perform(get("/subscriptions").session(session)).andReturn();

    // Then: 200 OK와 함께 ACTIVE 청약만 반환되고 CANCELLED 기록은 제외됨 (AC-007, AC-018)
    assertThat(result.getResponse().getStatus())
        .as("목록 조회는 200 OK를 반환해야 합니다 (AC-007)")
        .isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("id")).longValue())
        .as("취소된 기록은 목록에서 제외되어야 합니다 (AC-018)")
        .isEqualTo(activeId);
    assertThat(((Number) body.get(0).get("investorId")).longValue()).isEqualTo(investorId);
    assertThat(body.get(0).get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void listSubscriptionsFiltersByInvestorId() throws Exception {
    // Given: 서로 다른 두 투자자가 각각 하나의 청약 기록을 가짐 (AC-008)
    signupAndLogin();
    Long investor1 = registerInvestorAndGetId();
    Long firm1 = registerFirm();
    Long account1 = registerSecuritiesAccount(investor1, firm1);
    Long ipo1 = createIpo();

    Long investor2 = registerInvestorAndGetId();
    Long firm2 = registerFirm();
    Long account2 = registerSecuritiesAccount(investor2, firm2);
    Long ipo2 = createIpo("기업B", "공모주B", "654321");

    assertThat(
            registerSubscription(validRequest(investor1, account1, ipo1)).getResponse().getStatus())
        .isEqualTo(201);
    assertThat(
            registerSubscription(validRequest(investor2, account2, ipo2)).getResponse().getStatus())
        .isEqualTo(201);

    // When: GET /subscriptions?investorId={investor1} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                get("/subscriptions")
                    .param("investorId", String.valueOf(investor1))
                    .session(session))
            .andReturn();

    // Then: 200 OK와 함께 해당 투자자의 청약만 반환됨 (AC-008)
    assertThat(result.getResponse().getStatus())
        .as("투자자 필터 조회는 200 OK를 반환해야 합니다 (AC-008)")
        .isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("investorId")).longValue())
        .as("investorId 필터에 해당하는 기록만 반환되어야 합니다 (AC-008)")
        .isEqualTo(investor1);
  }

  @Test
  void listSubscriptionsFiltersBySecuritiesAccountId() throws Exception {
    // Given: 같은 투자자에게 두 증권계좌가 있고 각 계좌마다 청약 기록이 존재함 (AC-009)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firm1 = registerFirm();
    Long account1 = registerSecuritiesAccount(investorId, firm1);
    Long ipo1 = createIpo();

    Long firm2 = registerFirm();
    Long account2 = registerSecuritiesAccount(investorId, firm2);
    Long ipo2 = createIpo("기업B", "공모주B", "654321");

    assertThat(
            registerSubscription(validRequest(investorId, account1, ipo1))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    assertThat(
            registerSubscription(validRequest(investorId, account2, ipo2))
                .getResponse()
                .getStatus())
        .isEqualTo(201);

    // When: GET /subscriptions?securitiesAccountId={account2} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                get("/subscriptions")
                    .param("securitiesAccountId", String.valueOf(account2))
                    .session(session))
            .andReturn();

    // Then: 200 OK와 함께 해당 계좌의 청약만 반환됨 (AC-009)
    assertThat(result.getResponse().getStatus())
        .as("계좌 필터 조회는 200 OK를 반환해야 합니다 (AC-009)")
        .isEqualTo(200);
    List<Map<String, Object>> body = readList(result);
    assertThat(body).hasSize(1);
    assertThat(((Number) body.get(0).get("securitiesAccountId")).longValue())
        .as("securitiesAccountId 필터에 해당하는 기록만 반환되어야 합니다 (AC-009)")
        .isEqualTo(account2);
  }

  @Test
  void getSubscriptionByIdReturnsDetail() throws Exception {
    // Given: 등록된 ACTIVE 청약 기록이 존재함 (AC-010)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: GET /subscriptions/{id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(get("/subscriptions/" + subscriptionId).session(session)).andReturn();

    // Then: 200 OK와 함께 해당 청약의 상세 정보가 반환됨 (AC-010)
    assertThat(result.getResponse().getStatus())
        .as("상세 조회는 200 OK를 반환해야 합니다 (AC-010)")
        .isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(((Number) response.get("id")).longValue()).isEqualTo(subscriptionId);
    assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
    assertThat(((Number) response.get("securitiesAccountId")).longValue())
        .isEqualTo(securitiesAccountId);
    assertThat(((Number) response.get("ipoId")).longValue()).isEqualTo(ipoId);
    assertThat(((Number) response.get("quantity")).longValue()).isEqualTo(100L);
    assertThat(((Number) response.get("depositAmount")).longValue()).isEqualTo(1000000L);
    assertThat(response.get("subscribedAt")).isEqualTo("2026-10-01T09:00:00");
    assertThat(response.get("memo")).isEqualTo("비대면 청약");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void getSubscriptionByIdWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 세션이 준비되어 있고 대상 ID가 존재하지 않는 값임 (AC-011)
    signupAndLogin();

    // When: GET /subscriptions/{존재하지 않는 id} 요청을 보냄
    MvcResult result = mockMvc.perform(get("/subscriptions/99999999").session(session)).andReturn();

    // Then: 404 Not Found + NOT_FOUND (AC-011)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 ID의 상세 조회는 404를 반환해야 합니다 (AC-011)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateSubscriptionWithPartialFieldsReturns200AndUpdatedFields() throws Exception {
    // Given: 등록된 ACTIVE 청약 기록이 존재함 (AC-012)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: memo와 quantity만 담긴 수정 요청을 보냄 (나머지 필드는 미제공)
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("memo", "수정된 메모");
    updateRequest.put("quantity", 50);
    MvcResult result = updateSubscription(subscriptionId, updateRequest);

    // Then: 200 OK + 제공된 필드는 갱신, 미제공 필드는 기존값 유지, updatedAt 갱신 (AC-012)
    assertThat(result.getResponse().getStatus())
        .as("유효한 부분 수정은 200 OK를 반환해야 합니다 (AC-012)")
        .isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("memo")).isEqualTo("수정된 메모");
    assertThat(((Number) response.get("quantity")).longValue()).isEqualTo(50L);
    assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
    assertThat(((Number) response.get("securitiesAccountId")).longValue())
        .isEqualTo(securitiesAccountId);
    assertThat(((Number) response.get("ipoId")).longValue()).isEqualTo(ipoId);
    assertThat(((Number) response.get("depositAmount")).longValue()).isEqualTo(1000000L);
    assertThat(response.get("subscribedAt")).isEqualTo("2026-10-01T09:00:00");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(response.get("updatedAt")).isNotNull();

    Subscription updated = subscriptionRepository.findById(subscriptionId).orElseThrow();
    assertThat(updated.getMemo()).isEqualTo("수정된 메모");
    assertThat(updated.getQuantity()).isEqualTo(50L);
    assertThat(updated.getDepositAmount()).isEqualTo(1000000L);
    assertThat(updated.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(updated.getDeletedAt()).isNull();
  }

  @Test
  void updateSubscriptionWithInvalidQuantityOrDepositReturns400() throws Exception {
    // Given: 등록된 ACTIVE 청약 기록이 존재함 (AC-013)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    // When/Then: quantity=0, quantity<0, depositAmount<0 각각 400 VALIDATION_ERROR (AC-013)
    Map<String, Object> zeroQuantity = new LinkedHashMap<>();
    zeroQuantity.put("quantity", 0);
    assertValidationError(updateSubscription(subscriptionId, zeroQuantity));

    Map<String, Object> negativeQuantity = new LinkedHashMap<>();
    negativeQuantity.put("quantity", -1);
    assertValidationError(updateSubscription(subscriptionId, negativeQuantity));

    Map<String, Object> negativeDeposit = new LinkedHashMap<>();
    negativeDeposit.put("depositAmount", -1);
    assertValidationError(updateSubscription(subscriptionId, negativeDeposit));

    // And: 기존 값이 변경되지 않음 (AC-013)
    Subscription unchanged = subscriptionRepository.findById(subscriptionId).orElseThrow();
    assertThat(unchanged.getQuantity()).isEqualTo(100L);
    assertThat(unchanged.getDepositAmount()).isEqualTo(1000000L);
  }

  @Test
  void updateSubscriptionWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 세션이 준비되어 있고 대상 ID가 존재하지 않는 값임 (AC-015)
    signupAndLogin();

    // When: PATCH /subscriptions/{존재하지 않는 id} 요청을 보냄
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("memo", "수정된 메모");
    MvcResult result = updateSubscription(99999999L, updateRequest);

    // Then: 404 Not Found + NOT_FOUND (AC-015)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 ID의 수정은 404를 반환해야 합니다 (AC-015)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateSubscriptionForCancelledRecordReturns404() throws Exception {
    // Given: 청약이 등록 후 취소(CANCELLED + deletedAt)됨 (엣지케이스: 취소된 기록 재수정 금지)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    Subscription cancelled = subscriptionRepository.findById(subscriptionId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.cancel(now);
    subscriptionRepository.saveAndFlush(cancelled);

    // When: 취소된 기록에 PATCH 수정 요청을 보냄
    Map<String, Object> updateRequest = new LinkedHashMap<>();
    updateRequest.put("memo", "수정된 메모");
    MvcResult result = updateSubscription(subscriptionId, updateRequest);

    // Then: 404 Not Found + NOT_FOUND (엣지케이스 — 조회 API에서 노출되지 않는 기록이므로)
    assertThat(result.getResponse().getStatus())
        .as("CANCELLED 기록의 수정은 404를 반환해야 합니다 (엣지케이스)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateSubscriptionWithInvestorOrAccountOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A와 다른 계정 B가 있고, B 소유의 투자자/계좌 ID가 알려져 있음
    signupAndLogin();
    Long ownInvestorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long ownAccountId = registerSecuritiesAccount(ownInvestorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(ownInvestorId, ownAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    Long otherInvestorId = createOtherAccountInvestorAndAccount();
    java.util.List<SecuritiesAccount> otherAccounts =
        securitiesAccountRepository.findByInvestorId(otherInvestorId);
    assertThat(otherAccounts).hasSize(1);
    Long otherAccountId = otherAccounts.get(0).getId();

    // When/Then: investorId를 다른 계정 소유 투자자로 변경 시도 시 403 FORBIDDEN (신규 소유권 재검증)
    Map<String, Object> otherInvestorRequest = new LinkedHashMap<>();
    otherInvestorRequest.put("investorId", otherInvestorId);
    MvcResult otherInvestorResult = updateSubscription(subscriptionId, otherInvestorRequest);
    assertThat(otherInvestorResult.getResponse().getStatus())
        .as("다른 계정 소유 투자자로 investorId 변경 시 403을 반환해야 합니다 (소유권 재검증)")
        .isEqualTo(403);
    assertError(otherInvestorResult, "FORBIDDEN");

    // When/Then: securitiesAccountId를 다른 계정 소유 계좌로 변경 시도 시 403 FORBIDDEN (신규 소유권 재검증)
    Map<String, Object> otherAccountRequest = new LinkedHashMap<>();
    otherAccountRequest.put("securitiesAccountId", otherAccountId);
    MvcResult otherAccountResult = updateSubscription(subscriptionId, otherAccountRequest);
    assertThat(otherAccountResult.getResponse().getStatus())
        .as("다른 계정 소유 증권계좌로 securitiesAccountId 변경 시 403을 반환해야 합니다 (소유권 재검증)")
        .isEqualTo(403);
    assertError(otherAccountResult, "FORBIDDEN");
  }

  @Test
  void updateSubscriptionWithDuplicateComboReturns409ButSelfReupdateAllowed() throws Exception {
    // Given: 서로 다른 조합의 ACTIVE 청약 기록 2건이 존재함 (AC-014)
    signupAndLogin();
    Long investor1 = registerInvestorAndGetId();
    Long firm1 = registerFirm();
    Long account1 = registerSecuritiesAccount(investor1, firm1);
    Long ipo1 = createIpo();

    Long investor2 = registerInvestorAndGetId();
    Long firm2 = registerFirm();
    Long account2 = registerSecuritiesAccount(investor2, firm2);
    Long ipo2 = createIpo("기업B", "공모주B", "654321");

    MvcResult first = registerSubscription(validRequest(investor1, account1, ipo1));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionAId = ((Number) readBody(first).get("id")).longValue();

    MvcResult second = registerSubscription(validRequest(investor2, account2, ipo2));
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionBId = ((Number) readBody(second).get("id")).longValue();

    // When: 기록 B를 기록 A와 동일한 (investorId, securitiesAccountId, ipoId) 조합으로 변경 시도
    Map<String, Object> duplicateRequest = new LinkedHashMap<>();
    duplicateRequest.put("investorId", investor1);
    duplicateRequest.put("securitiesAccountId", account1);
    duplicateRequest.put("ipoId", ipo1);
    duplicateRequest.put("memo", "중복 시도");
    MvcResult conflict = updateSubscription(subscriptionBId, duplicateRequest);

    // Then: 409 Conflict + DUPLICATE_SUBSCRIPTION, 기존 값은 그대로 유지됨 (AC-014)
    assertThat(conflict.getResponse().getStatus())
        .as("다른 ACTIVE 기록과 조합이 중복되는 수정은 409를 반환해야 합니다 (AC-014)")
        .isEqualTo(409);
    assertError(conflict, "DUPLICATE_SUBSCRIPTION");
    Subscription unchanged = subscriptionRepository.findById(subscriptionBId).orElseThrow();
    assertThat(unchanged.getInvestorId()).isEqualTo(investor2);
    assertThat(unchanged.getSecuritiesAccountId()).isEqualTo(account2);
    assertThat(unchanged.getIpoId()).isEqualTo(ipo2);
    assertThat(unchanged.getMemo()).isEqualTo("비대면 청약");

    // When: 기록 A를 자기 자신과 동일한 조합으로 재수정(메모만 변경, 조합은 그대로)
    Map<String, Object> selfRequest = new LinkedHashMap<>();
    selfRequest.put("memo", "자기 자신 재수정");
    MvcResult selfUpdate = updateSubscription(subscriptionAId, selfRequest);

    // Then: 200 OK — 자기 자신과는 중복으로 간주하지 않음 (AC-014)
    assertThat(selfUpdate.getResponse().getStatus())
        .as("자기 자신과 동일 조합 재수정은 409가 아니라 200을 반환해야 합니다 (AC-014)")
        .isEqualTo(200);
    Map<String, Object> selfResponse = readBody(selfUpdate);
    assertThat(selfResponse.get("memo")).isEqualTo("자기 자신 재수정");
    assertThat(((Number) selfResponse.get("investorId")).longValue()).isEqualTo(investor1);
  }

  @Test
  void cancelSubscriptionWithValidIdReturns204AndCancelsRecord() throws Exception {
    // Given: 등록된 ACTIVE 청약 기록이 존재함 (AC-016)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: DELETE /subscriptions/{id} 요청을 보냄
    MvcResult result = cancelSubscription(subscriptionId);

    // Then: 204 No Content가 반환되고, status가 CANCELLED로 변경되며 deletedAt이 설정됨 (물리 삭제하지 않음) (AC-016)
    assertThat(result.getResponse().getStatus())
        .as("유효한 청약 취소 요청 시 204 No Content를 반환해야 합니다 (AC-016)")
        .isEqualTo(204);
    Subscription cancelled = subscriptionRepository.findById(subscriptionId).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(RecordStatus.CANCELLED);
    assertThat(cancelled.getDeletedAt()).isNotNull();
    assertThat(cancelled.getUpdatedAt()).isNotNull();
  }

  @Test
  void cancelSubscriptionWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 세션이 준비되어 있고 대상 ID가 존재하지 않는 값임 (AC-017)
    signupAndLogin();

    // When: DELETE /subscriptions/{존재하지 않는 id} 요청을 보냄
    MvcResult result = cancelSubscription(99999999L);

    // Then: 404 Not Found + NOT_FOUND (AC-017)
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 ID의 취소는 404를 반환해야 합니다 (AC-017)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void cancelSubscriptionAlreadyCancelledReturns404() throws Exception {
    // Given: 등록된 청약이 이미 한 번 취소됨 (엣지케이스: 재취소는 자연 배제)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    MvcResult firstCancel = cancelSubscription(subscriptionId);
    assertThat(firstCancel.getResponse().getStatus()).isEqualTo(204);

    // When: 이미 취소된 기록을 다시 취소 시도함
    MvcResult result = cancelSubscription(subscriptionId);

    // Then: 404 Not Found + NOT_FOUND — 이미 삭제된(deletedAt 설정) 기록은 조회되지 않음 (엣지케이스)
    assertThat(result.getResponse().getStatus())
        .as("이미 취소된 기록의 재취소는 404를 반환해야 합니다 (엣지케이스)")
        .isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void cancelSubscriptionExcludesFromListAndDetailView() throws Exception {
    // Given: 등록된 ACTIVE 청약 기록이 존재함 (AC-018)
    signupAndLogin();
    Long investorId = registerInvestorAndGetId();
    Long firmId = registerFirm();
    Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
    Long ipoId = createIpo();

    MvcResult registerResult =
        registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));
    assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
    Long subscriptionId = ((Number) readBody(registerResult).get("id")).longValue();

    // When: 청약을 취소한 뒤 목록/상세 조회를 수행함
    MvcResult cancelResult = cancelSubscription(subscriptionId);
    assertThat(cancelResult.getResponse().getStatus()).isEqualTo(204);

    MvcResult listResult = mockMvc.perform(get("/subscriptions").session(session)).andReturn();
    MvcResult detailResult =
        mockMvc.perform(get("/subscriptions/" + subscriptionId).session(session)).andReturn();

    // Then: 목록 조회에서는 제외되고(AC-018), 상세 조회는 404를 반환함
    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = readList(listResult);
    assertThat(body).as("취소된 청약은 목록 조회에서 제외되어야 합니다 (AC-018)").isEmpty();

    assertThat(detailResult.getResponse().getStatus())
        .as("취소된 청약의 상세 조회는 404를 반환해야 합니다")
        .isEqualTo(404);
    assertError(detailResult, "NOT_FOUND");
  }
}
