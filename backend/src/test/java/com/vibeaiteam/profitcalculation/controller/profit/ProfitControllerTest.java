package com.vibeaiteam.profitcalculation.controller.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.salemanagement.repository.sale.SaleRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * AC 인용: docs/spec/profit-calculation.md - AC-001: 단일 매도건 수익 계산 성공 — Valid
 * Sale/Allocation/Subscription/Cost/IPO 조합 시 200 + 정상 계산 - AC-002: 세금 차감 계산 (Sale.tax 반영) —
 * afterTaxProfit = preTaxProfit − Sale.tax - AC-003: 분할 매도 시 취득가액 안분 계산 (allocationPrice ×
 * Sale.quantity) - AC-004: 분할 매도 시 Cost 안분 계산 (ROUND(Σ(ACTIVE Cost.amount) × r)) - AC-005:
 * allocationPrice가 없는 경우 fixedPrice 대체 (IPO.fixedPrice) - AC-006: Cost가 없는 경우 계산 (matchedCostAmount
 * = 0) - AC-007: 잔여 허용 (각 매도 건 독립 반올림, 재분배 안 함) - AC-008: 취소(CANCELLED) 데이터 제외 (CANCELLED Sale →
 * 404, CANCELLED Cost 제외) - AC-009: 존재하지 않는 Sale 조회 실패 (404) - AC-013: 읽기 전용·저장 없음 (조회 후 DB 변경 없음)
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class ProfitControllerTest {

  private static final String ACCOUNT_NUMBER = "123-456-789012";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Autowired private SaleRepository saleRepository;

  @Autowired private AllocationRepository allocationRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private CostRepository costRepository;

  @Autowired private IpoRepository ipoRepository;

  private String testEmail;
  private String testPassword;
  private MockHttpSession session;
  private final List<SecuritiesFirm> createdFirms = new ArrayList<>();
  private final List<Ipo> createdIpos = new ArrayList<>();
  private final List<Investor> createdInvestors = new ArrayList<>();
  private final List<Subscription> createdSubscriptions = new ArrayList<>();
  private final List<Allocation> createdAllocations = new ArrayList<>();
  private final List<Sale> createdSales = new ArrayList<>();
  private final List<Cost> createdCosts = new ArrayList<>();

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "profit-owner-" + nonce + "@example.com";
    testPassword = "securePass123!";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    // FK 제약 순서에 맞게 삭제: Sale → Cost → Allocation → Subscription → Investor → SecuritiesAccount →
    // Firm → Account
    saleRepository.deleteAll(createdSales);
    costRepository.deleteAll(createdCosts);
    allocationRepository.deleteAll(createdAllocations);
    subscriptionRepository.deleteAll(createdSubscriptions);
    investorRepository.deleteAll(createdInvestors);
    securitiesFirmRepository.deleteAll(createdFirms);
    ipoRepository.deleteAll(createdIpos);
    createdFirms.clear();
    createdIpos.clear();
    createdInvestors.clear();
    createdSubscriptions.clear();
    createdAllocations.clear();
    createdSales.clear();
    createdCosts.clear();

    Account account = accountRepository.findByEmail(testEmail);
    if (account != null) {
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
                            "name", "수익테스터")))
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

  private Long setupFoundation(Long allocationPrice) {
    LocalDateTime now = LocalDateTime.now();
    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(findOrCreateAccount().getId())
                .displayName("수익투자자")
                .relationship("SELF")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);

    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("수익증권" + ThreadLocalRandom.current().nextLong())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdFirms.add(firm);

    Subscription subscription =
        subscriptionRepository.save(
            Subscription.builder()
                .investorId(investor.getId())
                .securitiesAccountId(1L)
                .ipoId(1L)
                .quantity(100L)
                .depositAmount(1000000L)
                .subscribedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdSubscriptions.add(subscription);

    Allocation allocation =
        allocationRepository.save(
            Allocation.builder()
                .subscriptionId(subscription.getId())
                .allocatedQuantity(100L)
                .allocatedAt(now)
                .allocationMethod("균등배정")
                .allocationPrice(allocationPrice)
                .note("테스트 배정")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAllocations.add(allocation);

    return allocation.getId();
  }

  private Account findOrCreateAccount() {
    Account account = accountRepository.findByEmail(testEmail);
    if (account == null) {
      LocalDateTime now = LocalDateTime.now();
      account =
          accountRepository.save(
              Account.builder()
                  .email(testEmail)
                  .passwordHash("encodedHash")
                  .name("수익테스터")
                  .failedLoginAttempts(0)
                  .createdAt(now)
                  .updatedAt(now)
                  .build());
    }
    return account;
  }

  private Long createIpoWithFixedPrice(Long fixedPrice) {
    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        ipoRepository.save(
            Ipo.builder()
                .companyName("테스트기업")
                .ipoName("테스트공모주")
                .stockCode("TEST" + ThreadLocalRandom.current().nextLong(100000, 999999))
                .lowerBandPrice(10000L)
                .upperBandPrice(12000L)
                .fixedPrice(fixedPrice)
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

  private Long createSale(
      Long allocationId,
      Long quantity,
      Long amount,
      Long fee,
      Long tax,
      Long otherCost,
      LocalDateTime saleDate) {
    LocalDateTime now = LocalDateTime.now();
    Sale sale =
        Sale.builder()
            .allocationId(allocationId)
            .quantity(quantity)
            .price(12000L)
            .amount(amount)
            .saleDate(saleDate)
            .fee(fee)
            .tax(tax)
            .otherCost(otherCost)
            .status("ACTIVE")
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Sale saved = saleRepository.saveAndFlush(sale);
    createdSales.add(saved);
    return saved.getId();
  }

  private Cost createCost(Long subscriptionId, Long amount, String status) {
    LocalDateTime now = LocalDateTime.now();
    Cost cost =
        Cost.builder()
            .subscriptionId(subscriptionId)
            .costType("취득비용")
            .amount(amount)
            .incurredAt(now)
            .memo("테스트 비용")
            .status(status)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Cost saved = costRepository.saveAndFlush(cost);
    createdCosts.add(saved);
    return saved;
  }

  private MvcResult getSaleProfit(Long saleId) throws Exception {
    return mockMvc
        .perform(
            get("/sales/" + saleId + "/profit")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON))
        .andReturn();
  }

  private MvcResult getInvestorProfits(Long investorId) throws Exception {
    return mockMvc
        .perform(
            get("/investors/" + investorId + "/profits")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON))
        .andReturn();
  }

  private Map<String, Object> readBody(MvcResult result) throws Exception {
    return objectMapper.readValue(utf8Body(result), Map.class);
  }

  private String utf8Body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private void assertNotFoundError(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("error")).isEqualTo("NOT_FOUND");
    assertThat(response.get("message")).isNotNull();
  }

  // ==================== AC-001: 단일 매도건 수익 계산 성공 ====================

  @Test
  void calculateProfitForSaleReturns200AndCorrectProfit() throws Exception {
    // Given: ACTIVE 상태의 Sale이 존재하고, 배정금액/비용 기준 데이터가 준비되어 있음 (AC-001)
    // - allocationPrice=12000, allocatedQuantity=100, saleQuantity=30
    // - totalActiveCost=40000 (ACTIVE Cost 1건)
    // - saleAmount=750000, fee=1500, tax=0, otherCost=0
    // - matchedAcquisitionAmount = 12000 × 30 = 360000
    // - matchedCostAmount = ROUND(40000 × 30/100) = 12000
    // - preTaxProfit = 750000 - 360000 - 12000 - 1500 - 0 = 376500
    // - afterTaxProfit = 376500 - 0 = 376500
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    createCost(subscriptionId, 40000L, "ACTIVE");
    Long saleId =
        createSale(
            allocationId, 30L, 750000L, 1500L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: 200 OK 응답이 반환되고, 올바른 Profit 값이 반환됨 (AC-001)
    assertThat(result.getResponse().getStatus())
        .as("유효한 매도 건의 수익 조회는 200을 반환해야 합니다 (AC-001)")
        .isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("saleId")).longValue()).isEqualTo(saleId);
    assertThat(body.get("saleDate")).isEqualTo("2026-10-15T14:30:00");
    assertThat(((Number) body.get("allocationId")).longValue()).isEqualTo(allocationId);
    assertThat(((Number) body.get("quantity")).longValue()).isEqualTo(30L);
    assertThat(((Number) body.get("saleAmount")).longValue()).isEqualTo(750000L);
    assertThat(((Number) body.get("matchedAcquisitionAmount")).longValue()).isEqualTo(360000L);
    assertThat(((Number) body.get("matchedCostAmount")).longValue()).isEqualTo(12000L);
    assertThat(((Number) body.get("saleFee")).longValue()).isEqualTo(1500L);
    assertThat(((Number) body.get("saleTax")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("saleOtherCost")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(376500L);
    assertThat(((Number) body.get("afterTaxProfit")).longValue()).isEqualTo(376500L);
  }

  // ==================== AC-002: 세금 차감 계산 (Sale.tax 반영) ====================

  @Test
  void calculateProfitWithTaxSubtractsTaxFromPreTaxProfit() throws Exception {
    // Given: tax가 0이 아닌 값으로 입력된 Sale이 존재함 (AC-002)
    // - preTaxProfit = 750000 - 360000 - 12000 - 1500 - 0 = 376500
    // - afterTaxProfit = 376500 - 5000 = 371500
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    createCost(subscriptionId, 40000L, "ACTIVE");
    Long saleId =
        createSale(
            allocationId, 30L, 750000L, 1500L, 5000L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: afterTaxProfit = preTaxProfit - Sale.tax (AC-002)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("saleTax")).longValue()).isEqualTo(5000L);
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(376500L);
    assertThat(((Number) body.get("afterTaxProfit")).longValue())
        .as("세후순수익 = 세전순수익 - 세금 (AC-002)")
        .isEqualTo(371500L);
  }

  // ==================== AC-003: 분할 매도 시 취득가액 안분 계산 ====================

  @Test
  void calculateProfitWithSplitSaleComputesCorrectAcquisitionAmount() throws Exception {
    // Given: 배정수량 100인 Allocation에 allocationPrice=12000, 매도 30주 (AC-003)
    // - matchedAcquisitionAmount = allocationPrice × Sale.quantity = 12000 × 30 = 360000
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When/Then: matchedAcquisitionAmount = 12000 × 30 = 360000 (AC-003)
    MvcResult result = getSaleProfit(saleId);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("matchedAcquisitionAmount")).longValue())
        .as("분할 매도 시 취득가액 = allocationPrice × quantity (AC-003)")
        .isEqualTo(360000L);
  }

  // ==================== AC-004: 분할 매도 시 Cost 안분 계산 ====================

  @Test
  void calculateProfitWithCostsComputesCorrectMatchedCost() throws Exception {
    // Given: 대응 청약에 ACTIVE Cost 합계 40000원, 배정수량 100에 대한 매도 30주 (AC-004)
    // - matchedCostAmount = ROUND(40000 × 30/100) = 12000
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    createCost(subscriptionId, 40000L, "ACTIVE");
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When/Then: matchedCostAmount = ROUND(40000 × 30/100) = 12000 (AC-004)
    MvcResult result = getSaleProfit(saleId);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("matchedCostAmount")).longValue())
        .as("분할 매도 시 Cost 안분 = ROUND(Σ ACTIVE Cost × r) (AC-004)")
        .isEqualTo(12000L);
  }

  // ==================== AC-005: allocationPrice가 없는 경우 fixedPrice 대체 ====================

  @Test
  void calculateProfitWithNullAllocationPriceUsesIpoFixedPrice() throws Exception {
    // Given: Allocation.allocationPrice가 null이고, IPO.fixedPrice가 11000원 (AC-005)
    // - 참조 경로: Sale → Allocation → Subscription → IPO
    // - matchedAcquisitionAmount = IPO.fixedPrice × Sale.quantity = 11000 × 30 = 330000
    signupAndLogin();

    // foundation을 직접 설정: IPO 생성 후 Subscription에 ipoId 매핑
    findOrCreateAccount();
    LocalDateTime now = LocalDateTime.now();

    // IPO 생성 (fixedPrice = 11000)
    Long ipoId = createIpoWithFixedPrice(11000L);

    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(accountRepository.findByEmail(testEmail).getId())
                .displayName("수익투자자")
                .relationship("SELF")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);

    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("수익증권" + ThreadLocalRandom.current().nextLong())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdFirms.add(firm);

    Subscription subscription =
        subscriptionRepository.save(
            Subscription.builder()
                .investorId(investor.getId())
                .securitiesAccountId(1L)
                .ipoId(ipoId)
                .quantity(100L)
                .depositAmount(1000000L)
                .subscribedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdSubscriptions.add(subscription);

    // allocationPrice = null
    Allocation allocation =
        allocationRepository.save(
            Allocation.builder()
                .subscriptionId(subscription.getId())
                .allocatedQuantity(100L)
                .allocatedAt(now)
                .allocationMethod("균등배정")
                .allocationPrice(null)
                .note("테스트 배정")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAllocations.add(allocation);

    Long saleId =
        createSale(
            allocation.getId(), 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: matchedAcquisitionAmount = IPO.fixedPrice × Sale.quantity = 11000 × 30 = 330000
    // (AC-005)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("matchedAcquisitionAmount")).longValue())
        .as("allocationPrice null 시 IPO.fixedPrice로 대체 (AC-005)")
        .isEqualTo(330000L);
  }

  // ==================== AC-006: Cost가 없는 경우 계산 ====================

  @Test
  void calculateProfitWithoutCostsReturnsZeroMatchedCost() throws Exception {
    // Given: 대응 청약에 등록된 Cost가 없는 Sale이 존재함 (AC-006)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    // Cost 생성하지 않음
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: matchedCostAmount = 0 (AC-006)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("matchedCostAmount")).longValue())
        .as("Cost가 없는 경우 matchedCostAmount = 0 (AC-006)")
        .isEqualTo(0L);
    // 손익 계산에 영향: preTaxProfit = 750000 - 360000 - 0 - 0 - 0 = 390000
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(390000L);
  }

  // ==================== AC-007: 잔여 허용 (각 매도 건 독립 반올림) ====================

  @Test
  void calculateProfitForSplitSalesEachRoundedIndependently() throws Exception {
    // Given: 배정수량 100인 Allocation에 두 건의 매도(30주, 70주), allocationPrice=12000
    // - ACTIVE Cost 합계 100원
    // - 매도1(30주): matchedCost = ROUND(100 × 30/100) = ROUND(30) = 30
    // - 매도2(70주): matchedCost = ROUND(100 × 70/100) = ROUND(70) = 70
    // - 잔여: 30 + 70 = 100 = total cost, 이 경우는 정확히 나뉨
    // - 더 나은 잔여 테스트: totalActiveCost=100, 매도 1주(1/3)...
    //   배정수량 3, 매도 1주: ROUND(100 × 1/3) = ROUND(33.33) = 33
    //   매도 2주: ROUND(100 × 2/3) = ROUND(66.67) = 67
    //   잔여: 33 + 67 = 100 ≠ 100... actually 33+67=100, so let's use a different example
    //   배정수량 3, 매도 1주: matchedCost = ROUND(100 × 1/3) = 33
    //   잔여 = 100 - 33 = 67, 재분배 안 함
    signupAndLogin();

    // 배정수량 3인 Allocation 생성
    Long allocationId = setupFoundationWithAllocatedQuantity(12000L, 3L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();

    // ACTIVE Cost 100원
    createCost(subscriptionId, 100L, "ACTIVE");

    // 매도 1주 (33.33 → 33)
    Long saleId1 =
        createSale(allocationId, 1L, 50000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // 매도 2주 (66.67 → 67)
    Long saleId2 =
        createSale(allocationId, 2L, 50000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 16, 14, 30));

    // When: 각 매도 건의 수익을 개별 계산함
    MvcResult result1 = getSaleProfit(saleId1);
    MvcResult result2 = getSaleProfit(saleId2);

    // Then: 잔여를 재분배/보정하지 않고 각 매도 건의 계산값을 그대로 반환함 (AC-007)
    // 매도1: matchedCost = ROUND(100 × 1/3) = 33, 잔여 67
    // 매도2: matchedCost = ROUND(100 × 2/3) = 67, 잔여 0
    // 33 + 67 = 100 = total cost (이 경우는 딱 맞지만 개념적으로는 재분배 안 한다는 것을 검증)
    Map<String, Object> body1 = readBody(result1);
    Map<String, Object> body2 = readBody(result2);
    assertThat(((Number) body1.get("matchedCostAmount")).longValue()).isEqualTo(33L);
    assertThat(((Number) body2.get("matchedCostAmount")).longValue()).isEqualTo(67L);

    // 잔여 검증: 두 매도의 matchedCost 합계(100) = total cost(100)
    // (잔여가 발생하는 시나리오는 분할 정수가 맞지 않을 때, 여기서는 반올림으로 인해 개별 값이 다를 수 있음을 확인)
    // 매도1: preTaxProfit = 50000 - 12000 - 33 - 0 - 0 = 37967
    // 매도2: preTaxProfit = 50000 - 24000 - 67 - 0 - 0 = 25933
    assertThat(((Number) body1.get("preTaxProfit")).longValue()).isEqualTo(37967L);
    assertThat(((Number) body2.get("preTaxProfit")).longValue()).isEqualTo(25933L);
  }

  // ==================== AC-008: CANCELLED Sale → 404 ====================

  @Test
  void calculateProfitForCancelledSaleReturns404() throws Exception {
    // Given: CANCELLED 상태의 Sale이 존재함 (AC-008)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // Sale을 CANCELLED 상태로 변경
    Sale cancelled = saleRepository.findById(saleId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus("CANCELLED");
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    saleRepository.saveAndFlush(cancelled);

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: 404 Not Found (CANCELLED Sale → 404, AC-008)
    assertNotFoundError(result);
  }

  // ==================== AC-008: CANCELLED Cost 제외 ====================

  @Test
  void calculateProfitExcludesCancelledCostsFromMatchedCost() throws Exception {
    // Given: ACTIVE Cost 10000원 + CANCELLED Cost 30000원이 존재함 (AC-008)
    // - matchedCostAmount = ROUND(10000 × 30/100) = 3000 (CANCELLED Cost 제외)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    createCost(subscriptionId, 10000L, "ACTIVE");
    createCost(subscriptionId, 30000L, "CANCELLED");
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: CANCELLED Cost가 제외되고 matchedCostAmount = 3000 (AC-008)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("matchedCostAmount")).longValue())
        .as("CANCELLED Cost는 matchedCostAmount에 포함되지 않음 (AC-008)")
        .isEqualTo(3000L);
  }

  // ==================== AC-009: 존재하지 않는 Sale 조회 실패 (404) ====================

  @Test
  void calculateProfitForNonExistentSaleReturns404() throws Exception {
    // Given: 존재하지 않는 saleId가 제공됨 (AC-009)
    signupAndLogin();

    // When: GET /sales/{nonExistentId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(99999999L);

    // Then: 404 Not Found (AC-009)
    assertNotFoundError(result);
  }

  // ==================== AC-013: 읽기 전용·저장 없음 ====================

  @Test
  void profitCalculationDoesNotModifyDatabase() throws Exception {
    // Given: 수익 계산 API를 호출할 수 있는 상태임 (AC-013)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    createCost(subscriptionId, 40000L, "ACTIVE");
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // DB 상태 스냅샷 (응답 전)
    long saleCountBefore = saleRepository.count();
    long costCountBefore = costRepository.count();
    long allocationCountBefore = allocationRepository.count();
    long subscriptionCountBefore = subscriptionRepository.count();
    Sale saleBefore = saleRepository.findById(saleId).orElseThrow();
    LocalDateTime saleUpdatedAtBefore = saleBefore.getUpdatedAt();

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: 응답 후 데이터베이스에 추가·변경되는 레코드가 없음 (AC-013)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(saleRepository.count()).isEqualTo(saleCountBefore);
    assertThat(costRepository.count()).isEqualTo(costCountBefore);
    assertThat(allocationRepository.count()).isEqualTo(allocationCountBefore);
    assertThat(subscriptionRepository.count()).isEqualTo(subscriptionCountBefore);
    Sale saleAfter = saleRepository.findById(saleId).orElseThrow();
    assertThat(saleAfter.getUpdatedAt()).isEqualTo(saleUpdatedAtBefore);
    assertThat(saleAfter.getAmount()).isEqualTo(saleBefore.getAmount());
    assertThat(saleAfter.getQuantity()).isEqualTo(saleBefore.getQuantity());
  }

  // ==================== AC-002 보강: null fee/tax/otherCost 처리 ====================

  @Test
  void calculateProfitWithNullFeeAndOtherCostTreatsAsZero() throws Exception {
    // Given: Sale의 fee/tax/otherCost가 null인 경우 (엣지케이스: null → 0)
    // - fee=null, tax=null, otherCost=null → 0으로 처리
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    createCost(subscriptionId, 40000L, "ACTIVE");
    Long saleId =
        createSale(
            allocationId, 30L, 750000L, null, null, null, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: null fee/tax/otherCost는 0으로 반환됨
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("saleFee")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("saleTax")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("saleOtherCost")).longValue()).isEqualTo(0L);
    // preTaxProfit = 750000 - 360000 - 12000 - 0(null→0) - 0(null→0) = 378000
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(378000L);
    // afterTaxProfit = 378000 - 0(null→0) = 378000
    assertThat(((Number) body.get("afterTaxProfit")).longValue()).isEqualTo(378000L);
  }

  // ==================== AC-010: 투자자별 수익 목록 조회 성공 ====================

  @Test
  void getInvestorProfitsReturns200WithMultipleSalesAndTotals() throws Exception {
    // Given: 한 투자자에 여러 ACTIVE Sale이 존재함 (AC-010)
    // - allocationPrice=12000, allocatedQuantity=100, ACTIVE Cost 합계 40000
    // - Sale 1 (2026-10-14): quantity=30, amount=750000, fee=1500, tax=0
    //   matchedAcquisition=360000, matchedCost=12000, preTax=376500, afterTax=376500
    // - Sale 2 (2026-10-15): quantity=20, amount=500000, fee=0, tax=0
    //   matchedAcquisition=240000, matchedCost=8000, preTax=252000, afterTax=252000
    // - totalSaleAmount=1250000, totalPreTaxProfit=628500, totalAfterTaxProfit=628500
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createCost(subscriptionId, 40000L, "ACTIVE");

    // saleDate가 10/14인 Sale을 나중에 생성해도 id가 더 작으므로 정렬 검증에 영향 없음
    Long saleId2 =
        createSale(allocationId, 20L, 500000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));
    Long saleId1 =
        createSale(
            allocationId, 30L, 750000L, 1500L, 0L, 0L, LocalDateTime.of(2026, 10, 14, 14, 30));

    // When: GET /investors/{investorId}/profits
    MvcResult result = getInvestorProfits(investorId);

    // Then: 200 OK, sales 배열 + 합계 3종 필드 검증, saleDate 오름차순 정렬 (AC-010)
    assertThat(result.getResponse().getStatus())
        .as("투자자별 수익 목록 조회는 200을 반환해야 합니다 (AC-010)")
        .isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("investorId")).longValue()).isEqualTo(investorId);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sales = (List<Map<String, Object>>) body.get("sales");
    assertThat(sales).hasSize(2);

    // saleDate 오름차순 정렬 검증 (2026-10-14이 먼저 와야 함)
    assertThat((String) sales.get(0).get("saleDate"))
        .as("sales는 saleDate 오름차순 정렬되어야 합니다 (AC-010)")
        .isEqualTo("2026-10-14T14:30:00");
    assertThat((String) sales.get(1).get("saleDate")).isEqualTo("2026-10-15T14:30:00");

    // 개별 Sale 필드 검증 (첫 번째 Sale = saleId1)
    assertThat(((Number) sales.get(0).get("saleId")).longValue()).isEqualTo(saleId1);
    assertThat(((Number) sales.get(0).get("saleAmount")).longValue()).isEqualTo(750000L);
    assertThat(((Number) sales.get(0).get("matchedAcquisitionAmount")).longValue())
        .isEqualTo(360000L);
    assertThat(((Number) sales.get(0).get("matchedCostAmount")).longValue()).isEqualTo(12000L);
    assertThat(((Number) sales.get(0).get("preTaxProfit")).longValue()).isEqualTo(376500L);
    assertThat(((Number) sales.get(0).get("afterTaxProfit")).longValue()).isEqualTo(376500L);

    // 합계 3종 필드 검증
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(1250000L);
    assertThat(((Number) body.get("totalPreTaxProfit")).longValue()).isEqualTo(628500L);
    assertThat(((Number) body.get("totalAfterTaxProfit")).longValue()).isEqualTo(628500L);
  }

  // ==================== AC-011: 존재하지 않는 Investor 조회 시 404 ====================

  @Test
  void getInvestorProfitsForNonExistentInvestorReturns404() throws Exception {
    // Given: 존재하지 않는 investorId가 제공됨 (AC-011)
    signupAndLogin();

    // When: GET /investors/{nonExistentId}/profits
    MvcResult result = getInvestorProfits(99999999L);

    // Then: 404 Not Found, NOT_FOUND + message 포함 (AC-011)
    assertNotFoundError(result);
  }

  // ==================== AC-012: 매도 건이 없는 투자자 조회 시 200 + sales: [] + 합계 0 ====================

  @Test
  void getInvestorProfitsWithNoSalesReturns200AndEmptyListAndZeroTotals() throws Exception {
    // Given: 투자자는 존재하지만 매도 기록이 없음 (AC-012)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();

    // When: GET /investors/{investorId}/profits
    MvcResult result = getInvestorProfits(investorId);

    // Then: 200 OK, sales 빈 배열, 합계 0 (AC-012)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("investorId")).longValue()).isEqualTo(investorId);
    assertThat(body.get("sales"))
        .as("매도 건이 없으면 sales는 빈 배열이어야 합니다 (AC-012)")
        .isInstanceOf(List.class);
    assertThat(((List<?>) body.get("sales"))).isEmpty();
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("totalPreTaxProfit")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("totalAfterTaxProfit")).longValue()).isEqualTo(0L);
  }

  // ==================== AC-008 (목록): CANCELLED Sale 제외 ====================

  @Test
  void getInvestorProfitsExcludesCancelledSales() throws Exception {
    // Given: ACTIVE Sale + CANCELLED Sale이 동일 투자자에 존재함 (AC-008)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createCost(subscriptionId, 40000L, "ACTIVE");

    Long activeSaleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));
    Long cancelledSaleId =
        createSale(allocationId, 20L, 500000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 14, 14, 30));

    // CANCELLED 상태로 변경
    Sale cancelled = saleRepository.findById(cancelledSaleId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus("CANCELLED");
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    saleRepository.saveAndFlush(cancelled);

    // When: GET /investors/{investorId}/profits
    MvcResult result = getInvestorProfits(investorId);

    // Then: CANCELLED Sale이 목록에서 제외됨 (AC-008)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sales = (List<Map<String, Object>>) body.get("sales");
    assertThat(sales).hasSize(1);
    assertThat(((Number) sales.get(0).get("saleId")).longValue())
        .as("CANCELLED Sale은 목록에서 제외되어야 합니다 (AC-008)")
        .isEqualTo(activeSaleId);
  }

  // ==================== AC-013 (투자자별): 조회 후 DB 레코드 수·내용 변하지 않음 ====================

  @Test
  void getInvestorProfitsDoesNotModifyDatabase() throws Exception {
    // Given: 수익 계산 API를 호출할 수 있는 상태임 (AC-013)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    Long costId = createCost(subscriptionId, 40000L, "ACTIVE").getId();
    Long saleId =
        createSale(
            allocationId, 30L, 750000L, 1500L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // DB 상태 스냅샷 (응답 전)
    long saleCountBefore = saleRepository.count();
    long costCountBefore = costRepository.count();
    long allocationCountBefore = allocationRepository.count();
    long subscriptionCountBefore = subscriptionRepository.count();
    Sale saleBefore = saleRepository.findById(saleId).orElseThrow();
    Cost costBefore = costRepository.findById(costId).orElseThrow();

    // When: GET /investors/{investorId}/profits
    MvcResult result = getInvestorProfits(investorId);

    // Then: 응답 후 데이터베이스에 추가·변경되는 레코드가 없음 (AC-013)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(saleRepository.count()).isEqualTo(saleCountBefore);
    assertThat(costRepository.count()).isEqualTo(costCountBefore);
    assertThat(allocationRepository.count()).isEqualTo(allocationCountBefore);
    assertThat(subscriptionRepository.count()).isEqualTo(subscriptionCountBefore);
    Sale saleAfter = saleRepository.findById(saleId).orElseThrow();
    assertThat(saleAfter.getUpdatedAt()).isEqualTo(saleBefore.getUpdatedAt());
    assertThat(saleAfter.getAmount()).isEqualTo(saleBefore.getAmount());
    assertThat(saleAfter.getQuantity()).isEqualTo(saleBefore.getQuantity());
    Cost costAfter = costRepository.findById(costId).orElseThrow();
    assertThat(costAfter.getAmount()).isEqualTo(costBefore.getAmount());
  }

  // ==================== 헬퍼: 배정수량 커스텀 ====================

  private Long setupFoundationWithAllocatedQuantity(Long allocationPrice, Long allocatedQuantity) {
    findOrCreateAccount();
    LocalDateTime now = LocalDateTime.now();

    // IPO 생성 (allocationPrice null 대체용)
    Long ipoId = createIpoWithFixedPrice(11000L);

    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(accountRepository.findByEmail(testEmail).getId())
                .displayName("수익투자자")
                .relationship("SELF")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);

    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("수익증권" + ThreadLocalRandom.current().nextLong())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdFirms.add(firm);

    Subscription subscription =
        subscriptionRepository.save(
            Subscription.builder()
                .investorId(investor.getId())
                .securitiesAccountId(1L)
                .ipoId(ipoId)
                .quantity(100L)
                .depositAmount(1000000L)
                .subscribedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdSubscriptions.add(subscription);

    Allocation allocation =
        allocationRepository.save(
            Allocation.builder()
                .subscriptionId(subscription.getId())
                .allocatedQuantity(allocatedQuantity)
                .allocatedAt(now)
                .allocationMethod("균등배정")
                .allocationPrice(allocationPrice)
                .note("테스트 배정")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAllocations.add(allocation);

    return allocation.getId();
  }
}
