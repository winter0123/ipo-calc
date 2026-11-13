package com.ipocalc.investmentqueryapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * InvestmentHistoryController 실제 서비스·리포지토리·DB 경로 통합 테스트.
 *
 * <p>기존 {@link InvestmentHistoryControllerTest}는 {@code @MockitoBean}으로 {@link
 * com.ipocalc.investmentqueryapi.service.investmenthistory.InvestmentHistoryQueryService}를 모킹하여
 * HTTP 레이어만 검증했으나, 이 테스트는 실 {@code QueryService} + 실 리포지토리 + 실 DB(슬롯)를 사용하여 권한
 * 검증(verifyIpoIdOwnership 제거 후)과 데이터 스코프 (currentAccountId 강제)를 엔드투엔드로 검증한다.
 *
 * <p>7가지 시나리오 필수 검증: 1. 신규 미청약 IPO → 200 빈 목록 2. 타 계정 청약만 있는 IPO → 200 빈 목록 (본인 데이터만 스코프) 3. 본인과 타인
 * 청약 동시 존재 시 → 본인 것만 조회 4. 타인 investorId/계좌/단건(subscriptionId) → 403 5. null 계좌 허용 (accountNumber
 * null인 본인 계좌) 6. 비존재 IPO → 200 빈 목록 7. (보너스) 투자자/계좌 필터로 본인 데이터만 조회
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class InvestmentHistoryIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;
  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;
  @Autowired private IpoRepository ipoRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private static final String PASSWORD = "securePass123!";
  private MockHttpSession userSession;
  private MockHttpSession otherSession;
  private Account userAccount;
  private Account otherAccount;
  private Investor userInvestor;
  private Investor otherInvestor;
  private SecuritiesAccount userSecuritiesAccount;
  private SecuritiesAccount userSecuritiesAccountNullNumber;
  private SecuritiesAccount otherSecuritiesAccount;
  private SecuritiesFirm securitiesFirm;
  private Ipo testIpo;
  private Ipo otherIpo;

  @BeforeEach
  void setUp() throws Exception {
    // 사용자 계정 2개 생성 및 로그인
    userAccount =
        createAccount("user-" + System.currentTimeMillis() + "@example.com", AccountRole.USER);
    otherAccount =
        createAccount("other-" + System.currentTimeMillis() + "@example.com", AccountRole.USER);

    userSession = new MockHttpSession();
    otherSession = new MockHttpSession();
    AuthTestSupport.login(mockMvc, objectMapper, userSession, userAccount.getEmail(), PASSWORD);
    AuthTestSupport.login(mockMvc, objectMapper, otherSession, otherAccount.getEmail(), PASSWORD);

    // 투자자 생성
    userInvestor = createInvestor(userAccount.getId(), "본인");
    otherInvestor = createInvestor(otherAccount.getId(), "타인");

    // 증권사 생성
    securitiesFirm = createSecuritiesFirm("테스트증권");

    // 본인 증권계좌 2개 (하나는 계좌번호 null)
    userSecuritiesAccount = createSecuritiesAccount(userInvestor.getId(), "123-456-789", "주력계좌");
    userSecuritiesAccountNullNumber =
        createSecuritiesAccount(userInvestor.getId(), null, "계좌번호없는계좌");

    // 타인 증권계좌
    otherSecuritiesAccount = createSecuritiesAccount(otherInvestor.getId(), "987-654-321", "타인계좌");

    // 공모주 생성
    testIpo = createIpo("테스트공모주", "TEST001", 10000L);
    otherIpo = createIpo("다른공모주", "OTHER002", 20000L);
  }

  private Account createAccount(String email, AccountRole role) {
    Account account =
        Account.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .name("테스트유저")
            .role(role)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return accountRepository.save(account);
  }

  private Investor createInvestor(Long accountId, String displayName) {
    Investor investor =
        Investor.builder()
            .accountId(accountId)
            .displayName(displayName)
            .relationship("SELF")
            .status(com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return investorRepository.save(investor);
  }

  private SecuritiesFirm createSecuritiesFirm(String name) {
    SecuritiesFirm firm =
        SecuritiesFirm.builder()
            .name(name)
            .status(com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return securitiesFirmRepository.save(firm);
  }

  private SecuritiesAccount createSecuritiesAccount(
      Long investorId, String accountNumber, String accountName) {
    SecuritiesAccount account =
        SecuritiesAccount.builder()
            .investorId(investorId)
            .securitiesFirmId(securitiesFirm.getId())
            .accountNumber(accountNumber)
            .accountName(accountName)
            .status(com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return securitiesAccountRepository.save(account);
  }

  private Ipo createIpo(String ipoName, String stockCode, Long fixedPrice) {
    LocalDate today = LocalDate.now();
    Ipo ipo =
        Ipo.builder()
            .companyName(ipoName)
            .ipoName(ipoName)
            .stockCode(stockCode)
            .fixedPrice(fixedPrice)
            .subscriptionStartDate(today.minusDays(10))
            .subscriptionEndDate(today.minusDays(5))
            .listingDate(today.plusDays(10))
            .allotmentDate(today.plusDays(2))
            .refundDate(today.plusDays(3))
            .status(com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return ipoRepository.save(ipo);
  }

  private Subscription createSubscription(
      Long investorId, Long securitiesAccountId, Long ipoId, Long quantity) {
    Subscription sub =
        Subscription.builder()
            .investorId(investorId)
            .securitiesAccountId(securitiesAccountId)
            .ipoId(ipoId)
            .quantity(quantity)
            .depositAmount(fixedPrice(ipoId) * quantity / 2)
            .subscribedAt(LocalDateTime.now())
            .status(com.ipocalc.accountauthentication.domain.model.RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    return subscriptionRepository.save(sub);
  }

  private Long fixedPrice(Long ipoId) {
    return ipoRepository.findById(ipoId).orElseThrow().getFixedPrice();
  }

  // ===========================================
  // 1. 신규 미청약 IPO → 200 빈 목록
  // ===========================================
  @Test
  void getHistory_신규미청약IPO_빈목록200() throws Exception {
    // Given: testIpo에 아무도 청약하지 않음
    // When: 본인 세션으로 testIpo 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("ipoId", testIpo.getId().toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 200 OK + 빈 content
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.isEmpty() : "신규 미청약 IPO는 빈 목록 200을 반환해야 함";
    assert ((Number) body.get("totalElements")).longValue() == 0L : "totalElements는 0";
  }

  // ===========================================
  // 2. 타 계정 청약만 있는 IPO → 200 빈 목록 (본인 데이터만 스코프)
  // ===========================================
  @Test
  void getHistory_타계정청약만존재_본인빈목록200() throws Exception {
    // Given: 타인이 testIpo에 청약함 (본인은 청약 없음)
    createSubscription(
        otherInvestor.getId(), otherSecuritiesAccount.getId(), testIpo.getId(), 100L);

    // When: 본인 세션으로 testIpo 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("ipoId", testIpo.getId().toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 200 OK + 빈 목록 (currentAccountId 스코프로 본인 것만 조회됨)
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.isEmpty() : "타 계정 청약만 있는 IPO는 본인에게 빈 목록 200 반환";
    assert ((Number) body.get("totalElements")).longValue() == 0L : "totalElements는 0";
  }

  // ===========================================
  // 3. 본인과 타인 청약 동시 존재 시 → 본인 것만 조회
  // ===========================================
  @Test
  void getHistory_본인타인청약동시존재_본인만조회() throws Exception {
    // Given: 본인과 타인 모두 testIpo에 청약함
    Subscription mySub =
        createSubscription(
            userInvestor.getId(), userSecuritiesAccount.getId(), testIpo.getId(), 100L);
    createSubscription(
        otherInvestor.getId(), otherSecuritiesAccount.getId(), testIpo.getId(), 200L);

    // When: 본인 세션으로 testIpo 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("ipoId", testIpo.getId().toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 200 OK + 본인 청약 1건만 조회
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.size() == 1 : "본인 청약 1건만 조회되어야 함";
    assert ((Number) body.get("totalElements")).longValue() == 1L : "totalElements는 1";

    Map<String, Object> item = (Map<String, Object>) content.get(0);
    assert ((Number) item.get("id")).longValue() == mySub.getId() : "조회된 청약 ID는 본인 것";
    assert ((Number) item.get("subscriptionQuantity")).longValue() == 100L : "청약 수량 일치";
  }

  // ===========================================
  // 4. 타인 investorId/계좌/단건 subscriptionId → 403
  // ===========================================
  @Test
  void getHistory_타인investorId지정_403() throws Exception {
    // Given: 타인의 investorId로 조회 시도
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("investorId", otherInvestor.getId().toString())
                    .session(userSession))
            .andExpect(status().isForbidden())
            .andReturn();

    // Then: 403 Forbidden
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    assert body.get("error").equals("FORBIDDEN") : "타인 investorId 지정 시 403";
  }

  @Test
  void getHistory_타인계좌지정_403() throws Exception {
    // Given: 타인의 securitiesAccountId로 조회 시도
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("securitiesAccountId", otherSecuritiesAccount.getId().toString())
                    .session(userSession))
            .andExpect(status().isForbidden())
            .andReturn();

    // Then: 403 Forbidden
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    assert body.get("error").equals("FORBIDDEN") : "타인 계좌 지정 시 403";
  }

  @Test
  void getHistory_타인subscriptionId단건조회_403() throws Exception {
    // Given: 타인의 청약 ID로 단건 상세 조회 시도
    Subscription otherSub =
        createSubscription(
            otherInvestor.getId(), otherSecuritiesAccount.getId(), testIpo.getId(), 100L);

    MvcResult result =
        mockMvc
            .perform(get("/investment-history/" + otherSub.getId()).session(userSession))
            .andExpect(status().isForbidden())
            .andReturn();

    // Then: 403 Forbidden
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    assert body.get("error").equals("FORBIDDEN") : "타인 subscriptionId 단건 조회 시 403";
  }

  // ===========================================
  // 5. null 계좌 허용 (accountNumber null인 본인 계좌)
  // ===========================================
  @Test
  void getHistory_계좌번호null본인계좌_허용_본인청약조회() throws Exception {
    // Given: accountNumber가 null인 본인 계좌로 청약 생성
    createSubscription(
        userInvestor.getId(), userSecuritiesAccountNullNumber.getId(), testIpo.getId(), 50L);

    // When: 본인 세션으로 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("ipoId", testIpo.getId().toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 200 OK + 본인 청약 1건 조회 (accountNumber null 여부와 무관)
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.size() == 1 : "accountNumber null인 본인 계좌도 허용되어야 함";
  }

  // ===========================================
  // 6. 비존재 IPO → 200 빈 목록
  // ===========================================
  @Test
  void getHistory_비존재IPO_빈목록200() throws Exception {
    // Given: 존재하지 않는 ipoId (999999)
    Long nonExistentIpoId = 999999L;

    // When: 본인 세션으로 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("ipoId", nonExistentIpoId.toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 200 OK + 빈 목록 (공개 IPO 존재 여부 조회 불필요)
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.isEmpty() : "비존재 IPO도 빈 목록 200 반환";
    assert ((Number) body.get("totalElements")).longValue() == 0L : "totalElements는 0";
  }

  // ===========================================
  // 7. 투자자/계좌 필터로 본인 데이터만 조회 (보너스)
  // ===========================================
  @Test
  void getHistory_본인investorId필터_본인만조회() throws Exception {
    // Given: 본인/타인 청약 동시 존재
    createSubscription(userInvestor.getId(), userSecuritiesAccount.getId(), testIpo.getId(), 100L);
    createSubscription(
        otherInvestor.getId(), otherSecuritiesAccount.getId(), testIpo.getId(), 200L);

    // When: 본인 investorId 필터로 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("investorId", userInvestor.getId().toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 본인 데이터만 조회
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.size() == 1 : "본인 investorId 필터 시 본인만 조회";
  }

  @Test
  void getHistory_본인계좌필터_본인만조회() throws Exception {
    // Given: 본인/타인 청약 동시 존재 (계좌 다름)
    createSubscription(userInvestor.getId(), userSecuritiesAccount.getId(), testIpo.getId(), 100L);
    createSubscription(
        otherInvestor.getId(), otherSecuritiesAccount.getId(), testIpo.getId(), 200L);

    // When: 본인 계좌 필터로 조회
    MvcResult result =
        mockMvc
            .perform(
                get("/investment-history")
                    .param("securitiesAccountId", userSecuritiesAccount.getId().toString())
                    .session(userSession))
            .andExpect(status().isOk())
            .andReturn();

    // Then: 본인 계좌 데이터만 조회
    String json = result.getResponse().getContentAsString();
    Map<String, Object> body = objectMapper.readValue(json, Map.class);
    List<?> content = (List<?>) body.get("content");
    assert content != null && content.size() == 1 : "본인 계좌 필터 시 본인만 조회";
  }
}
