package com.vibeaiteam.subscriptionmanagement.controller.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
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
 * AC 인용: docs/spec/subscription-management.md
 * - AC-001: 청약 등록 성공 — 유효한 투자자/계좌/IPO 참조와 수량·증거금·청약일시가 제공되면 201 + ACTIVE 기록 생성
 * - AC-002: 청약 등록 실패 (필수값 누락) — investorId/securitiesAccountId/ipoId/quantity/depositAmount/subscribedAt
 *           중 하나 이상 누락 시 400 VALIDATION_ERROR, 레코드 미생성
 * - AC-003: 청약 등록 실패 (수량/증거금 값 오류) — quantity <= 0 또는 depositAmount < 0이면 400 VALIDATION_ERROR
 * - AC-004: 청약 등록 실패 (기준정보 존재하지 않음) — 존재하지 않는 investorId/securitiesAccountId/ipoId → 404 NOT_FOUND
 * - AC-005: 청약 등록 실패 (중복) — 동일 (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE 기록 존재 시 409
 * - AC-006: 동일 조합의 CANCELLED 기록이 있어도 재등록 성공 — 201 + 새 ACTIVE 기록
 * - 소유권 검증: 다른 계정 소유의 investorId/securitiesAccountId로 등록 시도 → 403 FORBIDDEN
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SubscriptionControllerTest {

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
            List<Investor> investors = investorRepository.findAll().stream()
                    .filter(i -> acctId.equals(i.getAccountId()))
                    .toList();
            for (Investor investor : investors) {
                List<SecuritiesAccount> accounts = securitiesAccountRepository.findByInvestorId(investor.getId());
                List<Subscription> subscriptions = subscriptionRepository.findAll().stream()
                        .filter(s -> investor.getId().equals(s.getInvestorId()))
                        .toList();
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
                                "displayName", "청약투자자",
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
        LocalDateTime now = LocalDateTime.now();
        Ipo ipo = ipoRepository.save(Ipo.builder()
                .companyName("기업A")
                .ipoName("공모주A")
                .stockCode("123456")
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

    private Long createOtherAccountInvestorAndAccount() {
        long nonce = ThreadLocalRandom.current().nextLong();
        otherEmail = "other-account-owner-" + nonce + "@example.com";
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
        return mockMvc.perform(post("/subscriptions")
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
                .as("유효한 청약 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);
        Map<String, Object> response = readBody(result);
        assertThat(response.get("id")).isNotNull();
        assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
        assertThat(((Number) response.get("securitiesAccountId")).longValue()).isEqualTo(securitiesAccountId);
        assertThat(((Number) response.get("ipoId")).longValue()).isEqualTo(ipoId);
        assertThat(((Number) response.get("quantity")).longValue()).isEqualTo(100L);
        assertThat(((Number) response.get("depositAmount")).longValue()).isEqualTo(1000000L);
        assertThat(response.get("subscribedAt")).isEqualTo("2026-10-01T09:00:00");
        assertThat(response.get("memo")).isEqualTo("비대면 청약");
        assertThat(response.get("status")).isEqualTo("ACTIVE");
        assertThat(response.get("createdAt")).isNotNull();
        assertThat(response.get("updatedAt")).isNotNull();

        Subscription saved = subscriptionRepository.findById(
                ((Number) response.get("id")).longValue()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
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

        // When/Then: investorId/securitiesAccountId/ipoId/quantity/depositAmount/subscribedAt 각각 누락 시 400 (AC-002)
        for (String field : List.of("investorId", "securitiesAccountId", "ipoId",
                "quantity", "depositAmount", "subscribedAt")) {
            Map<String, Object> request = validRequest(investorId, securitiesAccountId, ipoId);
            request.remove(field);
            assertValidationError(registerSubscription(request));
        }

        // And: 레코드가 생성되지 않음 (AC-002)
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    @Test
    void registerSubscriptionWithQuantityZeroOrNegativeOrNegativeDepositReturns400() throws Exception {
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
        MvcResult unknownInvestor = registerSubscription(
                validRequest(99999999L, securitiesAccountId, ipoId));
        assertThat(unknownInvestor.getResponse().getStatus())
                .as("존재하지 않는 investorId는 404를 반환해야 합니다 (AC-004)").isEqualTo(404);
        assertError(unknownInvestor, "NOT_FOUND");

        MvcResult unknownAccount = registerSubscription(
                validRequest(investorId, 99999999L, ipoId));
        assertThat(unknownAccount.getResponse().getStatus())
                .as("존재하지 않는 securitiesAccountId는 404를 반환해야 합니다 (AC-004)").isEqualTo(404);
        assertError(unknownAccount, "NOT_FOUND");

        MvcResult unknownIpo = registerSubscription(
                validRequest(investorId, securitiesAccountId, 99999999L));
        assertThat(unknownIpo.getResponse().getStatus())
                .as("존재하지 않는 ipoId는 404를 반환해야 합니다 (AC-004)").isEqualTo(404);
        assertError(unknownIpo, "NOT_FOUND");

        // And: 레코드가 생성되지 않음 (AC-004)
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    @Test
    void registerSubscriptionWithInvestorOrAccountOwnedByOtherAccountReturns403() throws Exception {
        // Given: 인증된 계정 A와 다른 계정 B가 있고, B 소유의 투자자/계좌 ID가 알려져 있음 (소유권 검증)
        signupAndLogin();
        Long otherInvestorId = createOtherAccountInvestorAndAccount();
        java.util.List<SecuritiesAccount> otherAccounts = securitiesAccountRepository.findByInvestorId(otherInvestorId);
        assertThat(otherAccounts).hasSize(1);
        Long otherAccountId = otherAccounts.get(0).getId();
        Long ipoId = createIpo();

        // When/Then: 계정 A가 계정 B의 투자자 ID로 등록 시 403 FORBIDDEN
        MvcResult otherInvestorResult = registerSubscription(
                validRequest(otherInvestorId, otherAccountId, ipoId));
        assertThat(otherInvestorResult.getResponse().getStatus())
                .as("다른 계정 소유 투자자로 등록 시 403 Forbidden을 반환해야 합니다 (소유권 검증)").isEqualTo(403);
        assertError(otherInvestorResult, "FORBIDDEN");

        // When/Then: 계정 A가 계정 B의 증권계좌 ID로 등록 시 403 FORBIDDEN
        Long ownInvestorId = registerInvestorAndGetId();
        MvcResult otherAccountResult = registerSubscription(
                validRequest(ownInvestorId, otherAccountId, ipoId));
        assertThat(otherAccountResult.getResponse().getStatus())
                .as("다른 계정 소유 증권계좌로 등록 시 403 Forbidden을 반환해야 합니다 (소유권 검증)").isEqualTo(403);
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
                .as("동일 조합 ACTIVE 중복 등록 시 409 Conflict를 반환해야 합니다 (AC-005)").isEqualTo(409);
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
        Subscription existing = subscriptionRepository.findById(
                ((Number) readBody(first).get("id")).longValue()).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        existing.setStatus("CANCELLED");
        existing.setDeletedAt(now);
        existing.setUpdatedAt(now);
        subscriptionRepository.saveAndFlush(existing);

        // When: 동일 조합으로 새 청약을 등록함
        MvcResult result = registerSubscription(validRequest(investorId, securitiesAccountId, ipoId));

        // Then: 201 Created + 새 ACTIVE 기록이 생성됨 (AC-006)
        assertThat(result.getResponse().getStatus())
                .as("CANCELLED 기록이 있어도 동일 조합 재등록은 201을 반환해야 합니다 (AC-006)").isEqualTo(201);
        Map<String, Object> response = readBody(result);
        assertThat(response.get("status")).isEqualTo("ACTIVE");
        assertThat(subscriptionRepository.findAll()).hasSize(2);
    }
}