package com.ipocalc.querycount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 조회 API의 SQL 실행 수가 데이터 건수와 무관하게 일정한지(N+1 없음) 확인한다.
 *
 * <p>청약·배정·매도를 5건 / 20건 만들어 각 API를 호출하고 Hibernate가 준비한 문장 수를 비교한다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class QueryCountTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private SecuritiesFirmRepository firmRepository;
  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;
  @Autowired private IpoRepository ipoRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private AllocationRepository allocationRepository;
  @Autowired private SaleRepository saleRepository;
  @Autowired private AccountNumberEncryptor encryptor;

  private final List<Runnable> cleanups = new ArrayList<>();

  @AfterEach
  void tearDown() {
    for (int i = cleanups.size() - 1; i >= 0; i--) {
      cleanups.get(i).run();
    }
    cleanups.clear();
  }

  @Test
  void sqlCountDoesNotGrowWithDataSize() throws Exception {
    Map<String, Long> small = measure(5);
    Map<String, Long> large = measure(20);
    System.out.println("QUERYCOUNT api | 5건 | 20건");
    small.forEach(
        (k, v) -> System.out.println("QUERYCOUNT " + k + " | " + v + " | " + large.get(k)));
    assertThat(large).as("데이터를 늘려도 SQL 수는 같아야 한다").isEqualTo(small);
  }

  // 청약 n·배정 n·매도 1.5n건을 만들고 각 API 호출의 SQL 수를 센다.
  private Map<String, Long> measure(int n) throws Exception {
    String email = "qc-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    MockHttpSession session = new MockHttpSession();
    AuthTestSupport.signupAndLogin(mockMvc, objectMapper, session, email, "securePass123!", "측정");
    Account account = accountRepository.findByEmail(email);
    cleanups.add(
        () -> {
          investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
          accountRepository.delete(account);
        });

    LocalDateTime now = LocalDateTime.now();
    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(account.getId())
                .displayName("측정투자자")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    cleanups.add(() -> investorRepository.delete(investor));

    Long firstSubscriptionId = null;
    for (int i = 0; i < n; i++) {
      SecuritiesFirm firm =
          firmRepository.save(
              SecuritiesFirm.builder()
                  .name("측정증권" + ThreadLocalRandom.current().nextLong())
                  .status(RecordStatus.ACTIVE)
                  .createdAt(now)
                  .updatedAt(now)
                  .build());
      cleanups.add(() -> firmRepository.delete(firm));
      SecuritiesAccount sa =
          securitiesAccountRepository.save(
              SecuritiesAccount.builder()
                  .investorId(investor.getId())
                  .securitiesFirmId(firm.getId())
                  .accountNumber(encryptor.encrypt("123-456-" + (100000 + i)))
                  .isDefault(i == 0)
                  .createdAt(now)
                  .updatedAt(now)
                  .build());
      cleanups.add(() -> securitiesAccountRepository.delete(sa));
      Ipo ipo =
          ipoRepository.save(
              Ipo.builder()
                  .companyName("측정기업")
                  .ipoName("측정공모주")
                  .stockCode("QC" + ThreadLocalRandom.current().nextLong(100000, 999999))
                  .lowerBandPrice(10000L)
                  .upperBandPrice(12000L)
                  .fixedPrice(12000L)
                  .subscriptionStartDate(LocalDate.of(2026, 10, 1))
                  .subscriptionEndDate(LocalDate.of(2026, 10, 2))
                  .refundDate(LocalDate.of(2026, 10, 6))
                  .listingDate(LocalDate.of(2026, 10, 15).plusDays(i % 40))
                  .offeringShareCount(1000000L)
                  .status(RecordStatus.ACTIVE)
                  .createdAt(now)
                  .updatedAt(now)
                  .build());
      cleanups.add(() -> ipoRepository.delete(ipo));
      Subscription sub =
          subscriptionRepository.save(
              Subscription.builder()
                  .investorId(investor.getId())
                  .securitiesAccountId(sa.getId())
                  .ipoId(ipo.getId())
                  .quantity(100L)
                  .depositAmount(1000000L)
                  .subscribedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                  .status(RecordStatus.ACTIVE)
                  .createdAt(now)
                  .updatedAt(now)
                  .build());
      cleanups.add(() -> subscriptionRepository.delete(sub));
      Allocation alloc =
          allocationRepository.save(
              Allocation.builder()
                  .subscriptionId(sub.getId())
                  .allocatedQuantity(100L)
                  .allocatedAt(now)
                  .allocationMethod("균등배정")
                  .allocationPrice(12000L)
                  .status(RecordStatus.ACTIVE)
                  .createdAt(now)
                  .updatedAt(now)
                  .build());
      cleanups.add(() -> allocationRepository.delete(alloc));
      int saleCount = i % 2 == 0 ? 2 : 1;
      for (int s = 0; s < saleCount; s++) {
        Sale sale =
            saleRepository.save(
                Sale.builder()
                    .allocationId(alloc.getId())
                    .quantity(10L)
                    .price(12000L)
                    .amount(150000L)
                    .saleDate(LocalDateTime.of(2026, 10, 16, 10, 0).plusDays(s))
                    .fee(100L)
                    .tax(0L)
                    .otherCost(0L)
                    .status(RecordStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        cleanups.add(() -> saleRepository.delete(sale));
      }
      if (firstSubscriptionId == null) {
        firstSubscriptionId = sub.getId();
      }
    }

    long id = investor.getId();
    Map<String, String> urls = new LinkedHashMap<>();
    urls.put("GET /investment-history (목록)", "/investment-history?investorId=" + id + "&size=100");
    urls.put("GET /investment-history/{id} (상세)", "/investment-history/" + firstSubscriptionId);
    urls.put("GET /dashboard/summary", "/dashboard/summary");
    urls.put("GET /dashboard/by-investor", "/dashboard/by-investor");
    urls.put("GET /dashboard/by-securities-firm", "/dashboard/by-securities-firm");
    urls.put("GET /dashboard/by-period", "/dashboard/by-period?unit=month");
    urls.put("GET /investors/{id}/profits", "/investors/" + id + "/profits");
    urls.put("GET /subscriptions", "/subscriptions?investorId=" + id);
    urls.put("GET /securities-accounts", "/securities-accounts?investorId=" + id);
    urls.put("GET /ipos", "/ipos");

    SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
    Map<String, Long> counts = new LinkedHashMap<>();
    // 첫 호출은 캐시(휴장일 등)를 데우므로 버리고, 두 번째 호출부터 센다.
    for (Map.Entry<String, String> e : urls.entrySet()) {
      mockMvc.perform(get(e.getValue()).session(session));
      sessionFactory.getStatistics().clear();
      int status =
          mockMvc.perform(get(e.getValue()).session(session)).andReturn().getResponse().getStatus();
      assertThat(status).as(e.getKey()).isEqualTo(200);
      counts.put(e.getKey(), sessionFactory.getStatistics().getPrepareStatementCount());
    }
    return counts;
  }
}
