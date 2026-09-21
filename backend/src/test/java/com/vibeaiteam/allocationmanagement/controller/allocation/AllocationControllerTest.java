package com.vibeaiteam.allocationmanagement.controller.allocation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
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
 * AC 인용: docs/spec/allocation-management.md, docs/breakdown/allocation-management.md SF-2
 * - AC-001: 배정 등록 성공 — 존재하는 Subscription과 유효한 배정수량/배정일시가 제공되면 201 + ACTIVE 기록 생성
 * - AC-002: 배정 등록 실패 (필수값 누락) — subscriptionId/allocatedQuantity/allocatedAt 중 하나 이상 누락 시 400
 * - AC-003: 배정 등록 실패 (배정수량 값 오류) — allocatedQuantity < 0 또는 청약수량 초과 시 400
 * - AC-004: 배정 등록 실패 (Subscription 미존재) — 존재하지 않는 subscriptionId → 404
 * - AC-005: 배정 등록 실패 (중복, 1:1 제약) — 동일 subscriptionId에 이미 ACTIVE 배정 기록 존재 시 409
 * - AC-006: 취소된 배정이 있으면 재등록 성공 — 동일 subscriptionId에 CANCELLED 배정만 있으면 201
 * - 소유권 검증: Allocation은 글로벌 기준정보로 계정 소유권 검증을 수행하지 않는다(SecurityUtils 미사용, breakdown 결정표)
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class AllocationControllerTest {

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
    private IpoRepository ipoRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    private String testEmail;
    private String testPassword;
    private String testName;
    private MockHttpSession session;
    private final List<SecuritiesFirm> createdFirms = new ArrayList<>();
    private final List<Ipo> createdIpos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long nonce = ThreadLocalRandom.current().nextLong();
        testEmail = "allocation-owner-" + nonce + "@example.com";
        testPassword = "securePass123!";
        testName = "배정테스터";
        session = new MockHttpSession();
    }

    @AfterEach
    void tearDown() {
        Account account = accountRepository.findByEmail(testEmail);
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
                List<Long> subscriptionIds = subscriptions.stream().map(Subscription::getId).toList();
                List<Allocation> allocations = allocationRepository.findAll().stream()
                        .filter(a -> subscriptionIds.contains(a.getSubscriptionId()))
                        .toList();
                allocationRepository.deleteAll(allocations);
                subscriptionRepository.deleteAll(subscriptions);
                securitiesAccountRepository.deleteAll(accounts);
            }
            investorRepository.deleteAll(investors);
            accountRepository.delete(account);
        }
        if (!createdFirms.isEmpty()) {
            securitiesFirmRepository.deleteAll(createdFirms);
        }
        if (!createdIpos.isEmpty()) {
            ipoRepository.deleteAll(createdIpos);
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
                                "displayName", "배정투자자",
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

    // 배정 등록 사전 준비: 투자자/증권계좌/IPO를 만들고 quantity 수량의 청약을 등록해 subscriptionId를 반환한다.
    private Long createSubscription(Long quantity) throws Exception {
        Long investorId = registerInvestorAndGetId();
        Long firmId = registerFirm();
        Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
        Long ipoId = createIpo();

        MvcResult result = mockMvc.perform(post("/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "investorId", investorId,
                                "securitiesAccountId", securitiesAccountId,
                                "ipoId", ipoId,
                                "quantity", quantity,
                                "depositAmount", 1000000,
                                "subscribedAt", "2026-10-01T09:00:00"))))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("테스트 사전 준비: 청약 등록은 201이어야 합니다").isEqualTo(201);
        return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
    }

    private Map<String, Object> validRequest(Long subscriptionId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("subscriptionId", subscriptionId);
        request.put("allocatedQuantity", 50);
        request.put("allocatedAt", "2026-10-05T18:00:00");
        request.put("allocationMethod", "균등배정");
        request.put("allocationPrice", 10000);
        request.put("note", "비고");
        return request;
    }

    private MvcResult registerAllocation(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/allocations")
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
    void registerAllocationWithValidRequestReturns201AndSavedAllocation() throws Exception {
        // Given: 존재하는 Subscription과 유효한 배정수량/배정일시가 제공됨 (AC-001)
        signupAndLogin();
        Long subscriptionId = createSubscription(100L);

        // When: POST /allocations 요청을 보냄
        MvcResult result = registerAllocation(validRequest(subscriptionId));

        // Then: 201 Created와 함께 신규 ACTIVE 배정 기록이 생성됨 (AC-001)
        assertThat(result.getResponse().getStatus())
                .as("유효한 배정 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);
        Map<String, Object> response = readBody(result);
        assertThat(response.get("id")).isNotNull();
        assertThat(((Number) response.get("subscriptionId")).longValue()).isEqualTo(subscriptionId);
        assertThat(((Number) response.get("allocatedQuantity")).longValue()).isEqualTo(50L);
        assertThat(response.get("allocatedAt")).isEqualTo("2026-10-05T18:00:00");
        assertThat(response.get("allocationMethod")).isEqualTo("균등배정");
        assertThat(((Number) response.get("allocationPrice")).longValue()).isEqualTo(10000L);
        assertThat(response.get("note")).isEqualTo("비고");
        assertThat(response.get("status")).isEqualTo("ACTIVE");
        assertThat(response.get("deletedAt")).isNull();
        assertThat(response.get("createdAt")).isNotNull();
        assertThat(response.get("updatedAt")).isNotNull();

        Allocation saved = allocationRepository.findById(
                ((Number) response.get("id")).longValue()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void registerAllocationWithMissingRequiredFieldsReturns400() throws Exception {
        // Given: 유효한 등록 준비가 끝나 있고, 필수 필드 중 하나씩을 제거해 요청함 (AC-002)
        signupAndLogin();
        Long subscriptionId = createSubscription(100L);

        // When/Then: subscriptionId/allocatedQuantity/allocatedAt 각각 누락 시 400 (AC-002)
        for (String field : List.of("subscriptionId", "allocatedQuantity", "allocatedAt")) {
            Map<String, Object> request = validRequest(subscriptionId);
            request.remove(field);
            assertValidationError(registerAllocation(request));
        }

        // And: 레코드가 생성되지 않음 (AC-002)
        assertThat(allocationRepository.findAll()).isEmpty();
    }

    @Test
    void registerAllocationWithInvalidQuantityReturns400() throws Exception {
        // Given: 청약수량 100인 Subscription이 존재함 (AC-003)
        signupAndLogin();
        Long subscriptionId = createSubscription(100L);

        // When/Then: allocatedQuantity < 0, allocatedQuantity > 청약수량 각각 400 (AC-003)
        Map<String, Object> negativeQuantity = validRequest(subscriptionId);
        negativeQuantity.put("allocatedQuantity", -1);
        assertValidationError(registerAllocation(negativeQuantity));

        Map<String, Object> overQuantity = validRequest(subscriptionId);
        overQuantity.put("allocatedQuantity", 101);
        assertValidationError(registerAllocation(overQuantity));

        // And: 레코드가 생성되지 않음 (AC-003)
        assertThat(allocationRepository.findAll()).isEmpty();
    }

    @Test
    void registerAllocationWithUnknownSubscriptionReturns404() throws Exception {
        // Given: 인증된 세션이 준비되어 있고 존재하지 않는 subscriptionId가 제공됨 (AC-004)
        signupAndLogin();

        // When: POST /allocations 요청을 보냄
        MvcResult result = registerAllocation(validRequest(99999999L));

        // Then: 404 Not Found + NOT_FOUND (AC-004)
        assertThat(result.getResponse().getStatus())
                .as("존재하지 않는 subscriptionId는 404를 반환해야 합니다 (AC-004)").isEqualTo(404);
        assertError(result, "NOT_FOUND");
        assertThat(allocationRepository.findAll()).isEmpty();
    }

    @Test
    void registerAllocationWithDuplicateActiveSubscriptionReturns409() throws Exception {
        // Given: 동일 subscriptionId의 ACTIVE 배정 기록이 이미 존재함 (AC-005)
        signupAndLogin();
        Long subscriptionId = createSubscription(100L);

        MvcResult first = registerAllocation(validRequest(subscriptionId));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        // When: 동일 subscriptionId로 다시 등록 요청
        MvcResult result = registerAllocation(validRequest(subscriptionId));

        // Then: 409 Conflict + DUPLICATE_ALLOCATION, 레코드는 1건만 유지됨 (AC-005)
        assertThat(result.getResponse().getStatus())
                .as("동일 subscriptionId ACTIVE 중복 등록 시 409 Conflict를 반환해야 합니다 (AC-005)").isEqualTo(409);
        assertError(result, "DUPLICATE_ALLOCATION");
        assertThat(allocationRepository.findAll()).hasSize(1);
    }

    @Test
    void registerAllocationAfterCancelAllowsReregisterReturns201() throws Exception {
        // Given: 동일 subscriptionId의 배정이 등록 후 취소(CANCELLED + deletedAt)됨 (AC-006)
        signupAndLogin();
        Long subscriptionId = createSubscription(100L);

        MvcResult first = registerAllocation(validRequest(subscriptionId));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        Allocation existing = allocationRepository.findById(
                ((Number) readBody(first).get("id")).longValue()).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        existing.setStatus("CANCELLED");
        existing.setDeletedAt(now);
        existing.setUpdatedAt(now);
        allocationRepository.saveAndFlush(existing);

        // When: 동일 subscriptionId로 새 배정을 등록함
        MvcResult result = registerAllocation(validRequest(subscriptionId));

        // Then: 201 Created + 새 ACTIVE 배정 기록이 생성됨 (AC-006)
        assertThat(result.getResponse().getStatus())
                .as("CANCELLED 기록이 있어도 동일 subscriptionId 재등록은 201을 반환해야 합니다 (AC-006)").isEqualTo(201);
        Map<String, Object> response = readBody(result);
        assertThat(response.get("status")).isEqualTo("ACTIVE");
        assertThat(allocationRepository.findAll()).hasSize(2);
    }
}
