package com.ipocalc.ipocatalog.controller.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoDetailRepository;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailService;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSnapshot;
import com.ipocalc.testsupport.AuthTestSupport;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** AC-001~AC-006: POST /ipos 등록 계약과 IPO 중복 검사 규칙. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class IpoControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private IpoRepository ipoRepository;

  @Autowired private IpoDetailRepository ipoDetailRepository;

  @Autowired private IpoDetailService ipoDetailService;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "ipo-controller-" + nonce + "@example.com";
    testPassword = "securePass123!";
    testName = "IPO테스터";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    ipoDetailRepository.deleteAll();
    ipoRepository.deleteAll();
    Account account = accountRepository.findByEmail(testEmail);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  @Test
  void registerIpoWithValidRequestReturns201AndSavedIpo() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();

    MvcResult result = registerIpo(request);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("id")).isNotNull();
    // companyName 미제공 시 ipoName으로 자동 저장
    assertThat(response.get("companyName")).isEqualTo("공모주A");
    assertThat(response.get("ipoName")).isEqualTo("공모주A");
    assertThat(response.get("stockCode")).isEqualTo("123456");
    assertThat(response.get("roundLabel")).isEqualTo("1차");
    assertThat(response.get("lowerBandPrice")).isEqualTo(10000);
    assertThat(response.get("upperBandPrice")).isEqualTo(12000);
    assertThat(response.get("fixedPrice")).isEqualTo(11000);
    assertThat(response.get("subscriptionStartDate")).isEqualTo("2026-10-01");
    assertThat(response.get("subscriptionEndDate")).isEqualTo("2026-10-02");
    assertThat(response.get("listingDate")).isEqualTo("2026-10-15");
    assertThat(response.get("offeringShareCount")).isEqualTo(1000000);
    assertThat(response.get("status")).isEqualTo("ACTIVE");
    assertThat(response.get("createdAt")).isNotNull();
    assertThat(response.get("updatedAt")).isNotNull();

    Ipo saved = ipoRepository.findById(((Number) response.get("id")).longValue()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(saved.getDeletedAt()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void registerIpoWithProvidedCompanyNameUsesProvidedValue() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("companyName", "별도기업");

    MvcResult result = registerIpo(request);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("companyName")).isEqualTo("별도기업");
    assertThat(response.get("ipoName")).isEqualTo("공모주A");
  }

  @Test
  void registerIpoWithMissingIpoNameReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.remove("ipoName");

    MvcResult result = registerIpo(request);

    assertValidationError(result);
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithMissingSubscriptionStartDateReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.remove("subscriptionStartDate");

    MvcResult result = registerIpo(request);

    assertValidationError(result);
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithMissingSubscriptionEndDateReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.remove("subscriptionEndDate");

    MvcResult result = registerIpo(request);

    assertValidationError(result);
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithEndDateBeforeStartDateReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("subscriptionStartDate", "2026-10-02");
    request.put("subscriptionEndDate", "2026-10-01");

    MvcResult result = registerIpo(request);

    assertValidationError(result);
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithLowerBandPriceGreaterThanUpperBandPriceReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("lowerBandPrice", 12000);
    request.put("upperBandPrice", 10000);

    MvcResult result = registerIpo(request);

    assertValidationError(result);
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithNegativePriceReturns400() throws Exception {
    signupAndLogin();

    Map<String, Object> negativeLower = validRequest();
    negativeLower.put("lowerBandPrice", -1);
    assertValidationError(registerIpo(negativeLower));

    Map<String, Object> negativeUpper = validRequest();
    negativeUpper.put("upperBandPrice", -1);
    assertValidationError(registerIpo(negativeUpper));

    Map<String, Object> negativeFixed = validRequest();
    negativeFixed.put("fixedPrice", -1);
    assertValidationError(registerIpo(negativeFixed));

    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithZeroOrNegativeOfferingShareCountReturns400() throws Exception {
    signupAndLogin();

    Map<String, Object> zeroCount = validRequest();
    zeroCount.put("offeringShareCount", 0);
    assertValidationError(registerIpo(zeroCount));

    Map<String, Object> negativeCount = validRequest();
    negativeCount.put("offeringShareCount", -1);
    assertValidationError(registerIpo(negativeCount));

    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithCompanyNameOrIpoNameOver255CharactersReturns400() throws Exception {
    signupAndLogin();

    Map<String, Object> longCompanyName = validRequest();
    longCompanyName.put("companyName", "가".repeat(256));
    assertValidationError(registerIpo(longCompanyName));

    Map<String, Object> longIpoName = validRequest();
    longIpoName.put("ipoName", "가".repeat(256));
    assertValidationError(registerIpo(longIpoName));

    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void registerIpoWithDuplicateActiveStockCodeReturns409() throws Exception {
    signupAndLogin();
    MvcResult first = registerIpo(validRequest());
    assertThat(first.getResponse().getStatus()).isEqualTo(201);

    Map<String, Object> duplicate = validRequest();
    duplicate.put("ipoName", "다른공모주");
    duplicate.put("subscriptionStartDate", "2026-11-01");
    duplicate.put("subscriptionEndDate", "2026-11-02");

    MvcResult result = registerIpo(duplicate);

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertError(result, "DUPLICATE_IPO");
    assertThat(ipoRepository.count()).isEqualTo(1);
  }

  @Test
  void registerIpoWithDuplicateActiveIpoNameAndStartDateReturns409() throws Exception {
    signupAndLogin();
    Map<String, Object> firstRequest = validRequest();
    firstRequest.remove("stockCode");
    assertThat(registerIpo(firstRequest).getResponse().getStatus()).isEqualTo(201);

    Map<String, Object> duplicate = validRequest();
    duplicate.remove("stockCode");
    duplicate.put("companyName", "기업B");

    MvcResult result = registerIpo(duplicate);

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertError(result, "DUPLICATE_IPO");
    assertThat(ipoRepository.count()).isEqualTo(1);
  }

  @Test
  void registerIpoWithSameNameButDifferentStockCodeOrRoundLabelReturns201() throws Exception {
    signupAndLogin();
    Map<String, Object> firstRequest = validRequest();
    firstRequest.put("ipoName", "공모주공통");
    firstRequest.put("stockCode", "123456");
    firstRequest.put("roundLabel", "1차");
    assertThat(registerIpo(firstRequest).getResponse().getStatus()).isEqualTo(201);

    Map<String, Object> differentStockCode = validRequest();
    differentStockCode.put("ipoName", "공모주공통");
    differentStockCode.put("stockCode", "654321");
    differentStockCode.put("roundLabel", "1차");
    MvcResult stockCodeResult = registerIpo(differentStockCode);
    assertThat(stockCodeResult.getResponse().getStatus()).isEqualTo(201);

    Map<String, Object> differentRoundLabel = validRequest();
    differentRoundLabel.put("ipoName", "공모주공통");
    differentRoundLabel.put("stockCode", "111111");
    differentRoundLabel.put("roundLabel", "2차");
    MvcResult roundLabelResult = registerIpo(differentRoundLabel);
    assertThat(roundLabelResult.getResponse().getStatus()).isEqualTo(201);

    assertThat(ipoRepository.count()).isEqualTo(3);
  }

  @Test
  void listIposWithExistingIposReturns200AndListInDescendingOrder() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주A");
    request.put("subscriptionStartDate", "2026-10-01");
    request.put("subscriptionEndDate", "2026-10-02");
    request.put("stockCode", "111111");
    MvcResult first = registerIpo(request);
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    Long firstId = ((Number) readBody(first).get("id")).longValue();

    request.put("ipoName", "공모주B");
    request.put("subscriptionStartDate", "2026-11-01");
    request.put("subscriptionEndDate", "2026-11-02");
    request.put("stockCode", "222222");
    MvcResult second = registerIpo(request);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);
    Long secondId = ((Number) readBody(second).get("id")).longValue();

    MvcResult listResult = mockMvc.perform(get("/ipos").session(session)).andReturn();

    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> list =
        objectMapper.readValue(utf8Body(listResult), new TypeReference<>() {});
    assertThat(list).hasSize(2);
    assertThat(((Number) list.get(0).get("id")).longValue()).isEqualTo(secondId);
    assertThat(((Number) list.get(1).get("id")).longValue()).isEqualTo(firstId);
  }

  @Test
  void listIposExcludesDeletedIpos() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주A");
    request.put("subscriptionStartDate", "2026-10-01");
    request.put("subscriptionEndDate", "2026-10-02");
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Ipo existing = ipoRepository.findById(id).orElseThrow();
    existing.deactivate(LocalDateTime.now());
    ipoRepository.save(existing);

    MvcResult listResult = mockMvc.perform(get("/ipos").session(session)).andReturn();

    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> list =
        objectMapper.readValue(utf8Body(listResult), new TypeReference<>() {});
    assertThat(list).isEmpty();
  }

  @Test
  void listIposWithNoIposReturns200AndEmptyArray() throws Exception {
    signupAndLogin();

    MvcResult listResult = mockMvc.perform(get("/ipos").session(session)).andReturn();

    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> list =
        objectMapper.readValue(utf8Body(listResult), new TypeReference<>() {});
    assertThat(list).isEmpty();
  }

  @Test
  void getIpoByIdExistingReturns200AndIpo() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주A");
    request.put("subscriptionStartDate", "2026-10-01");
    request.put("subscriptionEndDate", "2026-10-02");
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    MvcResult result = mockMvc.perform(get("/ipos/{id}", id).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(((Number) response.get("id")).longValue()).isEqualTo(id);
    assertThat(response.get("ipoName")).isEqualTo("공모주A");
    assertThat(response.get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void getIpoByIdNotFoundReturns404() throws Exception {
    signupAndLogin();

    MvcResult result = mockMvc.perform(get("/ipos/{id}", 9999L).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void getInsightIsPublicAndReturnsStoredDetailInContractShape() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        ipoRepository.save(
            Ipo.builder()
                .companyName("상세회사")
                .ipoName("상세공모-" + System.nanoTime())
                .subscriptionStartDate(java.time.LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(java.time.LocalDate.of(2026, 10, 2))
                .createdAt(now)
                .updatedAt(now)
                .build());
    ipoDetailService.save(
        ipo.getId(),
        new IpoDetailSnapshot(
            "20261007000328",
            "[기재정정]투자설명서",
            "김성포",
            java.time.LocalDate.of(2015, 10, 27),
            "경기도",
            100L,
            2308265000L,
            "김성포",
            new java.math.BigDecimal("30.10"),
            new java.math.BigDecimal("1096.56"),
            new java.math.BigDecimal("30.62"),
            List.of(new IpoDetailSnapshot.Financial("제11기", 23786846687L, null, 7287887715L)),
            List.of(
                new IpoDetailSnapshot.FloatStep(
                    "상장일 유통가능", 6347310L, new java.math.BigDecimal("23.33"))),
            List.of(new IpoDetailSnapshot.Underwriter("KB증권", "대표", 4000000L))));

    // 로그인 없이 호출(공개 GET /ipos/**)
    MvcResult result = mockMvc.perform(get("/ipos/{id}/insight", ipo.getId())).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(body.get("sourceUrl"))
        .isEqualTo("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261007000328");
    assertThat(body.get("ceoName")).isEqualTo("김성포");
    assertThat(body.get("establishedOn")).isEqualTo("2015-10-27");
    assertThat(body.get("parValue")).isEqualTo(100);
    assertThat(((Number) body.get("largestShareholderRatio")).doubleValue()).isEqualTo(30.10);
    assertThat(body.get("syncedAt")).isNotNull();
    Map<String, Object> fin = ((List<Map<String, Object>>) body.get("financials")).get(0);
    assertThat(fin.get("period")).isEqualTo("제11기");
    assertThat(fin.get("operatingProfit")).isNull();
    assertThat(((List<?>) body.get("floatSchedule"))).hasSize(1);
    assertThat(((List<Map<String, Object>>) body.get("underwriters")).get(0).get("role"))
        .isEqualTo("대표");
  }

  @Test
  void getInsightWithoutStoredDetailReturnsNullsAndEmptyArrays() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        ipoRepository.save(
            Ipo.builder()
                .companyName("빈상세")
                .ipoName("빈상세-" + System.nanoTime())
                .subscriptionStartDate(java.time.LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(java.time.LocalDate.of(2026, 10, 2))
                .createdAt(now)
                .updatedAt(now)
                .build());

    MvcResult result = mockMvc.perform(get("/ipos/{id}/insight", ipo.getId())).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> body = readBody(result);
    assertThat(body.get("syncedAt")).isNull();
    assertThat(body.get("sourceUrl")).isNull();
    assertThat((List<?>) body.get("financials")).isEmpty();
    assertThat((List<?>) body.get("floatSchedule")).isEmpty();
    assertThat((List<?>) body.get("underwriters")).isEmpty();
  }

  @Test
  void getInsightIsPublicAndReturns404ForUnknownIpo() throws Exception {
    // 로그인 없이 호출(공개 GET /ipos/**). 없는 공모주는 외부 호출 없이 404.
    MvcResult result = mockMvc.perform(get("/ipos/{id}/insight", 9999L)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void getIpoByIdDeletedReturns404() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주A");
    request.put("subscriptionStartDate", "2026-10-01");
    request.put("subscriptionEndDate", "2026-10-02");
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Ipo existing = ipoRepository.findById(id).orElseThrow();
    existing.deactivate(LocalDateTime.now());
    ipoRepository.save(existing);

    MvcResult result = mockMvc.perform(get("/ipos/{id}", id).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateIpoWithPartialFieldsReturns200AndUpdatedFields() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    updateRequest.put("companyName", "변경된기업명");

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("companyName")).isEqualTo("변경된기업명");
    assertThat(response.get("ipoName")).isEqualTo("공모주A");
    assertThat(response.get("updatedAt")).isNotNull();
  }

  @Test
  void updateIpoWithUnknownIdReturns404() throws Exception {
    signupAndLogin();
    Map<String, Object> updateRequest = validRequest();
    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", 9999L)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateIpoWithInvalidDateOrderReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    updateRequest.put("subscriptionStartDate", "2026-10-03");
    updateRequest.put("subscriptionEndDate", "2026-10-01");

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertValidationError(result);
  }

  @Test
  void updateIpoWithInvalidPriceRangeReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    updateRequest.put("lowerBandPrice", 15000);
    updateRequest.put("upperBandPrice", 10000);

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertValidationError(result);
  }

  @Test
  void updateIpoWithNegativePriceReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    updateRequest.put("lowerBandPrice", -100);

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertValidationError(result);
  }

  @Test
  void updateIpoWithDuplicateStockCodeReturns409() throws Exception {
    signupAndLogin();
    Map<String, Object> first = validRequest();
    MvcResult firstReg = registerIpo(first);
    Long firstId = ((Number) readBody(firstReg).get("id")).longValue();

    Map<String, Object> secondRequest = validRequest();
    secondRequest.put("companyName", "기업B");
    secondRequest.put("ipoName", "공모주B");
    secondRequest.put("stockCode", "654321");
    secondRequest.put("subscriptionStartDate", "2026-11-01");
    secondRequest.put("subscriptionEndDate", "2026-11-02");
    MvcResult secondReg = registerIpo(secondRequest);
    Long secondId = ((Number) readBody(secondReg).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    updateRequest.put("stockCode", "123456");

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", secondId)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertError(result, "DUPLICATE_IPO");
  }

  @Test
  void updateIpoToSameExistingValueAllowsNoChange() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    // 등록 시 companyName 미제공으로 ipoName과 같아짐
    updateRequest.put("companyName", "공모주A");
    updateRequest.put("ipoName", "공모주A");
    updateRequest.put("stockCode", "123456");
    updateRequest.put("subscriptionStartDate", "2026-10-01");

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(readBody(result).get("companyName")).isEqualTo("공모주A");
  }

  @Test
  void deactivateIpoReturns204AndStatusInactiveAndDeletedAtSet() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    MvcResult result = mockMvc.perform(delete("/ipos/{id}", id).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    Ipo deleted = ipoRepository.findById(id).orElseThrow();
    assertThat(deleted.getStatus()).isEqualTo(RecordStatus.INACTIVE);
    assertThat(deleted.getDeletedAt()).isNotNull();
  }

  @Test
  void deactivateIpoWithNotFoundIdReturns404() throws Exception {
    signupAndLogin();

    MvcResult result = mockMvc.perform(delete("/ipos/{id}", 9999L).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void deactivateAlreadyDeletedIpoReturns404() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Ipo existing = ipoRepository.findById(id).orElseThrow();
    existing.deactivate(LocalDateTime.now());
    ipoRepository.save(existing);

    MvcResult result = mockMvc.perform(delete("/ipos/{id}", id).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertError(result, "NOT_FOUND");
  }

  @Test
  void updateIpoOver255CharactersReturns400() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    Map<String, Object> updateRequest = new java.util.LinkedHashMap<>();
    updateRequest.put("companyName", "가".repeat(256));

    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    assertValidationError(result);
  }

  @Test
  void getIpoWithRefundDateReturnsRefundDateAndEstimatedFalse() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주A");
    request.put("subscriptionStartDate", "2027-01-11");
    request.put("subscriptionEndDate", "2027-01-12");
    MvcResult registered = registerIpo(request);
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // 엔티티에 직접 환불일 설정
    Ipo existing = ipoRepository.findById(id).orElseThrow();
    existing.correctSchedule(
        existing.getListingDate(),
        java.time.LocalDate.of(2027, 1, 20),
        existing.getSubscriptionEndDate());
    ipoRepository.save(existing);

    MvcResult result = mockMvc.perform(get("/ipos/{id}", id).session(session)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("refundDate")).isEqualTo("2027-01-20");
    assertThat(response.get("refundDateEstimated")).isEqualTo(false);
  }

  @Test
  void registerIpoWithoutRefundDateCalculatesEstimatedRefundDate() throws Exception {
    signupAndLogin();
    // 2026-11-13 금요일 (휴장일 없는 주, 2영업일 후 = 2026-11-17 화요일)
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주B");
    request.put("subscriptionStartDate", "2026-11-11");
    request.put("subscriptionEndDate", "2026-11-13"); // 금요일
    request.put("stockCode", "999999");

    MvcResult result = registerIpo(request);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> response = readBody(result);
    // 청약 종료일(금) + 2영업일 = 수요일 (토/일 제외)
    assertThat(response.get("refundDate")).isEqualTo("2026-11-17");
    assertThat(response.get("refundDateEstimated")).isEqualTo(true);
  }

  @Test
  void listIposIncludesRefundDateAndEstimatedFlag() throws Exception {
    signupAndLogin();
    Map<String, Object> request = validRequest();
    request.put("ipoName", "공모주A");
    request.put("subscriptionStartDate", "2026-11-02");
    request.put("subscriptionEndDate", "2026-11-03");
    request.put("stockCode", "111111");
    MvcResult first = registerIpo(request);
    assertThat(first.getResponse().getStatus()).isEqualTo(201);

    request.put("ipoName", "공모주B");
    request.put("subscriptionStartDate", "2026-11-09");
    request.put("subscriptionEndDate", "2026-11-13"); // 금요일
    request.put("stockCode", "222222");
    MvcResult second = registerIpo(request);
    assertThat(second.getResponse().getStatus()).isEqualTo(201);

    MvcResult listResult = mockMvc.perform(get("/ipos").session(session)).andReturn();

    assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> list =
        objectMapper.readValue(utf8Body(listResult), new TypeReference<>() {});
    assertThat(list).hasSize(2);
    // 목록에도 refundDate, refundDateEstimated 필드가 포함됨
    for (Map<String, Object> item : list) {
      assertThat(item.get("refundDate")).isNotNull();
      assertThat(item.get("refundDateEstimated")).isNotNull();
    }
  }

  @Test
  void listIposWithoutLoginReturns200() throws Exception {
    // given: IPO 1개 등록 (관리자 세션으로)
    signupAndLogin();
    MvcResult registered = registerIpo(validRequest());
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // when: 로그인 없이 목록 조회 (새 세션)
    MockHttpSession newSession = new MockHttpSession();
    MvcResult result = mockMvc.perform(get("/ipos").session(newSession)).andReturn();

    // then: 200 OK (공개 달력 요구(2026-10-02))
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> list =
        objectMapper.readValue(utf8Body(result), new TypeReference<>() {});
    assertThat(list).hasSize(1);
  }

  @Test
  void getIpoByIdWithoutLoginReturns200() throws Exception {
    // given: IPO 1개 등록
    signupAndLogin();
    MvcResult registered = registerIpo(validRequest());
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // when: 로그인 없이 단건 조회
    MockHttpSession newSession = new MockHttpSession();
    MvcResult result = mockMvc.perform(get("/ipos/{id}", id).session(newSession)).andReturn();

    // then: 200 OK (공개 달력 요구(2026-10-02))
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(((Number) response.get("id")).longValue()).isEqualTo(id);
  }

  @Test
  void registerIpoWithoutLoginReturns401() throws Exception {
    // when: 로그인 없이 등록 시도
    MockHttpSession newSession = new MockHttpSession();
    MvcResult result = registerIpo(validRequest(), newSession);

    // then: 401 Unauthorized
    assertThat(result.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void updateIpoWithoutLoginReturns401() throws Exception {
    // given: IPO 1개 등록
    signupAndLogin();
    MvcResult registered = registerIpo(validRequest());
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // when: 로그인 없이 수정 시도
    MockHttpSession newSession = new MockHttpSession();
    Map<String, Object> updateRequest = Map.of("companyName", "변경");
    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(newSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    // then: 401 Unauthorized
    assertThat(result.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void deactivateIpoWithoutLoginReturns401() throws Exception {
    // given: IPO 1개 등록
    signupAndLogin();
    MvcResult registered = registerIpo(validRequest());
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // when: 로그인 없이 비활성화 시도
    MockHttpSession newSession = new MockHttpSession();
    MvcResult result = mockMvc.perform(delete("/ipos/{id}", id).session(newSession)).andReturn();

    // then: 401 Unauthorized
    assertThat(result.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void registerIpoWithNormalUserReturns403() throws Exception {
    // given: 일반 사용자 로그인 (ADMIN 아님)
    String userEmail = "user-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    String userPassword = "securePass123!";
    String userName = "일반유저";
    MockHttpSession userSession = new MockHttpSession();
    AuthTestSupport.signup(mockMvc, objectMapper, userSession, userEmail, userPassword, userName);
    AuthTestSupport.login(mockMvc, objectMapper, userSession, userEmail, userPassword);

    // when: 일반 사용자로 등록 시도
    MvcResult result = registerIpo(validRequest(), userSession);

    // then: 403 Forbidden (ADMIN만 쓰기 가능)
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    Map<String, Object> error = readBody(result);
    assertThat(error.get("error")).isEqualTo("FORBIDDEN");

    // 정리
    Account account = accountRepository.findByEmail(userEmail);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  @Test
  void updateIpoWithNormalUserReturns403() throws Exception {
    // given: 관리자로 IPO 1개 등록
    signupAndLogin();
    MvcResult registered = registerIpo(validRequest());
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // given: 일반 사용자 로그인
    String userEmail = "user-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    String userPassword = "securePass123!";
    String userName = "일반유저";
    MockHttpSession userSession = new MockHttpSession();
    AuthTestSupport.signup(mockMvc, objectMapper, userSession, userEmail, userPassword, userName);
    AuthTestSupport.login(mockMvc, objectMapper, userSession, userEmail, userPassword);

    // when: 일반 사용자로 수정 시도
    Map<String, Object> updateRequest = Map.of("companyName", "변경");
    MvcResult result =
        mockMvc
            .perform(
                patch("/ipos/{id}", id)
                    .session(userSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andReturn();

    // then: 403 Forbidden
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    Map<String, Object> error = readBody(result);
    assertThat(error.get("error")).isEqualTo("FORBIDDEN");

    // 정리
    Account account = accountRepository.findByEmail(userEmail);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  @Test
  void deactivateIpoWithNormalUserReturns403() throws Exception {
    // given: 관리자로 IPO 1개 등록
    signupAndLogin();
    MvcResult registered = registerIpo(validRequest());
    Long id = ((Number) readBody(registered).get("id")).longValue();

    // given: 일반 사용자 로그인
    String userEmail = "user-" + ThreadLocalRandom.current().nextLong() + "@example.com";
    String userPassword = "securePass123!";
    String userName = "일반유저";
    MockHttpSession userSession = new MockHttpSession();
    AuthTestSupport.signup(mockMvc, objectMapper, userSession, userEmail, userPassword, userName);
    AuthTestSupport.login(mockMvc, objectMapper, userSession, userEmail, userPassword);

    // when: 일반 사용자로 비활성화 시도
    MvcResult result = mockMvc.perform(delete("/ipos/{id}", id).session(userSession)).andReturn();

    // then: 403 Forbidden
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    Map<String, Object> error = readBody(result);
    assertThat(error.get("error")).isEqualTo("FORBIDDEN");

    // 정리
    Account account = accountRepository.findByEmail(userEmail);
    if (account != null) {
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  private Map<String, Object> validRequest() {
    Map<String, Object> request = new java.util.LinkedHashMap<>();
    request.put("ipoName", "공모주A");
    request.put("stockCode", "123456");
    request.put("roundLabel", "1차");
    request.put("lowerBandPrice", 10000);
    request.put("upperBandPrice", 12000);
    request.put("fixedPrice", 11000);
    request.put("subscriptionStartDate", "2026-10-01");
    request.put("subscriptionEndDate", "2026-10-02");
    request.put("listingDate", "2026-10-15");
    request.put("offeringShareCount", 1000000);
    return request;
  }

  private MvcResult registerIpo(Map<String, Object> request) throws Exception {
    return registerIpo(request, session);
  }

  private MvcResult registerIpo(Map<String, Object> request, MockHttpSession session)
      throws Exception {
    return mockMvc
        .perform(
            post("/ipos")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andReturn();
  }

  private void signupAndLogin() throws Exception {
    AuthTestSupport.signup(mockMvc, objectMapper, session, testEmail, testPassword, testName);
    Account admin = accountRepository.findByEmail(testEmail);
    admin.changeRole(AccountRole.ADMIN);
    accountRepository.save(admin);
    AuthTestSupport.login(mockMvc, objectMapper, session, testEmail, testPassword);
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
}
