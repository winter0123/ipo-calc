package com.ipocalc.investormanagement.controller.ownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
 * 청약·배정·매도·비용·수익 API의 계정 간 소유권 격리 검증. 계정 A 소유 데이터를 계정 B가 조회·수정·취소·참조하면 403이고, 목록에는 A의 데이터가 나오지 않으며,
 * A 본인은 정상 접근한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class OwnershipIsolationTest {

  private static final String PASSWORD = "securePass123!";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private AllocationRepository allocationRepository;
  @Autowired private SaleRepository saleRepository;

  private final MockHttpSession ownerSession = new MockHttpSession();
  private final MockHttpSession intruderSession = new MockHttpSession();
  private String ownerEmail;
  private String intruderEmail;
  private Investor investor;
  private Subscription subscription;
  private Allocation allocation;
  private Sale sale;

  @BeforeEach
  void setUp() throws Exception {
    long nonce = ThreadLocalRandom.current().nextLong();
    ownerEmail = "isolation-owner-" + nonce + "@example.com";
    intruderEmail = "isolation-intruder-" + nonce + "@example.com";
    signupAndLogin(ownerEmail, ownerSession);
    signupAndLogin(intruderEmail, intruderSession);

    LocalDateTime now = LocalDateTime.now();
    investor =
        investorRepository.save(
            Investor.builder()
                .accountId(accountRepository.findByEmail(ownerEmail).getId())
                .displayName("소유자투자자")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    subscription =
        subscriptionRepository.save(
            Subscription.builder()
                .investorId(investor.getId())
                .securitiesAccountId(1L)
                .ipoId(1L)
                .quantity(100L)
                .depositAmount(1000000L)
                .subscribedAt(now)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    allocation =
        allocationRepository.save(
            Allocation.builder()
                .subscriptionId(subscription.getId())
                .allocatedQuantity(100L)
                .allocatedAt(now)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    sale =
        saleRepository.save(
            Sale.builder()
                .allocationId(allocation.getId())
                .quantity(10L)
                .price(12000L)
                .amount(120000L)
                .saleDate(now)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
  }

  @AfterEach
  void tearDown() {
    saleRepository.delete(sale);
    allocationRepository.delete(allocation);
    subscriptionRepository.delete(subscription);
    investorRepository.delete(investor);
    Account owner = accountRepository.findByEmail(ownerEmail);
    Account intruder = accountRepository.findByEmail(intruderEmail);
    investorRepository.deleteAll(investorRepository.findByAccountId(owner.getId()));
    investorRepository.deleteAll(investorRepository.findByAccountId(intruder.getId()));
    accountRepository.delete(owner);
    accountRepository.delete(intruder);
  }

  @Test
  void otherAccountCannotReadUpdateOrCancelRecords() throws Exception {
    String[] paths = {
      "/subscriptions/" + subscription.getId(),
      "/allocations/" + allocation.getId(),
      "/sales/" + sale.getId()
    };
    for (String path : paths) {
      assertStatus(get(path), intruderSession, 403);
      assertStatus(json(patch(path), "{}"), intruderSession, 403);
      assertStatus(delete(path), intruderSession, 403);
    }
    assertStatus(get("/sales/" + sale.getId() + "/profit"), intruderSession, 403);
    assertStatus(get("/investors/" + investor.getId() + "/profits"), intruderSession, 403);
  }

  @Test
  void otherAccountCannotAttachToRecords() throws Exception {
    String now = "2026-10-01T09:00:00";
    assertStatus(
        json(post("/allocations"), allocationBody(subscription.getId(), now)),
        intruderSession,
        403);
    assertStatus(json(post("/sales"), saleBody(allocation.getId(), now)), intruderSession, 403);
  }

  @Test
  void listsOnlyContainOwnRecords() throws Exception {
    for (String path : List.of("/subscriptions", "/allocations", "/sales")) {
      assertThat(listIds(path, intruderSession)).as(path + " (타 계정)").isEmpty();
    }
    assertThat(listIds("/subscriptions", ownerSession)).contains(subscription.getId());
    assertThat(listIds("/allocations", ownerSession)).contains(allocation.getId());
    assertThat(listIds("/sales", ownerSession)).contains(sale.getId());
  }

  @Test
  void ownerCanAccessOwnRecords() throws Exception {
    assertStatus(get("/subscriptions/" + subscription.getId()), ownerSession, 200);
    assertStatus(get("/allocations/" + allocation.getId()), ownerSession, 200);
    assertStatus(get("/sales/" + sale.getId()), ownerSession, 200);
    assertStatus(get("/sales/" + sale.getId() + "/profit"), ownerSession, 200);
    assertStatus(get("/investors/" + investor.getId() + "/profits"), ownerSession, 200);
  }

  private String allocationBody(Long subscriptionId, String at) {
    return "{\"subscriptionId\":"
        + subscriptionId
        + ",\"allocatedQuantity\":1,\"allocatedAt\":\""
        + at
        + "\"}";
  }

  private String saleBody(Long allocationId, String at) {
    return "{\"allocationId\":" + allocationId + ",\"quantity\":1,\"saleDate\":\"" + at + "\"}";
  }

  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
    return builder.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private void assertStatus(
      MockHttpServletRequestBuilder builder, MockHttpSession session, int expected)
      throws Exception {
    MvcResult result = mockMvc.perform(builder.session(session)).andReturn();
    assertThat(result.getResponse().getStatus())
        .as(builder.toString() + " → " + body(result))
        .isEqualTo(expected);
  }

  private List<Long> listIds(String path, MockHttpSession session) throws Exception {
    MvcResult result = mockMvc.perform(get(path).session(session)).andReturn();
    assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
    List<Map<String, Object>> rows = objectMapper.readValue(body(result), List.class);
    return rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
  }

  private String body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private void signupAndLogin(String email, MockHttpSession session) throws Exception {
    String credentials =
        objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    mockMvc.perform(
        post("/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of(
                        "email", email, "password", PASSWORD, "name", "격리테스터", "nickname", "격리닉")))
            .session(session));
    mockMvc.perform(
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credentials)
            .session(session));
  }
}
