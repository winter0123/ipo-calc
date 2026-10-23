package com.ipocalc.brokerintegration.controller.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.ipocalc.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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

/**
 * 브로커 API 인증정보 API 통합 테스트.
 *
 * <p>AC 인용: docs/breakdown/broker-readonly-api-pilot.md (SF-2) - AC-1810: 등록한 시크릿은 DB에 암호화되어 평문이
 * 보이지 않는다 - AC-1811: 응답 JSON에 시크릿·토큰 필드가 존재하지 않는다 - AC-1812: 타 계정 소유 계좌 접근은 403 - AC-1813: 같은
 * 계좌·provider 중복 등록은 409 - AC-1814: 브로커 암호화 키 미설정 시 등록 실패는 서비스 단위 테스트에서 검증한다
 * (ApiCredentialServiceTest.registerWhenBrokerEncryptionKeyMissingThrowsUnavailable).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class ApiCredentialControllerTest {

  private static final String PLAIN_ACCOUNT_NUMBER = "123456789012";
  private static final String DUMMY_APP_KEY = "dummy-app-key";
  private static final String DUMMY_APP_SECRET = "dummy-app-secret";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;

  @Autowired private SecuritiesAccountApiCredentialRepository credentialRepository;

  @Autowired private AccountNumberEncryptor accountNumberEncryptor;

  private String testEmail;
  private String otherEmail;
  private MockHttpSession session;
  private final List<SecuritiesFirm> createdFirms = new ArrayList<>();

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "api-credential-owner-" + nonce + "@example.com";
    otherEmail = null;
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    cleanupAccount(testEmail);
    cleanupAccount(otherEmail);
    cleanupFirms();
  }

  private void cleanupAccount(String email) {
    if (email == null) {
      return;
    }
    Account account = accountRepository.findByEmail(email);
    if (account == null) {
      return;
    }
    Long acctId = account.getId();
    List<Investor> investors =
        investorRepository.findAll().stream()
            .filter(i -> acctId.equals(i.getAccountId()))
            .collect(Collectors.toList());
    for (Investor investor : investors) {
      List<SecuritiesAccount> accounts =
          securitiesAccountRepository.findByInvestorId(investor.getId());
      deleteCredentialsOf(accounts);
      securitiesAccountRepository.deleteAll(accounts);
    }
    investorRepository.deleteAll(investors);
    investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
    accountRepository.delete(account);
  }

  private void deleteCredentialsOf(List<SecuritiesAccount> accounts) {
    if (accounts.isEmpty()) {
      return;
    }
    Set<Long> accountIds =
        accounts.stream().map(SecuritiesAccount::getId).collect(Collectors.toSet());
    List<SecuritiesAccountApiCredential> toDelete =
        credentialRepository.findAll().stream()
            .filter(c -> accountIds.contains(c.getSecuritiesAccountId()))
            .collect(Collectors.toList());
    if (!toDelete.isEmpty()) {
      credentialRepository.deleteAll(toDelete);
    }
  }

  private void cleanupFirms() {
    List<SecuritiesFirm> toDelete =
        createdFirms.stream().filter(f -> f.getId() != null).collect(Collectors.toList());
    if (!toDelete.isEmpty()) {
      securitiesFirmRepository.deleteAll(toDelete);
    }
  }

  private void signupAndLogin() throws Exception {
    AuthTestSupport.signupAndLogin(
        mockMvc, objectMapper, session, testEmail, "securePass123!", "인증정보테스터");
  }

  private Long registerFirm() {
    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("한국투자증권" + ThreadLocalRandom.current().nextLong())
                .apiSupported(true)
                .apiProvider("KIS")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdFirms.add(firm);
    return firm.getId();
  }

  private Long registerInvestorAndGetId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("displayName", "인증정보투자자", "relationship", "SELF")))
                    .session(session))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("테스트 사전 준비: 투자자 등록은 201이어야 합니다").isEqualTo(201);
    return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
  }

  private Long createSecuritiesAccount(Long investorId, Long firmId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/securities-accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "investorId", investorId,
                                "securitiesFirmId", firmId,
                                "accountNumber", PLAIN_ACCOUNT_NUMBER,
                                "accountName", "개인 MTS 계좌")))
                    .session(session))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .as("테스트 사전 준비: 증권계좌 등록은 201이어야 합니다")
        .isEqualTo(201);
    return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
  }

  private Long createOtherAccountsSecuritiesAccount() throws Exception {
    long nonce = ThreadLocalRandom.current().nextLong();
    otherEmail = "api-credential-other-" + nonce + "@example.com";
    LocalDateTime now = LocalDateTime.now();
    Account other =
        accountRepository.save(
            Account.builder()
                .email(otherEmail)
                .passwordHash("encodedPasswordHash")
                .name("다른계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    Investor investor =
        investorRepository.save(
            Investor.builder()
                .accountId(other.getId())
                .displayName("다른계정투자자")
                .relationship("OTHER")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    SecuritiesFirm firm =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("다른계정증권사" + nonce)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdFirms.add(firm);
    SecuritiesAccount account =
        securitiesAccountRepository.save(
            SecuritiesAccount.builder()
                .investorId(investor.getId())
                .securitiesFirmId(firm.getId())
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .isDefault(true)
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return account.getId();
  }

  private Map<String, Object> credentialRequest() {
    return Map.of("apiProvider", "KIS", "appKey", DUMMY_APP_KEY, "appSecret", DUMMY_APP_SECRET);
  }

  private MvcResult postCredential(Long securitiesAccountId, Map<String, ?> request)
      throws Exception {
    return mockMvc
        .perform(
            post("/securities-accounts/{id}/api-credential", securitiesAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(session))
        .andReturn();
  }

  private MvcResult putCredential(Long securitiesAccountId, Map<String, ?> request)
      throws Exception {
    return mockMvc
        .perform(
            put("/securities-accounts/{id}/api-credential", securitiesAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(session))
        .andReturn();
  }

  private MvcResult getCredential(Long securitiesAccountId) throws Exception {
    return mockMvc
        .perform(
            get("/securities-accounts/{id}/api-credential", securitiesAccountId).session(session))
        .andReturn();
  }

  private MvcResult deleteCredential(Long securitiesAccountId) throws Exception {
    return mockMvc
        .perform(
            delete("/securities-accounts/{id}/api-credential", securitiesAccountId)
                .session(session))
        .andReturn();
  }

  private String utf8Body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  void registerCredentialReturns201WithoutAnySecretField() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있음 (AC-1811)
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);

    // When: POST /securities-accounts/{id}/api-credential 요청을 보냄
    MvcResult result = postCredential(securitiesAccountId, credentialRequest());

    // Then: 201 Created와 함께 연결 상태·마스킹된 계좌번호가 반환됨 (AC-1811)
    assertThat(result.getResponse().getStatus()).as("유효한 인증정보 등록 요청은 201이어야 합니다").isEqualTo(201);
    String body = utf8Body(result);
    Map<String, Object> response = objectMapper.readValue(body, Map.class);
    assertThat(response.get("connectionStatus")).isEqualTo("CONNECTED");
    assertThat(((Number) response.get("securitiesAccountId")).longValue())
        .isEqualTo(securitiesAccountId);
    assertThat(response.get("accountNumber"))
        .isEqualTo(AccountNumberMasker.mask(PLAIN_ACCOUNT_NUMBER));
    assertThat(response.get("lastSyncedAt")).isNull();

    // And: 응답 JSON에 시크릿·토큰 필드가 아예 없음 (AC-1811)
    assertThat(response.keySet())
        .as("응답에 시크릿·토큰 필드가 존재하면 안 됩니다 (AC-1811)")
        .doesNotContain("appKey", "appSecret", "accessToken", "tokenExpiresAt", "secret", "token");
    assertThat(body)
        .as("응답 본문에 평문 시크릿이 포함되면 안 됩니다 (AC-1811)")
        .doesNotContain(DUMMY_APP_KEY)
        .doesNotContain(DUMMY_APP_SECRET);
  }

  @Test
  void registerCredentialStoresEncryptedSecretsInDatabase() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있음 (AC-1810)
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);

    // When: 인증정보를 등록함
    MvcResult result = postCredential(securitiesAccountId, credentialRequest());
    assertThat(result.getResponse().getStatus()).isEqualTo(201);

    // Then: DB에 저장된 시크릿은 평문이 아닌 암호문임 (AC-1810)
    SecuritiesAccountApiCredential saved =
        credentialRepository
            .findBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(securitiesAccountId, "KIS")
            .orElseThrow();
    assertThat(saved.getAppKeyEncrypted())
        .as("DB에 평문 앱키가 보이면 안 됩니다 (AC-1810)")
        .isNotEqualTo(DUMMY_APP_KEY)
        .doesNotContain(DUMMY_APP_KEY);
    assertThat(saved.getAppSecretEncrypted())
        .as("DB에 평문 앱시크릿이 보이면 안 됩니다 (AC-1810)")
        .isNotEqualTo(DUMMY_APP_SECRET)
        .doesNotContain(DUMMY_APP_SECRET);
    assertThat(saved.getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(saved.getDeletedAt()).isNull();
  }

  @Test
  void getStatusBeforeConnectionReturns200Disconnected() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 아직 연결되지 않음
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);

    // When & Then: GET 요청 시 200 + DISCONNECTED
    MvcResult result = getCredential(securitiesAccountId);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(response.get("connectionStatus")).isEqualTo("DISCONNECTED");
    assertThat(response.get("id")).isNull();
    assertThat(response.get("accountNumber"))
        .isEqualTo(AccountNumberMasker.mask(PLAIN_ACCOUNT_NUMBER));
    assertThat(response.keySet())
        .doesNotContain("appKey", "appSecret", "accessToken", "tokenExpiresAt");
  }

  @Test
  void registerDuplicateCredentialReturns409() throws Exception {
    // Given: 같은 계좌에 이미 KIS 인증정보가 등록되어 있음 (AC-1813)
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);
    assertThat(postCredential(securitiesAccountId, credentialRequest()).getResponse().getStatus())
        .as("테스트 사전 준비: 첫 등록은 201이어야 합니다")
        .isEqualTo(201);

    // When: 같은 provider로 다시 등록 시도 (AC-1813)
    MvcResult duplicate = postCredential(securitiesAccountId, credentialRequest());

    // Then: 409 Conflict + CONFLICT
    assertThat(duplicate.getResponse().getStatus())
        .as("같은 계좌·provider 중복 등록은 409여야 합니다 (AC-1813)")
        .isEqualTo(409);
    Map<String, Object> body = objectMapper.readValue(utf8Body(duplicate), Map.class);
    assertThat(body.get("error")).isEqualTo("CONFLICT");
    assertThat(utf8Body(duplicate)).doesNotContain(DUMMY_APP_KEY).doesNotContain(DUMMY_APP_SECRET);
  }

  @Test
  void registerCredentialForOtherAccountsSecuritiesAccountReturns403() throws Exception {
    // Given: 인증된 계정 A와, 다른 계정 B가 소유한 증권계좌 (AC-1812)
    signupAndLogin();
    Long otherSecuritiesAccountId = createOtherAccountsSecuritiesAccount();

    // When: 계정 A가 계정 B의 계좌에 인증정보를 등록 시도 (AC-1812)
    MvcResult postResult = postCredential(otherSecuritiesAccountId, credentialRequest());
    MvcResult getResult = getCredential(otherSecuritiesAccountId);
    MvcResult deleteResult = deleteCredential(otherSecuritiesAccountId);

    // Then: 세 요청 모두 403 Forbidden + FORBIDDEN (AC-1812)
    assertThat(postResult.getResponse().getStatus())
        .as("타 계정 계좌 인증정보 등록은 403이어야 합니다 (AC-1812)")
        .isEqualTo(403);
    assertThat(getResult.getResponse().getStatus())
        .as("타 계정 계좌 연결 상태 조회는 403이어야 합니다 (AC-1812)")
        .isEqualTo(403);
    assertThat(deleteResult.getResponse().getStatus())
        .as("타 계정 계좌 연결 해제는 403이어야 합니다 (AC-1812)")
        .isEqualTo(403);
    for (MvcResult result : List.of(postResult, getResult, deleteResult)) {
      Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
      assertThat(body.get("error")).isEqualTo("FORBIDDEN");
    }

    // And: 인증정보가 하나도 생성되지 않음 (AC-1812)
    assertThat(
            credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                otherSecuritiesAccountId))
        .isEmpty();
  }

  @Test
  void putReplacesKeyAndReturnsNewIssueDateWithoutSecrets() throws Exception {
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);
    assertThat(postCredential(securitiesAccountId, credentialRequest()).getResponse().getStatus())
        .isEqualTo(201);

    MvcResult result =
        putCredential(
            securitiesAccountId,
            Map.of("appKey", "replaced-app-key", "appSecret", "replaced-app-secret"));

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String body = utf8Body(result);
    assertThat(body).doesNotContain("replaced-app-key").doesNotContain("replaced-app-secret");
    Map<?, ?> json = objectMapper.readValue(body, Map.class);
    assertThat(json.get("keyIssuedOn")).isEqualTo(java.time.LocalDate.now().toString());
    assertThat(json.get("supportedProvider")).isEqualTo("KIS");
  }

  @Test
  void putWithoutConnectionReturns404() throws Exception {
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);

    MvcResult result = putCredential(securitiesAccountId, Map.of("appKey", "k", "appSecret", "s"));

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void disconnectReturns204AndPhysicallyDeletesCredential() throws Exception {
    // Given: 인증된 계정이 소유한 계좌에 인증정보가 등록되어 있음
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);
    assertThat(postCredential(securitiesAccountId, credentialRequest()).getResponse().getStatus())
        .isEqualTo(201);

    // When: DELETE 요청 (비밀키 암호문이 남지 않도록 물리 삭제, D-B-015)
    MvcResult result = deleteCredential(securitiesAccountId);

    // Then: 204 No Content
    assertThat(result.getResponse().getStatus()).as("연결 해제는 204 No Content여야 합니다").isEqualTo(204);

    // And: 행이 물리 삭제되어 암호문이 DB에 남지 않음
    assertThat(
            credentialRepository.findAll().stream()
                .anyMatch(c -> securitiesAccountId.equals(c.getSecuritiesAccountId())))
        .as("연결 해제 후 인증정보 행이 남아 있으면 안 됩니다")
        .isFalse();

    // And: 이후 상태 조회는 DISCONNECTED, 재등록은 201로 허용됨
    Map<String, Object> statusResponse =
        objectMapper.readValue(utf8Body(getCredential(securitiesAccountId)), Map.class);
    assertThat(statusResponse.get("connectionStatus")).isEqualTo("DISCONNECTED");
    assertThat(postCredential(securitiesAccountId, credentialRequest()).getResponse().getStatus())
        .as("연결 해제 후 재등록은 201이어야 합니다")
        .isEqualTo(201);
  }

  @Test
  void registerCredentialWithBlankFieldsReturns400() throws Exception {
    // Given: 인증된 계정이 소유한 증권계좌가 있고, 필수값이 빈 요청을 보냄
    signupAndLogin();
    Long firmId = registerFirm();
    Long investorId = registerInvestorAndGetId();
    Long securitiesAccountId = createSecuritiesAccount(investorId, firmId);

    // When & Then: 빈 appSecret 요청은 400
    MvcResult result =
        postCredential(
            securitiesAccountId,
            Map.of("apiProvider", "KIS", "appKey", DUMMY_APP_KEY, "appSecret", " "));

    assertThat(result.getResponse().getStatus()).as("필수값이 빈 등록 요청은 400이어야 합니다").isEqualTo(400);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("VALIDATION_FAILED");

    // And: 인증정보가 생성되지 않음
    assertThat(
            credentialRepository.findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                securitiesAccountId))
        .isEmpty();
  }

  @Test
  void getStatusForUnknownSecuritiesAccountReturns404() throws Exception {
    // Given: 인증된 계정과 존재하지 않는 증권계좌 ID
    signupAndLogin();

    // When & Then: 404 Not Found + NOT_FOUND
    MvcResult result = getCredential(99999999L);
    assertThat(result.getResponse().getStatus())
        .as("존재하지 않는 계좌의 연결 상태 조회는 404여야 합니다")
        .isEqualTo(404);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("error")).isEqualTo("NOT_FOUND");
  }
}
