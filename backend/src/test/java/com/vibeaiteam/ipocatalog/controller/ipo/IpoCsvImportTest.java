package com.vibeaiteam.ipocatalog.controller.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.domain.model.account.AccountRole;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** AC-018~AC-020: POST /ipos/import CSV 일괄 등록 계약. 전체 성공/부분 성공(행 단위 실패 격리)/빈 파일·헤더 누락 400. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoCsvImportTest {

  private static final String CSV_HEADER =
      "companyName,ipoName,stockCode,roundLabel,lowerBandPrice,upperBandPrice,"
          + "fixedPrice,subscriptionStartDate,subscriptionEndDate,listingDate,offeringShareCount";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private IpoRepository ipoRepository;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "ipo-csv-" + nonce + "@example.com";
    testPassword = "securePass123!";
    testName = "CSV테스터";
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
  void importCsvWithAllValidRowsReturns200AndSuccessCount() throws Exception {
    signupAndLogin();
    String csv =
        CSV_HEADER
            + "\n"
            + "기업A,공모주A,111111,,10000,12000,11000,2026-10-01,2026-10-02,2026-10-15,1000000\n"
            + "기업B,공모주B,222222,,13000,14000,,2026-11-01,2026-11-02,,500000";

    MvcResult result = importCsv(csv);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("successCount")).isEqualTo(2);
    assertThat(response.get("failureCount")).isEqualTo(0);
    assertThat((List<?>) response.get("failures")).isEmpty();
    assertThat(ipoRepository.count()).isEqualTo(2);
  }

  @Test
  void importCsvWithSomeInvalidRowsReturns200AndFailureDetailsWithoutRollback() throws Exception {
    signupAndLogin();

    Map<String, Object> preRegistered = new java.util.LinkedHashMap<>();
    preRegistered.put("companyName", "기업기존");
    preRegistered.put("ipoName", "공모주기존");
    preRegistered.put("stockCode", "123456");
    preRegistered.put("lowerBandPrice", 10000);
    preRegistered.put("upperBandPrice", 12000);
    preRegistered.put("subscriptionStartDate", "2026-09-01");
    preRegistered.put("subscriptionEndDate", "2026-09-02");
    MvcResult pre =
        mockMvc
            .perform(
                post("/ipos")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(preRegistered)))
            .andReturn();
    assertThat(pre.getResponse().getStatus()).isEqualTo(201);

    String csv =
        CSV_HEADER
            + "\n"
            + "기업A,공모주A,111111,,10000,12000,11000,2026-10-01,2026-10-02,2026-10-15,1000000\n"
            + "기업B,공모주B,222222,,10000,12000,11000,2026-13-01,2026-10-02,2026-10-15,1000000\n"
            + "기업C,공모주C,333333,,10000,12000,11000,2026-11-01,2026-11-02,,500000\n"
            + "기업D,공모주D,123456,,10000,12000,11000,2026-12-01,2026-12-02,2026-12-15,1000000\n"
            + "기업E,공모주E,555555,,10000,12000,11000,,2026-10-02,2026-10-15,1000000";

    MvcResult result = importCsv(csv);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("successCount")).isEqualTo(2);
    assertThat(response.get("failureCount")).isEqualTo(3);
    List<Map<String, Object>> failures =
        objectMapper.convertValue(response.get("failures"), new TypeReference<>() {});
    assertThat(failures).hasSize(3);

    Map<String, Object> badDate = failures.get(0);
    assertThat(((Number) badDate.get("rowNumber")).intValue()).isEqualTo(3);
    assertThat((String) badDate.get("reason")).contains("subscriptionStartDate");

    Map<String, Object> duplicate = failures.get(1);
    assertThat(((Number) duplicate.get("rowNumber")).intValue()).isEqualTo(5);
    assertThat((String) duplicate.get("reason")).contains("이미 등록된 IPO");

    Map<String, Object> missingValue = failures.get(2);
    assertThat(((Number) missingValue.get("rowNumber")).intValue()).isEqualTo(6);
    assertThat((String) missingValue.get("reason")).contains("subscriptionStartDate는 필수");

    assertThat(ipoRepository.count()).isEqualTo(3);
  }

  @Test
  void importCsvWithNonNumericPriceMarksOnlyThatRowFailed() throws Exception {
    signupAndLogin();
    String csv =
        CSV_HEADER
            + "\n"
            + "기업A,공모주A,111111,,10000,12000,11000,2026-10-01,2026-10-02,2026-10-15,1000000\n"
            + "기업B,공모주B,222222,,십만원,12000,11000,2026-11-01,2026-11-02,,500000";

    MvcResult result = importCsv(csv);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("successCount")).isEqualTo(1);
    assertThat(response.get("failureCount")).isEqualTo(1);
    List<Map<String, Object>> failures =
        objectMapper.convertValue(response.get("failures"), new TypeReference<>() {});
    assertThat(((Number) failures.get(0).get("rowNumber")).intValue()).isEqualTo(3);
    assertThat((String) failures.get(0).get("reason")).contains("lowerBandPrice");
    assertThat(ipoRepository.count()).isEqualTo(1);
  }

  @Test
  void importCsvWithSpecialCharactersParsesSafely() throws Exception {
    signupAndLogin();
    String csv =
        CSV_HEADER
            + "\n"
            + "\"기업,주식회사\",\"공모주따옴표\"\"테스트\",777777,,10000,12000,11000,"
            + "2026-10-01,2026-10-02,2026-10-15,1000000";

    MvcResult result = importCsv(csv);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("successCount")).isEqualTo(1);
    assertThat(response.get("failureCount")).isEqualTo(0);
    List<Ipo> saved = ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getCompanyName()).isEqualTo("기업,주식회사");
    assertThat(saved.get(0).getIpoName()).isEqualTo("공모주따옴표\"테스트");
  }

  @Test
  void importCsvWithEmptyFileReturns400InvalidCsv() throws Exception {
    signupAndLogin();

    MvcResult result =
        mockMvc
            .perform(
                multipart("/ipos/import")
                    .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
                    .session(session))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("error")).isEqualTo("INVALID_CSV");
    assertThat(response.get("message")).isNotNull();
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void importCsvWithMissingRequiredHeaderReturns400InvalidCsv() throws Exception {
    signupAndLogin();
    String csv = "ipoName,stockCode,\n" + "공모주A,123456,";

    MvcResult result = importCsv(csv);

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("error")).isEqualTo("INVALID_CSV");
    assertThat((String) response.get("message")).contains("companyName");
    assertThat(ipoRepository.count()).isZero();
  }

  @Test
  void importCsvWithTooLargeFileReturns400FileTooLarge() throws Exception {
    signupAndLogin();
    String huge = "x".repeat(5 * 1024 * 1024 + 1);

    MvcResult result =
        mockMvc
            .perform(
                multipart("/ipos/import")
                    .file(
                        new MockMultipartFile(
                            "file", "huge.csv", "text/csv", huge.getBytes(StandardCharsets.UTF_8)))
                    .session(session))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> response = readBody(result);
    assertThat(response.get("error")).isEqualTo("FILE_TOO_LARGE");
    assertThat(ipoRepository.count()).isZero();
  }

  private MvcResult importCsv(String csvContent) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "ipos.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
    return mockMvc.perform(multipart("/ipos/import").file(file).session(session)).andReturn();
  }

  private void signupAndLogin() throws Exception {
    mockMvc
        .perform(
            post("/accounts")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", testEmail,
                            "password", testPassword,
                            "name", testName))))
        .andReturn();

    Account admin = accountRepository.findByEmail(testEmail);
    admin.setRole(AccountRole.ADMIN);
    accountRepository.save(admin);

    mockMvc
        .perform(
            post("/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", testEmail,
                            "password", testPassword))))
        .andReturn();
  }

  private Map<String, Object> readBody(MvcResult result) throws Exception {
    byte[] body = result.getResponse().getContentAsByteArray();
    return objectMapper.readValue(new ByteArrayInputStream(body), Map.class);
  }
}
