package com.vibeaiteam.salemanagement.controller.sale;

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
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC 인용: docs/spec/sale-management.md, docs/breakdown/sale-management.md SF-2
 * - AC-001: 등록 성공 — 존재하는 Allocation과 유효한 매도수량/매도일시가 제공되면 201 + ACTIVE 기록 생성
 * - AC-002: 등록 실패 (필수값 누락) — allocationId/quantity/saleDate 중 하나 이상 누락 시 400
 * - AC-003: 등록 실패 (매도수량 값 오류) — quantity ≤ 0 시 400
 * - AC-004: 등록 실패 (Allocation 미존재) — 존재하지 않는 allocationId → 404
 * - AC-005: 등록 실패 (누적 매도수량 초과) — 기존 ACTIVE 매도량 합계 + 신규 quantity > allocatedQuantity → 400
 * - AC-006: 분할 매도 등록 성공 — 기존 ACTIVE 매도량 합계 + 신규 quantity ≤ allocatedQuantity면 여러 건 등록 가능
 * - 소유권 검증: Sale은 글로벌 기준정보로 계정 소유권 검증을 수행하지 않는다(breakdown 결정표)
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SaleControllerTest {

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

    @Autowired
    private SaleRepository saleRepository;

    private String testEmail;
    private String testPassword;
    private String testName;
    private MockHttpSession session;
    private final List<SecuritiesFirm> createdFirms = new ArrayList<>();
    private final List<Ipo> createdIpos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long nonce = ThreadLocalRandom.current().nextLong();
        testEmail = "sale-owner-" + nonce + "@example.com";
        testPassword = "securePass123!";
        testName = "매도테스터";
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
                List<Long> allocationIds = allocations.stream().map(Allocation::getId).toList();
                List<Sale> sales = saleRepository.findAll().stream()
                        .filter(s -> allocationIds.contains(s.getAllocationId()))
                        .toList();
                saleRepository.deleteAll(sales);
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
                                "displayName", "매도투자자",
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
        String uniqueCode = "123456" + ThreadLocalRandom.current().nextLong(100000, 999999);
        Ipo ipo = ipoRepository.save(Ipo.builder()
                .companyName("기업A")
                .ipoName("공모주A")
                .stockCode(uniqueCode)
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

    // Allocation 생성 사전 준비: 투자자/증권계좌/IPO/청약을 만들고 allocationId를 반환한다.
    // allocatedQuantity 파라미터로 배정수량을 지정할 수 있다(기본 100).
    private Long createAllocation(Long allocatedQuantity) throws Exception {
        Long investorId = registerInvestorAndGetId();
        Long firmId = registerFirm();
        Long securitiesAccountId = registerSecuritiesAccount(investorId, firmId);
        Long ipoId = createIpo();

        MvcResult subResult = mockMvc.perform(post("/subscriptions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "investorId", investorId,
                                "securitiesAccountId", securitiesAccountId,
                                "ipoId", ipoId,
                                "quantity", 200L, // 청약수량은 충분히 크게
                                "depositAmount", 1000000,
                                "subscribedAt", "2026-10-01T09:00:00"))))
                .andReturn();
        assertThat(subResult.getResponse().getStatus())
                .as("테스트 사전 준비: 청약 등록은 201이어야 합니다").isEqualTo(201);
        Long subscriptionId = ((Number) objectMapper.readValue(utf8Body(subResult), Map.class).get("id")).longValue();

        MvcResult allocResult = mockMvc.perform(post("/allocations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subscriptionId", subscriptionId,
                                "allocatedQuantity", allocatedQuantity,
                                "allocatedAt", "2026-10-05T18:00:00",
                                "allocationMethod", "균등배정",
                                "allocationPrice", 10000,
                                "note", "배정 비고"))))
                .andReturn();
        assertThat(allocResult.getResponse().getStatus())
                .as("테스트 사전 준비: 배정 등록은 201이어야 합니다").isEqualTo(201);
        return ((Number) objectMapper.readValue(utf8Body(allocResult), Map.class).get("id")).longValue();
    }

    private Map<String, Object> validSaleRequest(Long allocationId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("allocationId", allocationId);
        request.put("quantity", 30L);
        request.put("price", 12000L);
        request.put("amount", 360000L);
        request.put("saleDate", "2026-10-10T14:00:00");
        request.put("fee", 1000L);
        request.put("tax", 500L);
        request.put("otherCost", 200L);
        return request;
    }

    private MvcResult registerSale(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/sales")
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

    private void assertAllocationExceededError(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertError(result, "ALLOCATION_EXCEEDED");
    }

    private void assertNotFoundError(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertError(result, "NOT_FOUND");
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
    void registerSaleWithValidRequestReturns201AndSavedSale() throws Exception {
        // Given: 존재하는 Allocation과 유효한 매도수량/매도일시가 제공됨 (AC-001)
        signupAndLogin();
        Long allocationId = createAllocation(100L); // 배정수량 100

        // When: POST /sales 요청을 보냄
        MvcResult result = registerSale(validSaleRequest(allocationId));

        // Then: 201 Created와 함께 신규 ACTIVE 매도 기록이 생성됨 (AC-001)
        assertThat(result.getResponse().getStatus())
                .as("유효한 매도 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);
        Map<String, Object> response = readBody(result);
        assertThat(response.get("id")).isNotNull();
        assertThat(((Number) response.get("allocationId")).longValue()).isEqualTo(allocationId);
        assertThat(((Number) response.get("quantity")).longValue()).isEqualTo(30L);
        assertThat(((Number) response.get("price")).longValue()).isEqualTo(12000L);
        assertThat(((Number) response.get("amount")).longValue()).isEqualTo(360000L);
        assertThat(response.get("saleDate")).isEqualTo("2026-10-10T14:00:00");
        assertThat(((Number) response.get("fee")).longValue()).isEqualTo(1000L);
        assertThat(((Number) response.get("tax")).longValue()).isEqualTo(500L);
        assertThat(((Number) response.get("otherCost")).longValue()).isEqualTo(200L);
        assertThat(response.get("status")).isEqualTo("ACTIVE");
        assertThat(response.get("deletedAt")).isNull();
        assertThat(response.get("createdAt")).isNotNull();
        assertThat(response.get("updatedAt")).isNotNull();

        Sale saved = saleRepository.findById(
                ((Number) response.get("id")).longValue()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void registerSaleWithMissingRequiredFieldsReturns400() throws Exception {
        // Given: 유효한 등록 준비가 끝나 있고, 필수 필드 중 하나씩을 제거해 요청함 (AC-002)
        signupAndLogin();
        Long allocationId = createAllocation(100L);

        // When/Then: allocationId/quantity/saleDate 각각 누락 시 400 (AC-002)
        for (String field : List.of("allocationId", "quantity", "saleDate")) {
            Map<String, Object> request = validSaleRequest(allocationId);
            request.remove(field);
            assertValidationError(registerSale(request));
        }

        // And: 레코드가 생성되지 않음 (AC-002)
        assertThat(saleRepository.findAll()).isEmpty();
    }

    @Test
    void registerSaleWithInvalidQuantityReturns400() throws Exception {
        // Given: 배정수량 100인 Allocation이 존재함 (AC-003)
        signupAndLogin();
        Long allocationId = createAllocation(100L);

        // When/Then: quantity = 0, quantity < 0 각각 400 (AC-003)
        Map<String, Object> zeroQuantity = validSaleRequest(allocationId);
        zeroQuantity.put("quantity", 0);
        assertValidationError(registerSale(zeroQuantity));

        Map<String, Object> negativeQuantity = validSaleRequest(allocationId);
        negativeQuantity.put("quantity", -1);
        assertValidationError(registerSale(negativeQuantity));

        // And: 레코드가 생성되지 않음 (AC-003)
        assertThat(saleRepository.findAll()).isEmpty();
    }

    @Test
    void registerSaleWithUnknownAllocationReturns404() throws Exception {
        // Given: 인증된 세션이 준비되어 있고 존재하지 않는 allocationId가 제공됨 (AC-004)
        signupAndLogin();

        // When: POST /sales 요청을 보냄
        MvcResult result = registerSale(validSaleRequest(99999999L));

        // Then: 404 Not Found + NOT_FOUND (AC-004)
        assertThat(result.getResponse().getStatus())
                .as("존재하지 않는 allocationId는 404를 반환해야 합니다 (AC-004)").isEqualTo(404);
        assertNotFoundError(result);
        assertThat(saleRepository.findAll()).isEmpty();
    }

    @Test
    void registerSaleWithExceedingCumulativeQuantityReturns400() throws Exception {
        // Given: 배정수량 100인 Allocation에 이미 60주 매도 기록이 존재함 (AC-005)
        signupAndLogin();
        Long allocationId = createAllocation(100L);

        // 첫 번째 매도: 60주 (배정수량 100 중 60 사용)
        Map<String, Object> firstRequest = validSaleRequest(allocationId);
        firstRequest.put("quantity", 60L);
        MvcResult first = registerSale(firstRequest);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        // When: 두 번째 매도에서 50주 시도 (60 + 50 = 110 > 100 초과) (AC-005)
        Map<String, Object> secondRequest = validSaleRequest(allocationId);
        secondRequest.put("quantity", 50L);
        MvcResult result = registerSale(secondRequest);

        // Then: 400 Bad Request + ALLOCATION_EXCEEDED (AC-005)
        assertThat(result.getResponse().getStatus())
                .as("누적 매도수량이 배정수량을 초과하면 400을 반환해야 합니다 (AC-005)").isEqualTo(400);
        assertAllocationExceededError(result);

        // And: 두 번째 매도는 저장되지 않음 (기존 1건만 유지)
        assertThat(saleRepository.findAll()).hasSize(1);
        assertThat(saleRepository.findAll().get(0).getQuantity()).isEqualTo(60L);
    }

    @Test
    void registerMultipleSalesForSameAllocationSucceedsIfWithinLimit() throws Exception {
        // Given: 배정수량 100인 Allocation (AC-006, 분할 매도)
        signupAndLogin();
        Long allocationId = createAllocation(100L);

        // When: 첫 번째 매도 30주 등록
        Map<String, Object> firstRequest = validSaleRequest(allocationId);
        firstRequest.put("quantity", 30L);
        MvcResult first = registerSale(firstRequest);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        // When: 두 번째 매도 40주 등록 (누적 70 ≤ 100)
        Map<String, Object> secondRequest = validSaleRequest(allocationId);
        secondRequest.put("quantity", 40L);
        MvcResult second = registerSale(secondRequest);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);

        // When: 세 번째 매도 30주 등록 (누적 100 ≤ 100, 정확히 배정수량 채움)
        Map<String, Object> thirdRequest = validSaleRequest(allocationId);
        thirdRequest.put("quantity", 30L);
        MvcResult third = registerSale(thirdRequest);
        assertThat(third.getResponse().getStatus()).isEqualTo(201);

        // Then: 3건 모두 ACTIVE로 저장됨, 누적 합계 = 100 = 배정수량 (AC-006)
        assertThat(saleRepository.findAll()).hasSize(3);
        long totalQuantity = saleRepository.findAll().stream()
                .mapToLong(Sale::getQuantity)
                .sum();
        assertThat(totalQuantity).isEqualTo(100L);

        // And: 각 매도 기록이 올바르게 저장되었는지 확인
        List<Sale> sales = saleRepository.findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(allocationId, "ACTIVE");
        assertThat(sales).hasSize(3);
        for (Sale sale : sales) {
            assertThat(sale.getStatus()).isEqualTo("ACTIVE");
            assertThat(sale.getDeletedAt()).isNull();
        }
    }

    @Test
    void registerSaleWithInvalidPriceReturns400() throws Exception {
        // Given: 유효한 Allocation 존재 (가격/금액/수수료 등 선택 필드 0 이상 검증)
        signupAndLogin();
        Long allocationId = createAllocation(100L);

        // When/Then: price < 0, amount < 0, fee < 0, tax < 0, otherCost < 0 각각 400
        Map<String, Object> negativePrice = validSaleRequest(allocationId);
        negativePrice.put("price", -100L);
        assertValidationError(registerSale(negativePrice));

        Map<String, Object> negativeAmount = validSaleRequest(allocationId);
        negativeAmount.put("amount", -100L);
        assertValidationError(registerSale(negativeAmount));

        Map<String, Object> negativeFee = validSaleRequest(allocationId);
        negativeFee.put("fee", -100L);
        assertValidationError(registerSale(negativeFee));

        Map<String, Object> negativeTax = validSaleRequest(allocationId);
        negativeTax.put("tax", -100L);
        assertValidationError(registerSale(negativeTax));

        Map<String, Object> negativeOtherCost = validSaleRequest(allocationId);
        negativeOtherCost.put("otherCost", -100L);
        assertValidationError(registerSale(negativeOtherCost));

        assertThat(saleRepository.findAll()).isEmpty();
    }
}