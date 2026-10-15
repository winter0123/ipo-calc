package com.ipocalc.brokerintegration.controller.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** 증권사 매도 확정의 중복 저장 방지(순차·동시)와 최소 검증을 실제 DB로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class BrokerSyncConfirmIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Clock clock;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;
  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;
  @Autowired private IpoRepository ipoRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private AllocationRepository allocationRepository;
  @Autowired private SaleRepository saleRepository;

  private String email;
  private MockHttpSession session;
  private SecuritiesFirm firm;
  private Ipo ipo;
  private Long securitiesAccountId;
  private Long allocationId;

  @BeforeEach
  void setUp() throws Exception {
    email = "broker-confirm-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    session = new MockHttpSession();
    AuthTestSupport.signupAndLogin(mockMvc, objectMapper, session, email, "securePass123!", "확정");
    Long investorId =
        idOf(postJson("/investors", Map.of("displayName", "투자자", "relationship", "SELF")));
    LocalDateTime now = LocalDateTime.now();
    firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("증권사" + ThreadLocalRandom.current().nextLong())
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    securitiesAccountId =
        idOf(
            postJson(
                "/securities-accounts",
                Map.of(
                    "investorId",
                    investorId,
                    "securitiesFirmId",
                    firm.getId(),
                    "accountNumber",
                    "123-456-789012",
                    "accountName",
                    "계좌")));
    ipo =
        ipoRepository.save(
            Ipo.builder()
                .companyName("기업")
                .ipoName("공모주")
                .stockCode("9" + ThreadLocalRandom.current().nextInt(100000, 999999))
                .subscriptionStartDate(LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(LocalDate.of(2026, 10, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    Long subscriptionId =
        idOf(
            postJson(
                "/subscriptions",
                Map.of(
                    "investorId",
                    investorId,
                    "securitiesAccountId",
                    securitiesAccountId,
                    "ipoId",
                    ipo.getId(),
                    "quantity",
                    200L,
                    "depositAmount",
                    1000000,
                    "subscribedAt",
                    "2026-10-01T09:00:00")));
    allocationId =
        idOf(
            postJson(
                "/allocations",
                Map.of(
                    "subscriptionId",
                    subscriptionId,
                    "allocatedQuantity",
                    100L,
                    "allocatedAt",
                    "2026-10-05T18:00:00",
                    "allocationMethod",
                    "균등배정",
                    "allocationPrice",
                    10000)));
  }

  @AfterEach
  void tearDown() {
    saleRepository.deleteAll(
        saleRepository.findAll().stream()
            .filter(s -> allocationId.equals(s.getAllocationId()))
            .toList());
    var investors =
        investorRepository.findAll().stream()
            .filter(i -> accountRepository.findByEmail(email).getId().equals(i.getAccountId()))
            .toList();
    for (var investor : investors) {
      var subs =
          subscriptionRepository.findAll().stream()
              .filter(s -> investor.getId().equals(s.getInvestorId()))
              .toList();
      var subIds = subs.stream().map(s -> s.getId()).toList();
      allocationRepository.deleteAll(
          allocationRepository.findAll().stream()
              .filter(a -> subIds.contains(a.getSubscriptionId()))
              .toList());
      subscriptionRepository.deleteAll(subs);
      securitiesAccountRepository.deleteAll(
          securitiesAccountRepository.findByInvestorId(investor.getId()));
    }
    investorRepository.deleteAll(investors);
    accountRepository.delete(accountRepository.findByEmail(email));
    securitiesFirmRepository.delete(firm);
    ipoRepository.delete(ipo);
  }

  private MvcResult postJson(String path, Object body) throws Exception {
    return mockMvc
        .perform(
            post(path)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andReturn();
  }

  private Long idOf(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return ((Number)
            objectMapper
                .readValue(
                    result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class)
                .get("id"))
        .longValue();
  }

  private Map<String, Object> confirmBody(LocalDateTime saleDate, Long amount) {
    Map<String, Object> sale = new java.util.LinkedHashMap<>();
    sale.put("allocationId", allocationId);
    sale.put("quantity", 10L);
    sale.put("price", 12000L);
    if (amount != null) {
      sale.put("amount", amount);
    }
    sale.put("saleDate", saleDate.toString());
    sale.put("fee", 0L);
    sale.put("tax", 0L);
    return Map.of("sales", List.of(sale));
  }

  private int confirm(Map<String, Object> body) throws Exception {
    return postJson("/securities-accounts/" + securitiesAccountId + "/broker-sync/confirm", body)
        .getResponse()
        .getStatus();
  }

  private long activeSales() {
    return saleRepository.findAll().stream()
        .filter(s -> allocationId.equals(s.getAllocationId()))
        .filter(s -> s.getStatus() == RecordStatus.ACTIVE)
        .count();
  }

  private LocalDateTime yesterday() {
    return LocalDate.now(clock).minusDays(1).atStartOfDay();
  }

  @Test
  void confirmingTheSameBodyTwiceRejectsTheSecondWith409() throws Exception {
    Map<String, Object> body = confirmBody(yesterday(), 120_000L);

    assertThat(confirm(body)).isEqualTo(201);
    assertThat(confirm(body)).isEqualTo(409);
    assertThat(activeSales()).isEqualTo(1);
  }

  @Test
  void concurrentConfirmsOfTheSameBodySaveExactlyOneSale() throws Exception {
    Map<String, Object> body = confirmBody(yesterday(), 120_000L);
    int threads = 4;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          pool.submit(
              () -> {
                start.await();
                return confirm(body);
              }));
    }
    start.countDown();
    int created = 0;
    for (Future<Integer> f : futures) {
      if (f.get() == 201) {
        created++;
      }
    }
    pool.shutdown();

    assertThat(created).isEqualTo(1);
    assertThat(activeSales()).isEqualTo(1);
  }

  @Test
  void futureSaleDateIsRejectedWith400() throws Exception {
    LocalDateTime tomorrow = LocalDate.now(clock).plusDays(1).atStartOfDay();

    assertThat(confirm(confirmBody(tomorrow, 120_000L))).isEqualTo(400);
    assertThat(activeSales()).isZero();
  }

  @Test
  void missingAmountIsFilledWithPriceTimesQuantity() throws Exception {
    assertThat(confirm(confirmBody(yesterday(), null))).isEqualTo(201);

    Sale saved =
        saleRepository.findAll().stream()
            .filter(s -> allocationId.equals(s.getAllocationId()))
            .findFirst()
            .orElseThrow();
    assertThat(saved.getAmount()).isEqualTo(120_000L);
  }

  @Test
  void saleDateBeforeAllocationDateIsAllowed() throws Exception {
    // 배정일(2026-10-05)보다 앞선 매도일도 막지 않는다
    assertThat(confirm(confirmBody(LocalDateTime.of(2026, 10, 1, 0, 0), 120_000L))).isEqualTo(201);
  }
}
