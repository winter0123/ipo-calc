package com.vibeaiteam.profitcalculation.controller.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
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
import com.vibeaiteam.testsupport.AuthTestSupport;
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
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * AC 인용: docs/spec/profit-calculation.md - AC-001: 단일 매도건 수익 계산 성공 — Valid
 * Sale/Allocation/Subscription/IPO 조합 시 200 + 정상 계산 - AC-002: 세금 차감 계산 (Sale.tax 반영) —
 * afterTaxProfit = preTaxProfit − Sale.tax - AC-003: 분할 매도 시 취득가액 안분 계산 (allocationPrice ×
 * Sale.quantity) - AC-005: allocationPrice가 없는 경우 fixedPrice 대체 (IPO.fixedPrice) - AC-008:
 * 취소(CANCELLED) 데이터 제외 (CANCELLED Sale → 404) - AC-009: 존재하지 않는 Sale 조회 실패 (404) - AC-013: 읽기 전용·저장
 * 없음 (조회 후 DB 변경 없음).
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
  private final List<Account> createdOtherAccounts = new ArrayList<>();

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "profit-owner-" + nonce + "@example.com";
    testPassword = "securePass123!";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    // FK 제약 순서에 맞게 삭제: Sale → Allocation → Subscription → Investor → SecuritiesAccount →
    // Firm → Account
    saleRepository.deleteAll(createdSales);
    allocationRepository.deleteAll(createdAllocations);
    subscriptionRepository.deleteAll(createdSubscriptions);
    investorRepository.deleteAll(createdInvestors);
    accountRepository.deleteAll(createdOtherAccounts);
    securitiesFirmRepository.deleteAll(createdFirms);
    ipoRepository.deleteAll(createdIpos);
    createdFirms.clear();
    createdIpos.clear();
    createdInvestors.clear();
    createdSubscriptions.clear();
    createdAllocations.clear();
    createdSales.clear();
    createdOtherAccounts.clear();

    Account account = accountRepository.findByEmail(testEmail);
    if (account != null) {
      accountRepository.delete(account);
    }
  }

  private void signupAndLogin() throws Exception {
    AuthTestSupport.signupAndLogin(
        mockMvc, objectMapper, session, testEmail, testPassword, "수익테스터");
  }

  private Long setupFoundation(Long allocationPrice) {
    LocalDateTime now = LocalDateTime.now();
    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(findOrCreateAccount().getId())
                .displayName("수익투자자")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);

    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("수익증권" + ThreadLocalRandom.current().nextLong())
                .status(RecordStatus.ACTIVE)
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
                .status(RecordStatus.ACTIVE)
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
                .status(RecordStatus.ACTIVE)
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

  // 다른 계정이 소유한 투자자를 만들어 그 ID를 반환한다 (소유권 검증용).
  private Long createOtherAccountInvestor() {
    LocalDateTime now = LocalDateTime.now();
    Account otherAccount =
        accountRepository.save(
            Account.builder()
                .email("profit-other-" + ThreadLocalRandom.current().nextLong() + "@example.com")
                .passwordHash("encodedHash")
                .name("다른계정")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdOtherAccounts.add(otherAccount);

    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(otherAccount.getId())
                .displayName("다른계정투자자")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);
    return investor.getId();
  }

  // 배정의 청약이 가리키는 공모주를 주어진 상장일(null이면 상장일 없음 → 매도일로 기간 판단)의 새 공모주로 바꾼다.
  private void useIpoListedOn(Long allocationId, java.time.LocalDate listingDate) {
    Ipo ipo = ipoRepository.findById(createIpoWithFixedPrice(12000L)).orElseThrow();
    ipo.setListingDate(listingDate);
    ipoRepository.save(ipo);
    Subscription subscription =
        subscriptionRepository
            .findById(allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId())
            .orElseThrow();
    subscription.setIpoId(ipo.getId());
    subscriptionRepository.save(subscription);
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
                .status(RecordStatus.ACTIVE)
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
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Sale saved = saleRepository.saveAndFlush(sale);
    createdSales.add(saved);
    return saved.getId();
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

  private MvcResult getInvestorProfits(Long investorId, String from, String to) throws Exception {
    MockHttpServletRequestBuilder request =
        get("/investors/" + investorId + "/profits")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON);
    if (from != null) {
      request.param("from", from);
    }
    if (to != null) {
      request.param("to", to);
    }
    return mockMvc.perform(request).andReturn();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> salesOf(Map<String, Object> body) {
    return (List<Map<String, Object>>) body.get("sales");
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
    // - realizedProfit = 750000 - 360000 - 0 - 0 = 390000 (공모주수수료 1500 미차감)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
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
    assertThat(((Number) body.get("saleFee")).longValue()).isEqualTo(1500L);
    assertThat(((Number) body.get("saleTax")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("saleOtherCost")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(388500L);
    assertThat(((Number) body.get("afterTaxProfit")).longValue()).isEqualTo(388500L);
    assertThat(((Number) body.get("realizedProfit")).longValue())
        .as("실현손익 = 매도금액 - 취득금액 - 세금 - 기타 (공모주수수료 미차감)")
        .isEqualTo(390000L);
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
    Long saleId =
        createSale(
            allocationId, 30L, 750000L, 1500L, 5000L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: afterTaxProfit = preTaxProfit - Sale.tax (AC-002)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("saleTax")).longValue()).isEqualTo(5000L);
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(388500L);
    assertThat(((Number) body.get("afterTaxProfit")).longValue())
        .as("세후순수익 = 세전순수익 - 세금 (AC-002)")
        .isEqualTo(383500L);
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
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);

    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("수익증권" + ThreadLocalRandom.current().nextLong())
                .status(RecordStatus.ACTIVE)
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
                .status(RecordStatus.ACTIVE)
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
                .status(RecordStatus.ACTIVE)
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
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    cancelled.setUpdatedAt(now);
    saleRepository.saveAndFlush(cancelled);

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: 404 Not Found (CANCELLED Sale → 404, AC-008)
    assertNotFoundError(result);
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
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // DB 상태 스냅샷 (응답 전)
    long saleCountBefore = saleRepository.count();
    long allocationCountBefore = allocationRepository.count();
    long subscriptionCountBefore = subscriptionRepository.count();
    Sale saleBefore = saleRepository.findById(saleId).orElseThrow();
    LocalDateTime saleUpdatedAtBefore = saleBefore.getUpdatedAt();

    // When: GET /sales/{saleId}/profit 요청이 전송됨
    MvcResult result = getSaleProfit(saleId);

    // Then: 응답 후 데이터베이스에 추가·변경되는 레코드가 없음 (AC-013)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(saleRepository.count()).isEqualTo(saleCountBefore);
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
    assertThat(((Number) body.get("preTaxProfit")).longValue()).isEqualTo(390000L);
    // afterTaxProfit = 378000 - 0(null→0) = 378000
    assertThat(((Number) body.get("afterTaxProfit")).longValue()).isEqualTo(390000L);
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

    useIpoListedOn(allocationId, java.time.LocalDate.of(2026, 10, 15));
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
    assertThat(((Number) sales.get(0).get("preTaxProfit")).longValue()).isEqualTo(388500L);
    assertThat(((Number) sales.get(0).get("afterTaxProfit")).longValue()).isEqualTo(388500L);
    assertThat(((Number) sales.get(0).get("realizedProfit")).longValue())
        .as("Sale 1 실현손익 = 750000 - 360000 - 0 - 0 (공모주수수료 1500 미차감)")
        .isEqualTo(390000L);
    assertThat(sales.get(0).get("listingDate")).isEqualTo("2026-10-15");
    assertThat(sales.get(0).get("ipoName")).isEqualTo("테스트공모주");

    // 두 번째 Sale(2026-10-15): 500000 - 240000 - 0 - 0 = 260000
    assertThat(((Number) sales.get(1).get("realizedProfit")).longValue())
        .as("Sale 2 실현손익 = 500000 - 240000 - 0 - 0")
        .isEqualTo(260000L);

    // 합계 3종 필드 검증
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(1250000L);
    assertThat(((Number) body.get("totalPreTaxProfit")).longValue()).isEqualTo(648500L);
    assertThat(((Number) body.get("totalAfterTaxProfit")).longValue()).isEqualTo(648500L);
    assertThat(((Number) body.get("totalRealizedProfit")).longValue())
        .as("총 실현손익 = 각 매도의 실현손익 합계 (390000 + 260000)")
        .isEqualTo(650000L);
  }

  @Test
  void getInvestorProfitsWithNullSaleAmountTreatsAmountAsZeroInTotals() throws Exception {
    // Given: 매도 금액(amount)은 선택 입력이라 null인 매도가 있을 수 있다 (D-SL-007)
    // - Sale 1: amount=null → 합계에서 0으로 간주, preTax = 0 - 360000 - 12000 = -372000
    // - Sale 2: amount=500000 → preTax = 500000 - 240000 - 8000 = 252000
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createSale(allocationId, 30L, null, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 14, 14, 30));
    createSale(allocationId, 20L, 500000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When
    MvcResult result = getInvestorProfits(investorId);

    // Then: 500이 아니라 200, 합계는 null을 0으로 합산
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(500000L);
    assertThat(((Number) body.get("totalPreTaxProfit")).longValue()).isEqualTo(-100000L);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sales = (List<Map<String, Object>>) body.get("sales");
    assertThat(sales.get(0).get("saleAmount")).isNull();
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
    assertThat(((Number) body.get("totalRealizedProfit")).longValue())
        .as("매도가 없으면 총 실현손익은 0이어야 합니다 (AC-012)")
        .isEqualTo(0L);
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

    Long activeSaleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));
    Long cancelledSaleId =
        createSale(allocationId, 20L, 500000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 14, 14, 30));

    // CANCELLED 상태로 변경
    Sale cancelled = saleRepository.findById(cancelledSaleId).orElseThrow();
    LocalDateTime now = LocalDateTime.now();
    cancelled.setStatus(RecordStatus.CANCELLED);
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
    Long saleId =
        createSale(
            allocationId, 30L, 750000L, 1500L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // DB 상태 스냅샷 (응답 전)
    long saleCountBefore = saleRepository.count();
    long allocationCountBefore = allocationRepository.count();
    long subscriptionCountBefore = subscriptionRepository.count();
    Sale saleBefore = saleRepository.findById(saleId).orElseThrow();

    // When: GET /investors/{investorId}/profits
    MvcResult result = getInvestorProfits(investorId);

    // Then: 응답 후 데이터베이스에 추가·변경되는 레코드가 없음 (AC-013)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(saleRepository.count()).isEqualTo(saleCountBefore);
    assertThat(allocationRepository.count()).isEqualTo(allocationCountBefore);
    assertThat(subscriptionRepository.count()).isEqualTo(subscriptionCountBefore);
    Sale saleAfter = saleRepository.findById(saleId).orElseThrow();
    assertThat(saleAfter.getUpdatedAt()).isEqualTo(saleBefore.getUpdatedAt());
    assertThat(saleAfter.getAmount()).isEqualTo(saleBefore.getAmount());
    assertThat(saleAfter.getQuantity()).isEqualTo(saleBefore.getQuantity());
  }

  // ==================== 기간 필터: from·to 선택 쿼리 파라미터 ====================

  @Test
  void getInvestorProfitsFiltersSalesByPeriodAndTotalsOnlyFilteredSales() throws Exception {
    // Given: 2026-10-10 / 10-15 / 10-20 매도 3건이 한 투자자에 존재한다 (AC-2)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    useIpoListedOn(allocationId, null);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createSale(allocationId, 10L, 100000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 10, 9, 0));
    Long middleSaleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));
    createSale(allocationId, 20L, 500000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 20, 16, 0));

    // When: from=2026-10-12, to=2026-10-18
    MvcResult result = getInvestorProfits(investorId, "2026-10-12", "2026-10-18");

    // Then: 10/15 매도 1건만 반환되고 합계도 그 매도 기준이다 (AC-2)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    List<Map<String, Object>> sales = salesOf(body);
    assertThat(sales).as("기간에 포함되는 매도만 반환해야 합니다 (AC-2)").hasSize(1);
    assertThat(((Number) sales.get(0).get("saleId")).longValue()).isEqualTo(middleSaleId);
    assertThat((String) sales.get(0).get("saleDate")).isEqualTo("2026-10-15T14:30:00");
    // 합계는 필터링된 매도 1건 기준: 취득가 12000 × 30 = 360000
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(750000L);
    assertThat(((Number) body.get("totalPreTaxProfit")).longValue()).isEqualTo(390000L);
    assertThat(((Number) body.get("totalAfterTaxProfit")).longValue()).isEqualTo(390000L);
    assertThat(((Number) body.get("totalRealizedProfit")).longValue()).isEqualTo(390000L);
  }

  @Test
  void getInvestorProfitsWithSameFromAndToIncludesBoundaryDaySale() throws Exception {
    // Given: 2026-10-15 14:30 매도가 존재한다 (AC-3)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    useIpoListedOn(allocationId, null);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    Long saleId =
        createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: from=to=2026-10-15 (양 끝 포함)
    MvcResult result = getInvestorProfits(investorId, "2026-10-15", "2026-10-15");

    // Then: 당일 매도가 포함된다 (AC-3)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    List<Map<String, Object>> sales = salesOf(body);
    assertThat(sales).hasSize(1);
    assertThat(((Number) sales.get(0).get("saleId")).longValue())
        .as("시작일과 종료일이 같으면 당일 매도를 포함해야 합니다 (AC-3)")
        .isEqualTo(saleId);
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(750000L);
  }

  @Test
  void getInvestorProfitsWithOnlyFromOrOnlyToFiltersOneSideOnly() throws Exception {
    // Given: 2026-10-10 / 10-15 / 10-20 매도 3건이 존재한다 (AC-4)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    useIpoListedOn(allocationId, null);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createSale(allocationId, 10L, 100000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 10, 9, 0));
    createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));
    createSale(allocationId, 20L, 500000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 20, 16, 0));

    // When: from만 준다
    MvcResult fromOnly = getInvestorProfits(investorId, "2026-10-15", null);

    // Then: 그 날 이후 전부가 반환된다 (AC-4)
    assertThat(fromOnly.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> fromOnlySales = salesOf(readBody(fromOnly));
    assertThat(fromOnlySales).hasSize(2);
    assertThat((String) fromOnlySales.get(0).get("saleDate")).isEqualTo("2026-10-15T14:30:00");
    assertThat((String) fromOnlySales.get(1).get("saleDate")).isEqualTo("2026-10-20T16:00:00");

    // When: to만 준다
    MvcResult toOnly = getInvestorProfits(investorId, null, "2026-10-15");

    // Then: 그 날까지 전부가 반환된다 (AC-4)
    assertThat(toOnly.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> toOnlySales = salesOf(readBody(toOnly));
    assertThat(toOnlySales).hasSize(2);
    assertThat((String) toOnlySales.get(0).get("saleDate")).isEqualTo("2026-10-10T09:00:00");
    assertThat((String) toOnlySales.get(1).get("saleDate")).isEqualTo("2026-10-15T14:30:00");
  }

  @Test
  void getInvestorProfitsWithNoSalesInsidePeriodReturnsEmptyListAndZeroTotals() throws Exception {
    // Given: 매도는 존재하지만 조회 기간 밖에 있다 (AC-5)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    useIpoListedOn(allocationId, null);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createSale(allocationId, 30L, 750000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 15, 14, 30));

    // When: 매도가 하나도 없는 기간을 조회한다
    MvcResult result = getInvestorProfits(investorId, "2026-11-01", "2026-11-30");

    // Then: 200 + sales 빈 배열 + 합계 전부 0 (AC-5)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(((Number) body.get("investorId")).longValue()).isEqualTo(investorId);
    assertThat(salesOf(body)).isEmpty();
    assertThat(((Number) body.get("totalSaleAmount")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("totalPreTaxProfit")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("totalAfterTaxProfit")).longValue()).isEqualTo(0L);
    assertThat(((Number) body.get("totalRealizedProfit")).longValue()).isEqualTo(0L);
  }

  @Test
  void getInvestorProfitsFiltersByIpoListingDateEvenWhenSoldLater() throws Exception {
    // Given: 10/15 상장 공모주를 10/20과 11/05에 나눠 팔았다 (사용자 결정 2026-10-13: 상장일 기준)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    useIpoListedOn(allocationId, java.time.LocalDate.of(2026, 10, 15));
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();
    createSale(allocationId, 10L, 150000L, 0L, 0L, 0L, LocalDateTime.of(2026, 10, 20, 9, 0));
    createSale(allocationId, 20L, 300000L, 0L, 0L, 0L, LocalDateTime.of(2026, 11, 5, 9, 0));

    // When/Then: 10월을 조회하면 11월 매도까지 둘 다 나오고, 11월을 조회하면 없다
    List<Map<String, Object>> october =
        salesOf(readBody(getInvestorProfits(investorId, "2026-10-01", "2026-10-31")));
    assertThat(october).hasSize(2);
    assertThat(salesOf(readBody(getInvestorProfits(investorId, "2026-11-01", "2026-11-30"))))
        .isEmpty();
  }

  @Test
  void getInvestorProfitsWithFromAfterToReturns400() throws Exception {
    // Given: 시작일이 종료일보다 늦은 기간이 주어진다 (AC-6)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();

    // When: from=2026-10-20, to=2026-10-10
    MvcResult result = getInvestorProfits(investorId, "2026-10-20", "2026-10-10");

    // Then: 400 Bad Request (AC-6)
    assertThat(result.getResponse().getStatus())
        .as("from이 to보다 늦으면 400이어야 합니다 (AC-6)")
        .isEqualTo(400);
  }

  @Test
  void getInvestorProfitsWithInvalidDateFormatReturns400() throws Exception {
    // Given: 날짜 형식이 잘못된 from이 주어진다 (AC-6)
    signupAndLogin();
    Long allocationId = setupFoundation(12000L);
    Long subscriptionId =
        allocationRepository.findById(allocationId).orElseThrow().getSubscriptionId();
    Long investorId = subscriptionRepository.findById(subscriptionId).orElseThrow().getInvestorId();

    // When: from=abc
    MvcResult result = getInvestorProfits(investorId, "abc", null);

    // Then: 400 Bad Request (AC-6)
    assertThat(result.getResponse().getStatus()).as("날짜 형식이 잘못되면 400이어야 합니다 (AC-6)").isEqualTo(400);
  }

  @Test
  void getInvestorProfitsWithPeriodForOtherAccountInvestorReturns403() throws Exception {
    // Given: 다른 계정이 소유한 투자자가 존재한다 (AC-7)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();

    // When: 기간과 함께 타 계정 투자자를 조회한다
    MvcResult result = getInvestorProfits(otherInvestorId, "2026-10-01", "2026-10-31");

    // Then: 403 Forbidden (AC-7)
    assertThat(result.getResponse().getStatus())
        .as("타 계정 투자자는 기간을 줘도 403이어야 합니다 (AC-7)")
        .isEqualTo(403);
  }

  @Test
  void getInvestorProfitsWithPeriodForNonExistentInvestorReturns404() throws Exception {
    // Given: 존재하지 않는 investorId에 기간을 함께 준다 (AC-7)
    signupAndLogin();

    // When
    MvcResult result = getInvestorProfits(99999999L, "2026-10-01", "2026-10-31");

    // Then: 404 Not Found (AC-7)
    assertNotFoundError(result);
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
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestors.add(investor);

    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("수익증권" + ThreadLocalRandom.current().nextLong())
                .status(RecordStatus.ACTIVE)
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
                .status(RecordStatus.ACTIVE)
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
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAllocations.add(allocation);

    return allocation.getId();
  }
}
