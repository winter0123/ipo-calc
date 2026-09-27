package com.vibeaiteam.costrecording.controller.cost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC 인용: docs/spec/cost-recording.md
 * - AC-001: 비용 등록 성공 — 존재하는 Subscription과 유효한 비용종류, 금액, 발생일시가 제공되면 201 + ACTIVE 기록 생성
 * - AC-002: 비용 등록 실패 (필수값 누락) — subscriptionId, costType, amount, incurredAt 중 하나 이상 누락 시 400 VALIDATION_ERROR
 * - AC-003: 비용 등록 실패 (금액 값 오류) — amount < 0이면 400 VALIDATION_ERROR
 * - AC-004: 비용 등록 실패 (Subscription 미존재) — 존재하지 않는 subscriptionId → 404 NOT_FOUND
 * - AC-005: 동일 청약 다건 등록 성공 (1:N) — 하나의 Subscription에 여러 비용 등록 가능
 * - 소유권 검증: 다른 계정 소유의 subscriptionId로 등록 시도 → 403 FORBIDDEN
 * - 엣지케이스: 취소된 Subscription(CANCELLED) 참조 시 404
 * - 엣지케이스: memo 255자 초과 시 400 VALIDATION_ERROR
 * - 엣지케이스: memo null 허용
 * - 엣지케이스: incurredAt 미래/과거 무관 허용
 * - 엣지케이스: status 필드 허용값 외 입력 시 400 (Controller에서 검증)
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class CostControllerTest {

    private static final String ACCOUNT_NUMBER = "123-456-789012";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private SecuritiesFirmRepository securitiesFirmRepository;

    @Autowired
    private SecuritiesAccountRepository securitiesAccountRepository;

    @Autowired
    private AccountNumberEncryptor accountNumberEncryptor;

    @Autowired
    private IpoRepository ipoRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private CostRepository costRepository;

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
            List<Investor> investors = investorRepository.findAll().stream()
                    .filter(i -> acctId.equals(i.getAccountId()))
                    .toList();
            for (Investor investor : investors) {
                List<SecuritiesAccount> accounts = securitiesAccountRepository.findByInvestorId(investor.getId());
                List<Subscription> subscriptions = subscriptionRepository.findAll().stream()
                        .filter(s -> investor.getId().equals(s.getInvestorId()))
                        .toList();
                // Cost 먼저 삭제 (FK 제약 때문에)
                for (Subscription sub : subscriptions) {
                    List<Cost> costs = costRepository.findAll().stream()
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
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", testPassword,
                                "name", testName)))
                        .session(session))
                .andReturn();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", testPassword)))
                        .session(session))
                .andReturn();
    }

    private Long registerInvestorAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post("/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "비용투자자",
                                "relationship", "SELF")))
                        .session(session))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("테스트 사전 준비: 투자자 등록은 201이어야 합니다").isEqualTo(201);
        return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
    }

    private Long registerFirm() {
        LocalDateTime now = LocalDateTime.now();
        SecuritiesFirm firm = securitiesFirmRepository.save(SecuritiesFirm.builder()
                .name("한국투자증권" + ThreadLocalRandom.current().nextLong())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
        createdFirms.add(firm);
        return firm.getId();
    }

    private Long registerSecuritiesAccount(Long investorId, Long firmId) throws Exception {
        MvcResult result = mockMvc.perform(post("/securities-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "investorId", investorId,
                                "securitiesFirmId", firmId,
                                "accountNumber", ACCOUNT_NUMBER,
                                "accountName", "개인 MTS 계좌")))
                        .session(session))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("테스트 사전 준비: 증권계좌 등록은 201이어야 합니다").isEqualTo(201);
        return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
    }

    private Long createIpo() {
        return createIpo("기업A", "공모주A", "123456");
    }

    private Long createIpo(String companyName, String ipoName, String stockCode) {
        LocalDateTime now = LocalDateTime.now();
        Ipo ipo = ipoRepository.save(Ipo.builder()
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
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        createdIpos.add(ipo);
        return ipo.getId();
    }

    private Long createSubscription(Long investorId, Long securitiesAccountId, Long ipoId) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .investorId(investorId)
                .securitiesAccountId(securitiesAccountId)
                .ipoId(ipoId)
                .quantity(100L)
                .depositAmount(1000000L)
                .subscribedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .memo("테스트 청약")
                .status("ACTIVE")
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
        Account other = accountRepository.save(Account.builder()
                .email(otherEmail)
                .passwordHash("encodedPasswordHash")
                .name("다른계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
        Investor saved = investorRepository.save(Investor.builder()
                .accountId(other.getId())
                .displayName("다른계정투자자")
                .relationship("OTHER")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        Long firmId = registerFirm();
        SecuritiesAccount account = securitiesAccountRepository.save(SecuritiesAccount.builder()
                .investorId(saved.getId())
                .securitiesFirmId(firmId)
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .isDefault(true)
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return saved.getId();
    }

    private Long createOtherAccountSubscription() throws Exception {
        Long otherInvestorId = createOtherAccountInvestorAndAccount();
        List<SecuritiesAccount> otherAccounts = securitiesAccountRepository.findByInvestorId(otherInvestorId);
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
        return mockMvc.perform(post("/costs")
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
                .as("유효한 비용 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);
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
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
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
                .as("존재하지 않는 subscriptionId는 404를 반환해야 합니다 (AC-004)").isEqualTo(404);
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
        cancelled.setStatus("CANCELLED");
        cancelled.setDeletedAt(now);
        cancelled.setUpdatedAt(now);
        subscriptionRepository.saveAndFlush(cancelled);

        // When: 취소된 Subscription으로 비용 등록 시도
        MvcResult result = registerCost(validRequest(subscriptionId));

        // Then: 404 Not Found (취소된 Subscription 참조 불가)
        assertThat(result.getResponse().getStatus())
                .as("취소된 Subscription 참조 시 404를 반환해야 합니다 (엣지케이스)").isEqualTo(404);
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
                .as("다른 계정 소유 Subscription으로 등록 시 403 Forbidden을 반환해야 합니다 (소유권 검증)").isEqualTo(403);
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
                .as("동일 Subscription에 여러 비용 등록은 201을 반환해야 합니다 (AC-005)").isEqualTo(201);
        Map<String, Object> secondResponse = readBody(second);
        assertThat(secondResponse.get("id")).isNotNull();
        assertThat(secondResponse.get("costType")).isEqualTo("계좌개설비");
        assertThat(((Number) secondResponse.get("amount")).longValue()).isEqualTo(15000L);
        assertThat(secondResponse.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) secondResponse.get("subscriptionId")).longValue()).isEqualTo(subscriptionId);

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
                .as("memo 255자 초과 시 400을 반환해야 합니다 (엣지케이스)").isEqualTo(400);
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
}