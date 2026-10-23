package com.ipocalc.ipocatalog.controller.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** 공용 카탈로그(IPO·증권사) 쓰기는 ADMIN 전용이고 읽기는 로그인 사용자 전체에 열려 있다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class CatalogAdminAccessTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  private String email;
  private final String password = "securePass123!";
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    email = "catalog-admin-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    Account account = accountRepository.findByEmail(email);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  @Test
  void nonAdminCannotWriteCatalogAndGets403() throws Exception {
    signupAndLogin();

    for (AbstractMockHttpServletRequestBuilder<?> request : writeRequests()) {
      var response = mockMvc.perform(request.session(session)).andReturn().getResponse();
      assertThat(response.getStatus()).as(request.toString()).isEqualTo(403);
      assertThat(response.getContentAsString()).contains("\"error\":\"FORBIDDEN\"");
    }
  }

  @Test
  void unauthenticatedWriteStillReturns401() throws Exception {
    var response =
        mockMvc
            .perform(post("/ipos").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void signupCannotGrantAdminEvenIfBodyContainsRole() throws Exception {
    mockMvc.perform(
        post("/accounts")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of(
                        "email", email,
                        "password", password,
                        "name", "테스터",
                        "nickname", "테스터닉",
                        "role", "ADMIN"))));

    assertThat(accountRepository.findByEmail(email).getRole()).isEqualTo(AccountRole.USER);
  }

  @Test
  void adminCanWriteCatalog() throws Exception {
    signup();
    Account account = accountRepository.findByEmail(email);
    account.setRole(AccountRole.ADMIN);
    accountRepository.save(account);
    login();

    var response =
        mockMvc.perform(delete("/ipos/999999999").session(session)).andReturn().getResponse();

    assertThat(response.getStatus()).as("ADMIN은 권한 검사를 통과해 404를 받는다").isEqualTo(404);
  }

  @Test
  void regularUserCanStillRead() throws Exception {
    signupAndLogin();

    assertThat(mockMvc.perform(get("/ipos").session(session)).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(
            mockMvc
                .perform(get("/securities-firms").session(session))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  private AbstractMockHttpServletRequestBuilder<?>[] writeRequests() {
    String json = "{}";
    return new AbstractMockHttpServletRequestBuilder<?>[] {
      post("/ipos").contentType(MediaType.APPLICATION_JSON).content(json),
      patch("/ipos/1").contentType(MediaType.APPLICATION_JSON).content(json),
      delete("/ipos/1"),
      post("/securities-firms").contentType(MediaType.APPLICATION_JSON).content(json),
      patch("/securities-firms/1").contentType(MediaType.APPLICATION_JSON).content(json),
      delete("/securities-firms/1")
    };
  }

  private void signupAndLogin() throws Exception {
    signup();
    login();
  }

  private void signup() throws Exception {
    mockMvc
        .perform(
            post("/accounts")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", email,
                            "password", password,
                            "name", "테스터",
                            "nickname", "테스터닉"))))
        .andReturn();
  }

  private void login() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andReturn();
  }
}
