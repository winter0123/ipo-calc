package com.ipocalc.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.util.HashMap;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 수량·금액 상한과 문자열 길이 상한: 오버플로·DB 컬럼 초과(500) 대신 400을 돌려준다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.signup.mode=OPEN", "app.rate-limit.enabled=false"})
class InputLimitsTest {

  private static final String PASSWORD = "securePass123!";
  private static final String DATE = "2026-01-01T00:00:00";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;

  private String email;
  private MockHttpSession session;

  @BeforeEach
  void setUp() throws Exception {
    email = "limits-" + Math.abs(ThreadLocalRandom.current().nextLong()) + "@example.com";
    session = new MockHttpSession();
    AuthTestSupport.signupAndLogin(mockMvc, objectMapper, session, email, PASSWORD, "한도");
  }

  @AfterEach
  void tearDown() {
    var account = accountRepository.findByEmail(email);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  private int postStatus(String path, Map<String, Object> body) throws Exception {
    return mockMvc
        .perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .session(session))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put((String) kv[i], kv[i + 1]);
    }
    return map;
  }

  @Test
  void tooLongInvestorRelationshipIs400() throws Exception {
    assertThat(postStatus("/investors", map("displayName", "나", "relationship", "x".repeat(21))))
        .isEqualTo(400);
  }

  @Test
  void tooLongAccountNumberOrNameIs400() throws Exception {
    assertThat(
            postStatus(
                "/securities-accounts",
                map("investorId", 1, "securitiesFirmId", 1, "accountNumber", "1".repeat(51))))
        .isEqualTo(400);
    assertThat(
            postStatus(
                "/securities-accounts",
                map(
                    "investorId",
                    1,
                    "securitiesFirmId",
                    1,
                    "accountNumber",
                    "123",
                    "accountName",
                    "n".repeat(256))))
        .isEqualTo(400);
  }

  @Test
  void hugeQuantityAndAmountsAreRejected() throws Exception {
    assertThat(
            postStatus(
                "/sales", map("allocationId", 1, "quantity", Long.MAX_VALUE, "saleDate", DATE)))
        .isEqualTo(400);
    assertThat(
            postStatus(
                "/sales",
                map("allocationId", 1, "quantity", 1, "price", Long.MAX_VALUE, "saleDate", DATE)))
        .isEqualTo(400);
    Map<String, Object> subscription =
        map(
            "investorId", 1,
            "securitiesAccountId", 1,
            "ipoId", 1,
            "quantity", Long.MAX_VALUE,
            "depositAmount", 0,
            "subscribedAt", DATE);
    assertThat(postStatus("/subscriptions", subscription)).isEqualTo(400);
    subscription.put("quantity", 1);
    subscription.put("memo", "m".repeat(1001));
    assertThat(postStatus("/subscriptions", subscription)).isEqualTo(400);
  }

  @Test
  void tooLongAllocationNoteIs400() throws Exception {
    assertThat(
            postStatus(
                "/allocations",
                map(
                    "subscriptionId",
                    1,
                    "allocatedQuantity",
                    1,
                    "allocatedAt",
                    DATE,
                    "note",
                    "n".repeat(256))))
        .isEqualTo(400);
  }

  @Test
  void hugePageNumberIs400() throws Exception {
    int status =
        mockMvc
            .perform(get("/investment-history").param("page", "2000000000").session(session))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(status).isEqualTo(400);
  }
}
