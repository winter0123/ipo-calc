package com.vibeaiteam.ipocatalog.controller.ipo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC-001~AC-006: POST /ipos 등록 계약과 IPO 중복 검사 규칙.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IpoRepository ipoRepository;

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
        ipoRepository.deleteAll();
        Account account = accountRepository.findByEmail(testEmail);
        if (account != null) {
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
        assertThat(response.get("companyName")).isEqualTo("기업A");
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
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void registerIpoWithMissingCompanyNameReturns400() throws Exception {
        signupAndLogin();
        Map<String, Object> request = validRequest();
        request.remove("companyName");

        MvcResult result = registerIpo(request);

        assertValidationError(result);
        assertThat(ipoRepository.count()).isZero();
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

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("companyName", "기업A");
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
        return mockMvc.perform(post("/ipos")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    private void signupAndLogin() throws Exception {
        mockMvc.perform(post("/accounts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", testPassword,
                                "name", testName))))
                .andReturn();

        mockMvc.perform(post("/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", testPassword))))
                .andReturn();
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
