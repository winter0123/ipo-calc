package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC 인용: docs/spec/securities-account-management.md, docs/work-log/securities-account-management.md
 * - AC-001: 증권계좌 등록 성공 — 유효한 요청 시 201 Created + 신규 레코드 생성,
 *           응답에 계좌 ID/투자자 ID/증권사 ID/마스킹된 계좌번호/status=ACTIVE/deletedAt=null 포함
 * - AC-002: 증권계좌 등록 실패 (필수값 누락) — investorId/securitiesFirmId/accountNumber 중 하나 이상 누락 시
 *           400 Bad Request + VALIDATION_ERROR, 레코드 미생성
 * - AC-003: 증권계좌 등록 실패 (존재하지 않는 투자자) — 400 Bad Request, 레코드 미생성
 * - AC-004: 증권계좌 등록 실패 (존재하지 않는 증권사) — 400 Bad Request, 레코드 미생성
 * - AC-005: 증권계좌 등록 실패 (계정 소유권 없음) — 계정 A가 계정 B의 투자자 ID로 요청 시
 *           403 Forbidden + FORBIDDEN
 * - D-SA-003: 투자자의 첫 계좌 등록 시 isDefault=true 자동 설정 (요청의 isDefault와 무관하게 강제)
 * - SF-2: 등록 성공 시 accountNumber는 AES 암호화되어 DB에 저장되고, 응답에는 원본 평문이 아닌
 *         마스킹된 값(뒷 4자리만 노출)이 포함된다
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesAccountControllerTest {

    private static final String ACCOUNT_NUMBER = "123-456-789012";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private SecuritiesFirmRepository securitiesFirmRepository;

    @Autowired
    private SecuritiesAccountRepository securitiesAccountRepository;

    @Autowired
    private AccountNumberEncryptor accountNumberEncryptor;

    private String testEmail;
    private String testPassword;
    private String testName;
    private MockHttpSession session;
    private Long accountId;
    private Long firmId;
    private String otherEmail;
    private final List<SecuritiesAccount> createdAccounts = new ArrayList<>();
    private final List<SecuritiesFirm> createdFirms = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long nonce = ThreadLocalRandom.current().nextLong();
        testEmail = "securities-account-owner-" + nonce + "@example.com";
        testPassword = "securePass123!";
        testName = "계좌테스터";
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
        if (account != null) {
            Long acctId = account.getId();
            List<Investor> investors = investorRepository.findAll().stream()
                    .filter(i -> acctId.equals(i.getAccountId()))
                    .collect(Collectors.toList());
            for (Investor investor : investors) {
                List<SecuritiesAccount> accounts = securitiesAccountRepository.findByInvestorId(investor.getId());
                securitiesAccountRepository.deleteAll(accounts);
                createdAccounts.removeAll(accounts);
            }
            investorRepository.deleteAll(investors);
            accountRepository.delete(account);
        }
    }

    private void cleanupFirms() {
        List<SecuritiesFirm> toDelete = createdFirms.stream()
                .filter(f -> f.getId() != null)
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            securitiesFirmRepository.deleteAll(toDelete);
        }
    }

    private void signupAndLogin() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", testPassword,
                                "name", testName)))
                        .session(session))
                .andReturn();

        Account account = accountRepository.findByEmail(testEmail);
        accountId = account.getId();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", testEmail,
                                "password", testPassword)))
                        .session(session))
                .andReturn();
    }

    private Long registerFirm() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        SecuritiesFirm firm = securitiesFirmRepository.save(SecuritiesFirm.builder()
                .name("한국투자증권" + ThreadLocalRandom.current().nextLong())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
        createdFirms.add(firm);
        return firm.getId();
    }

    private Long registerInvestorAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post("/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "계좌투자자",
                                "relationship", "SELF")))
                        .session(session))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("테스트 사전 준비: 투자자 등록은 201이어야 합니다").isEqualTo(201);
        return ((Number) objectMapper.readValue(utf8Body(result), Map.class).get("id")).longValue();
    }

    private Long createOtherAccountInvestor() {
        long nonce = ThreadLocalRandom.current().nextLong();
        otherEmail = "other-account-owner-" + nonce + "@example.com";
        LocalDateTime now = LocalDateTime.now();
        Account other = accountRepository.save(Account.builder()
                .email(otherEmail)
                .passwordHash("encodedPasswordHash")
                .name("다른계정주")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
        Investor saved = investorRepository.save(Investor.builder()
                .accountId(other.getId())
                .displayName("다른계정투자자")
                .relationship("OTHER")
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return saved.getId();
    }

    private MvcResult registerSecuritiesAccount(Map<String, ?> request) throws Exception {
        return mockMvc.perform(post("/securities-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(session))
                .andReturn();
    }

    private MvcResult updateSecuritiesAccount(Long id, Map<String, ?> request) throws Exception {
        return mockMvc.perform(patch("/securities-accounts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(session))
                .andReturn();
    }

    private String utf8Body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void registerSecuritiesAccountWithValidRequestReturns201WithMaskedAccountNumber() throws Exception {
        // Given: 인증된 계정이 유효한 투자자/증권사를 소유하고 있고, 계좌번호가 제공됨 (AC-001)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long securitiesFirmId = registerFirm();

        var request = Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "accountName", "개인 MTS 계좌",
                "isDefault", true
        );

        // When: POST /securities-accounts 요청을 보냄
        MvcResult result = registerSecuritiesAccount(request);

        // Then: 201 Created 응답과 함께 신규 레코드가 생성됨 (AC-001)
        int status = result.getResponse().getStatus();
        assertThat(status).as("유효한 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);

        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(response.get("id")).as("응답에 계좌 id가 포함되어야 합니다 (AC-001)").isNotNull();
        assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
        assertThat(((Number) response.get("securitiesFirmId")).longValue()).isEqualTo(securitiesFirmId);

        // And: 응답의 계좌번호는 마스킹된 형태 (원문 평문 노출 금지, 뒷 4자리만 노출) (AC-001, SF-2)
        String maskedAccountNumber = (String) response.get("accountNumber");
        String expectedMasked = AccountNumberMasker.mask(ACCOUNT_NUMBER);
        assertThat(maskedAccountNumber).as("응답 계좌번호는 마스킹된 값이어야 합니다 (AC-001)").isEqualTo(expectedMasked);
        assertThat(maskedAccountNumber)
                .as("응답 계좌번호는 원본 평문을 그대로 노출하면 안 됩니다 (SF-2)")
                .isNotEqualTo(ACCOUNT_NUMBER);
        assertThat(maskedAccountNumber).as("계좌번호 뒷 4자리는 노출되어야 합니다 (AC-001)").endsWith("9012");
        assertThat(response.get("accountName")).isEqualTo("개인 MTS 계좌");
        assertThat(response.get("isDefault")).isEqualTo(true);
        assertThat(response.get("status")).as("status는 ACTIVE여야 합니다 (AC-001)").isEqualTo("ACTIVE");
        assertThat(response.get("deletedAt")).as("deletedAt은 null이어야 합니다 (AC-001)").isNull();
        assertThat(response.get("createdAt")).as("createdAt이 포함되어야 합니다 (AC-001)").isNotNull();
        assertThat(response.get("updatedAt")).as("updatedAt이 포함되어야 합니다 (AC-001)").isNotNull();

        // And: DB에는 암호화된 계좌번호가 저장됨 (평문 저장 금지) (SF-2)
        SecuritiesAccount saved = securitiesAccountRepository.findById(
                ((Number) response.get("id")).longValue()).orElseThrow();
        assertThat(saved.getAccountNumber())
                .as("DB에는 원본 평문이 아니라 암호화된 계좌번호가 저장되어야 합니다 (SF-2)")
                .isNotEqualTo(ACCOUNT_NUMBER);
        assertThat(saved.getInvestorId()).isEqualTo(investorId);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        createdAccounts.add(saved);
    }

    @Test
    void registerFirstAccountForcesIsDefaultTrueEvenWhenRequestFalse() throws Exception {
        // Given: 인증된 계정이 소유한 투자자가 첫 계좌 등록을 앞두고 기존 계좌가 0건임 (D-SA-003)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long securitiesFirmId = registerFirm();

        var request = Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "isDefault", false
        );

        // When: isDefault=false로 POST /securities-accounts 요청을 보냄
        MvcResult result = registerSecuritiesAccount(request);

        // Then: 201 Created + 응답의 isDefault는 요청값과 무관하게 true (D-SA-003)
        int status = result.getResponse().getStatus();
        assertThat(status).as("첫 계좌 등록은 201이어야 합니다 (D-SA-003)").isEqualTo(201);

        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(response.get("isDefault"))
                .as("첫 계좌는 요청의 isDefault와 무관하게 isDefault=true여야 합니다 (D-SA-003)")
                .isEqualTo(true);

        // And: DB에도 is_default=true로 저장됨 (D-SA-003)
        SecuritiesAccount saved = securitiesAccountRepository.findById(
                ((Number) response.get("id")).longValue()).orElseThrow();
        assertThat(saved.getIsDefault()).as("DB에도 is_default=true로 저장되어야 합니다 (D-SA-003)").isTrue();
        createdAccounts.add(saved);
    }

    @Test
    void registerSecondAccountKeepsRequestedIsDefaultFalse() throws Exception {
        // Given: 투자자가 첫 계좌(기본 계좌)를 이미 보유 중임 (D-SA-003 경계: 강제 대상은 첫 계좌뿐)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long securitiesFirmId = registerFirm();

        MvcResult first = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "isDefault", false
        ));
        assertThat(first.getResponse().getStatus())
                .as("테스트 사전 준비: 첫 계좌 등록은 201이어야 합니다").isEqualTo(201);
        SecuritiesAccount firstSaved = securitiesAccountRepository.findById(
                ((Number) objectMapper.readValue(utf8Body(first), Map.class).get("id")).longValue()).orElseThrow();
        assertThat(firstSaved.getIsDefault()).as("첫 계좌는 기본 계좌여야 합니다 (D-SA-003)").isTrue();
        createdAccounts.add(firstSaved);

        // When: 두 번째 계좌를 isDefault=false로 등록함
        MvcResult result = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", "999-888-777666",
                "accountName", "일반 계좌",
                "isDefault", false
        ));

        // Then: 201 Created + 두 번째 계좌는 요청한 isDefault=false가 유지됨 (D-SA-003 경계)
        int status = result.getResponse().getStatus();
        assertThat(status).as("두 번째 계좌 등록은 201이어야 합니다").isEqualTo(201);
        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(response.get("isDefault"))
                .as("두 번째 계좌부터는 요청의 isDefault=false가 유지되어야 합니다 (D-SA-003 경계)")
                .isEqualTo(false);
        SecuritiesAccount secondSaved = securitiesAccountRepository.findById(
                ((Number) response.get("id")).longValue()).orElseThrow();
        assertThat(secondSaved.getIsDefault()).isFalse();
        createdAccounts.add(secondSaved);
    }

    @Test
    void registerSecuritiesAccountWithMissingRequiredFieldsReturns400ValidationError() throws Exception {
        // Given: 인증된 계정이 투자자/증권사를 소유하고 있음 (AC-002)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long securitiesFirmId = registerFirm();

        // When/Then: investorId 누락 시 400 + VALIDATION_ERROR (AC-002)
        MvcResult noInvestor = registerSecuritiesAccount(Map.of(
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER
        ));
        assertThat(noInvestor.getResponse().getStatus())
                .as("investorId 누락 시 400 Bad Request를 반환해야 합니다 (AC-002)").isEqualTo(400);
        Map<String, Object> noInvestorBody = objectMapper.readValue(utf8Body(noInvestor), Map.class);
        assertThat(noInvestorBody.get("error"))
                .as("investorId 누락 시 에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
                .isEqualTo("VALIDATION_ERROR");

        // When/Then: securitiesFirmId 누락 시 400 + VALIDATION_ERROR (AC-002)
        MvcResult noFirm = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "accountNumber", ACCOUNT_NUMBER
        ));
        assertThat(noFirm.getResponse().getStatus())
                .as("securitiesFirmId 누락 시 400 Bad Request를 반환해야 합니다 (AC-002)").isEqualTo(400);
        Map<String, Object> noFirmBody = objectMapper.readValue(utf8Body(noFirm), Map.class);
        assertThat(noFirmBody.get("error"))
                .as("securitiesFirmId 누락 시 에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
                .isEqualTo("VALIDATION_ERROR");

        // When/Then: accountNumber 누락 시 400 + VALIDATION_ERROR (AC-002)
        MvcResult noNumber = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId
        ));
        assertThat(noNumber.getResponse().getStatus())
                .as("accountNumber 누락 시 400 Bad Request를 반환해야 합니다 (AC-002)").isEqualTo(400);
        Map<String, Object> noNumberBody = objectMapper.readValue(utf8Body(noNumber), Map.class);
        assertThat(noNumberBody.get("error"))
                .as("accountNumber 누락 시 에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
                .isEqualTo("VALIDATION_ERROR");

        // And: 레코드가 생성되지 않음 (AC-002)
        assertThat(securitiesAccountRepository.findByInvestorId(investorId))
                .as("검증 실패 시 증권계좌 레코드가 생성되지 않아야 합니다 (AC-002)").isEmpty();
    }

    @Test
    void registerSecuritiesAccountWithUnknownInvestorReturns400() throws Exception {
        // Given: 인증된 계정이 존재하고, 존재하지 않는 투자자 ID가 전달됨 (AC-003)
        signupAndLogin();
        Long securitiesFirmId = registerFirm();

        var request = Map.of(
                "investorId", 99999999L,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER
        );

        // When: POST /securities-accounts 요청을 보냄
        MvcResult result = registerSecuritiesAccount(request);

        // Then: 400 Bad Request (AC-003)
        int status = result.getResponse().getStatus();
        assertThat(status).as("존재하지 않는 투자자로 등록 시 400 Bad Request를 반환해야 합니다 (AC-003)").isEqualTo(400);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-003)").isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    void registerSecuritiesAccountWithUnknownSecuritiesFirmReturns400() throws Exception {
        // Given: 인증된 계정이 유효한 투자자를 소유하고, 존재하지 않는 증권사 ID가 전달됨 (AC-004)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();

        var request = Map.of(
                "investorId", investorId,
                "securitiesFirmId", 99999999L,
                "accountNumber", ACCOUNT_NUMBER
        );

        // When: POST /securities-accounts 요청을 보냄
        MvcResult result = registerSecuritiesAccount(request);

        // Then: 400 Bad Request (AC-004)
        int status = result.getResponse().getStatus();
        assertThat(status).as("존재하지 않는 증권사로 등록 시 400 Bad Request를 반환해야 합니다 (AC-004)").isEqualTo(400);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-004)").isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    void registerSecuritiesAccountWithInvestorOwnedByOtherAccountReturns403() throws Exception {
        // Given: 인증된 계정 A가 존재하고, 다른 계정 B의 투자자 ID가 알려져 있음 (AC-005)
        signupAndLogin();
        Long otherInvestorId = createOtherAccountInvestor();
        Long securitiesFirmId = registerFirm();

        var request = Map.of(
                "investorId", otherInvestorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER
        );

        // When: 계정 A가 계정 B의 투자자 ID로 POST /securities-accounts 요청을 보냄
        MvcResult result = registerSecuritiesAccount(request);

        // Then: 403 Forbidden + FORBIDDEN (AC-005)
        int status = result.getResponse().getStatus();
        assertThat(status).as("다른 계정 소유 투자자로 등록 시 403 Forbidden을 반환해야 합니다 (AC-005)").isEqualTo(403);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다 (AC-005)").isEqualTo("FORBIDDEN");
        assertThat(body.get("message")).isNotNull();

        // And: 레코드가 생성되지 않음 (AC-005)
        assertThat(securitiesAccountRepository.findByInvestorId(otherInvestorId))
                .as("소유권 없는 요청은 증권계좌를 생성하면 안 됩니다 (AC-005)").isEmpty();
    }

    @Test
    void listSecuritiesAccountsReturnsOnlyGivenInvestorsAccounts() throws Exception {
        // Given: 특정 투자자에게 2개의 증권계좌가 연결되어 있고, 같은 계정의 다른 투자자에게도 1개가 있음 (AC-006)
        signupAndLogin();
        Long securitiesFirmId = registerFirm();
        String firmName = securitiesFirmRepository.findById(securitiesFirmId).orElseThrow().getName();
        Long investorId = registerInvestorAndGetId();
        Long secondInvestorId = registerInvestorAndGetId();

        MvcResult first = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "accountName", "기본 계좌"));
        assertThat(first.getResponse().getStatus())
                .as("테스트 사전 준비: 첫 계좌 등록은 201이어야 합니다").isEqualTo(201);
        createdAccounts.add(securitiesAccountRepository.findById(
                ((Number) objectMapper.readValue(utf8Body(first), Map.class).get("id")).longValue()).orElseThrow());

        MvcResult second = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", "999-888-777666",
                "accountName", "일반 계좌",
                "isDefault", false));
        assertThat(second.getResponse().getStatus())
                .as("테스트 사전 준비: 두 번째 계좌 등록은 201이어야 합니다").isEqualTo(201);
        createdAccounts.add(securitiesAccountRepository.findById(
                ((Number) objectMapper.readValue(utf8Body(second), Map.class).get("id")).longValue()).orElseThrow());

        MvcResult otherInvestor = registerSecuritiesAccount(Map.of(
                "investorId", secondInvestorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", "555-444-333222",
                "accountName", "다른 투자자 계좌",
                "isDefault", false));
        assertThat(otherInvestor.getResponse().getStatus())
                .as("테스트 사전 준비: 다른 투자자의 계좌 등록은 201이어야 합니다").isEqualTo(201);
        createdAccounts.add(securitiesAccountRepository.findById(
                ((Number) objectMapper.readValue(utf8Body(otherInvestor), Map.class).get("id")).longValue()).orElseThrow());

        // When: GET /securities-accounts?investorId={id} 요청을 보냄 (AC-006)
        MvcResult result = mockMvc.perform(get("/securities-accounts")
                        .param("investorId", String.valueOf(investorId))
                        .session(session))
                .andReturn();

        // Then: 200 OK + 해당 투자자의 2개 계좌만 마스킹된 계좌번호와 함께 반환됨 (AC-006)
        assertThat(result.getResponse().getStatus())
                .as("목록 조회는 200 OK를 반환해야 합니다 (AC-006)").isEqualTo(200);
        List<Map<String, Object>> accounts = objectMapper.readValue(utf8Body(result), List.class);
        assertThat(accounts).as("해당 투자자의 계좌 2개가 반환되어야 합니다 (AC-006)").hasSize(2);

        for (Map<String, Object> account : accounts) {
            String maskedNumber = (String) account.get("accountNumber");
            assertThat(maskedNumber)
                    .as("계좌번호는 원본 평문이 아닌 마스킹된 값이어야 합니다 (AC-006)").isNotEqualTo(ACCOUNT_NUMBER);
            assertThat(maskedNumber).as("계좌번호는 마스킹 형태여야 합니다 (AC-006)").contains("*");
            assertThat(account.get("securitiesFirmName"))
                    .as("각 계좌에 증권사명이 포함되어야 합니다 (SF-3)").isEqualTo(firmName);
        }
        // And: target 투자자의 두 계좌(뒷 4자리 9012/7666)가 반환되고, 다른 투자자의 계좌(3222)는 포함되지 않음 (AC-006)
        List<String> maskedNumbers = accounts.stream().map(a -> (String) a.get("accountNumber")).toList();
        assertThat(maskedNumbers)
                .as("해당 투자자의 두 계좌가 마스킹된 계좌번호와 함께 반환되어야 합니다 (AC-006)")
                .anyMatch(n -> n.endsWith("9012"))
                .anyMatch(n -> n.endsWith("7666"));
        assertThat(maskedNumbers)
                .as("다른 투자자의 계좌는 목록에 포함되면 안 됩니다 (AC-006)")
                .noneMatch(n -> n != null && n.endsWith("3222"));
    }

    @Test
    void listSecuritiesAccountsWithInvestorOwnedByOtherAccountReturns403() throws Exception {
        // Given: 인증된 계정 A가 존재하고, 다른 계정 B의 투자자 ID가 알려져 있음 (목록 소유권 검증)
        signupAndLogin();
        Long otherInvestorId = createOtherAccountInvestor();

        // When: 계정 A가 계정 B의 투자자 ID로 GET /securities-accounts 요청을 보냄
        MvcResult result = mockMvc.perform(get("/securities-accounts")
                        .param("investorId", String.valueOf(otherInvestorId))
                        .session(session))
                .andReturn();

        // Then: 403 Forbidden + FORBIDDEN (목록 소유권 검증)
        assertThat(result.getResponse().getStatus())
                .as("다른 계정 소유 투자자의 계좌 목록 조회 시 403 Forbidden을 반환해야 합니다").isEqualTo(403);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다").isEqualTo("FORBIDDEN");
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    void listSecuritiesAccountsFiltersByStatusWithActiveDefault() throws Exception {
        // Given: 한 투자자에게 ACTIVE 계좌 1개와 INACTIVE 계좌 1개가 연결되어 있음
        signupAndLogin();
        Long securitiesFirmId = registerFirm();
        Long investorId = registerInvestorAndGetId();

        MvcResult active = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER));
        assertThat(active.getResponse().getStatus())
                .as("테스트 사전 준비: 첫 계좌 등록은 201이어야 합니다").isEqualTo(201);

        MvcResult inactive = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", "999-888-777666",
                "isDefault", false));
        assertThat(inactive.getResponse().getStatus())
                .as("테스트 사전 준비: 두 번째 계좌 등록은 201이어야 합니다").isEqualTo(201);
        SecuritiesAccount inactiveAccount = securitiesAccountRepository.findById(
                ((Number) objectMapper.readValue(utf8Body(inactive), Map.class).get("id")).longValue()).orElseThrow();
        inactiveAccount.setStatus("INACTIVE");
        inactiveAccount.setUpdatedAt(LocalDateTime.now());
        securitiesAccountRepository.save(inactiveAccount);
        createdAccounts.add(inactiveAccount);

        // When: status 미지정(기본 ACTIVE) 조회 시 ACTIVE 계좌만 반환됨
        MvcResult defaultResult = mockMvc.perform(get("/securities-accounts")
                        .param("investorId", String.valueOf(investorId))
                        .session(session))
                .andReturn();
        assertThat(defaultResult.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> defaultAccounts = objectMapper.readValue(utf8Body(defaultResult), List.class);
        assertThat(defaultAccounts).as("status 기본값(ACTIVE)은 ACTIVE 계좌만 반환해야 합니다").hasSize(1);

        // When: status=INACTIVE 조회 시 INACTIVE 계좌만 반환됨
        MvcResult inactiveResult = mockMvc.perform(get("/securities-accounts")
                        .param("investorId", String.valueOf(investorId))
                        .param("status", "INACTIVE")
                        .session(session))
                .andReturn();
        assertThat(inactiveResult.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> inactiveAccounts = objectMapper.readValue(utf8Body(inactiveResult), List.class);
        assertThat(inactiveAccounts).as("status=INACTIVE는 INACTIVE 계좌만 반환해야 합니다").hasSize(1);
        assertThat(((String) inactiveAccounts.get(0).get("accountNumber")))
                .as("status=INACTIVE는 INACTIVE 계좌를 반환해야 합니다").endsWith("7666");

        // And: status=BOTH 조회 시 두 계좌 모두 반환됨
        MvcResult bothResult = mockMvc.perform(get("/securities-accounts")
                        .param("investorId", String.valueOf(investorId))
                        .param("status", "BOTH")
                        .session(session))
                .andReturn();
        assertThat(bothResult.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> bothAccounts = objectMapper.readValue(utf8Body(bothResult), List.class);
        assertThat(bothAccounts).as("status=BOTH는 ACTIVE와 INACTIVE 계좌를 모두 반환해야 합니다").hasSize(2);
    }

    @Test
    void getSecuritiesAccountReturnsOwnedAccountWithMaskedNumberAndFirmName() throws Exception {
        // Given: 인증된 계정이 특정 증권계좌를 소유하고 있음 (AC-007)
        signupAndLogin();
        Long securitiesFirmId = registerFirm();
        String firmName = securitiesFirmRepository.findById(securitiesFirmId).orElseThrow().getName();
        Long investorId = registerInvestorAndGetId();

        MvcResult registerResult = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "accountName", "개인 MTS 계좌"));
        assertThat(registerResult.getResponse().getStatus())
                .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다").isEqualTo(201);
        long accountId = ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id")).longValue();
        createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

        // When: GET /securities-accounts/{id} 요청을 보냄 (AC-007)
        MvcResult result = mockMvc.perform(get("/securities-accounts/{id}", accountId)
                        .session(session))
                .andReturn();

        // Then: 200 OK + 마스킹된 계좌번호와 증권사명 포함 (AC-007)
        assertThat(result.getResponse().getStatus())
                .as("본인 소유 계좌 상세 조회는 200 OK여야 합니다 (AC-007)").isEqualTo(200);
        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(((Number) response.get("id")).longValue()).isEqualTo(accountId);
        assertThat(((Number) response.get("investorId")).longValue()).isEqualTo(investorId);
        String masked = (String) response.get("accountNumber");
        assertThat(masked).as("계좌번호는 마스킹된 값이어야 합니다 (AC-007)")
                .isEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));
        assertThat(masked).as("계좌번호는 원본 평문을 노출하면 안 됩니다 (AC-007)").isNotEqualTo(ACCOUNT_NUMBER);
        assertThat(response.get("securitiesFirmName"))
                .as("응답에 증권사명이 포함되어야 합니다 (SF-3)").isEqualTo(firmName);
        assertThat(response.get("accountName")).isEqualTo("개인 MTS 계좌");
    }

    @Test
    void getSecuritiesAccountWithUnknownIdReturns404() throws Exception {
        // Given: 존재하지 않는 증권계좌 ID가 제공됨
        signupAndLogin();

        // When: GET /securities-accounts/{unknown_id} 요청을 보냄
        MvcResult result = mockMvc.perform(get("/securities-accounts/{id}", 99999999L)
                        .session(session))
                .andReturn();

        // Then: 404 Not Found + NOT_FOUND
        assertThat(result.getResponse().getStatus())
                .as("존재하지 않는 계좌 ID 조회 시 404 Not Found를 반환해야 합니다").isEqualTo(404);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    void getSecuritiesAccountOwnedByOtherAccountReturns403() throws Exception {
        // Given: 인증된 계정 A가 존재하고, 다른 계정 B가 소유한 증권계좌가 등록되어 있음
        signupAndLogin();
        Long otherInvestorId = createOtherAccountInvestor();
        Long securitiesFirmId = registerFirm();

        LocalDateTime now = LocalDateTime.now();
        SecuritiesAccount otherAccount = securitiesAccountRepository.save(SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .isDefault(true)
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        createdAccounts.add(otherAccount);

        // When: 계정 A가 계정 B의 증권계좌 ID로 GET /securities-accounts/{id} 요청을 보냄
        MvcResult result = mockMvc.perform(get("/securities-accounts/{id}", otherAccount.getId())
                        .session(session))
                .andReturn();

        // Then: 403 Forbidden + FORBIDDEN
        assertThat(result.getResponse().getStatus())
                .as("다른 계정 소유 계좌 조회 시 403 Forbidden을 반환해야 합니다").isEqualTo(403);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다").isEqualTo("FORBIDDEN");
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    void getAccountNumberReturnsPlaintextForOwnedAccount() throws Exception {
        // Given: 인증된 계정이 해당 증권계좌의 소유 투자자를 보유하고 있음 (AC-008)
        signupAndLogin();
        Long securitiesFirmId = registerFirm();
        Long investorId = registerInvestorAndGetId();

        MvcResult registerResult = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER));
        assertThat(registerResult.getResponse().getStatus())
                .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다").isEqualTo(201);
        long accountId = ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id")).longValue();
        createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

        // When: GET /securities-accounts/{id}/account-number 요청을 보냄 (AC-008)
        MvcResult result = mockMvc.perform(get("/securities-accounts/{id}/account-number", accountId)
                        .session(session))
                .andReturn();

        // Then: 200 OK + 계좌번호 전체가 평문으로 반환됨 (마스킹되지 않음) (AC-008)
        assertThat(result.getResponse().getStatus())
                .as("본인 소유 계좌의 계좌번호 노출 조회는 200 OK여야 합니다 (AC-008)").isEqualTo(200);
        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(((Number) response.get("id")).longValue()).isEqualTo(accountId);
        assertThat(response.get("accountNumber"))
                .as("계좌번호 전체가 평문(마스킹 안 됨)으로 반환되어야 합니다 (AC-008)").isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.get("accountNumber"))
                .as("평문은 마스킹된 형태와 달라야 합니다 (AC-008)")
                .isNotEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));

        // And: 노출 시각(exposedAt, ISO 8601)이 포함됨 (AC-008)
        String exposedAt = (String) response.get("exposedAt");
        assertThat(exposedAt).as("응답에 exposedAt이 포함되어야 합니다 (AC-008)").isNotNull();
        assertThat(Instant.parse(exposedAt)).as("exposedAt은 ISO 8601 형식이어야 합니다 (AC-008)").isNotNull();
    }

    @Test
    void getAccountNumberOwnedByOtherAccountReturns403() throws Exception {
        // Given: 인증된 계정 A가 존재하고, 다른 계정 B의 증권계좌 ID가 알려져 있음 (AC-009)
        signupAndLogin();
        Long otherInvestorId = createOtherAccountInvestor();
        Long securitiesFirmId = registerFirm();
        String otherPlainNumber = "777-666-555444";

        LocalDateTime now = LocalDateTime.now();
        SecuritiesAccount otherAccount = securitiesAccountRepository.save(SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt(otherPlainNumber))
                .accountName("다른계정계좌")
                .isDefault(true)
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        createdAccounts.add(otherAccount);

        // When: 계정 A가 계정 B의 증권계좌 ID로 GET /securities-accounts/{id}/account-number 요청을 보냄 (AC-009)
        MvcResult result = mockMvc.perform(get("/securities-accounts/{id}/account-number", otherAccount.getId())
                        .session(session))
                .andReturn();

        // Then: 403 Forbidden + FORBIDDEN (AC-009)
        assertThat(result.getResponse().getStatus())
                .as("다른 계정 소유 계좌의 계좌번호 노출 요청 시 403 Forbidden을 반환해야 합니다 (AC-009)").isEqualTo(403);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다 (AC-009)").isEqualTo("FORBIDDEN");
        assertThat(body.get("message")).isNotNull();

        // And: 계좌번호 전체(평문)가 응답에 포함되지 않음 (AC-009)
        assertThat(utf8Body(result))
                .as("소유권 없는 요청 응답에 계좌번호 평문이 포함되면 안 됩니다 (AC-009)")
                .doesNotContain(otherPlainNumber);
    }

    @Test
    void getAccountNumberWithUnknownIdReturns404() throws Exception {
        // Given: 인증된 계정이 존재하고, 존재하지 않는 증권계좌 ID가 제공됨
        signupAndLogin();

        // When: GET /securities-accounts/{unknown_id}/account-number 요청을 보냄
        MvcResult result = mockMvc.perform(get("/securities-accounts/{id}/account-number", 99999999L)
                        .session(session))
                .andReturn();

        // Then: 404 Not Found + NOT_FOUND
        assertThat(result.getResponse().getStatus())
                .as("존재하지 않는 계좌의 계좌번호 노출 요청 시 404 Not Found를 반환해야 합니다").isEqualTo(404);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    void detailAndListStillShowSecuritiesFirmNameAfterFirmIsDeactivated() throws Exception {
        // Given: 계좌가 등록된 증권사가 이후 비활성화됨 (Master 수정 검증 - 표시용 조회는
        // deletedAt 필터를 쓰면 안 된다. architecture.md: "증권사 비활성화 시 기존 계좌는 유지")
        signupAndLogin();
        Long securitiesFirmId = registerFirm();
        Long investorId = registerInvestorAndGetId();

        MvcResult registerResult = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", securitiesFirmId,
                "accountNumber", ACCOUNT_NUMBER));
        assertThat(registerResult.getResponse().getStatus()).isEqualTo(201);
        Long accountId = ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id")).longValue();

        SecuritiesFirm firm = securitiesFirmRepository.findById(securitiesFirmId).orElseThrow();
        firm.setStatus("INACTIVE");
        firm.setDeletedAt(LocalDateTime.now());
        securitiesFirmRepository.save(firm);

        // When: 상세 조회
        MvcResult detailResult = mockMvc.perform(get("/securities-accounts/" + accountId)
                        .session(session))
                .andReturn();

        // Then: 200 OK이며 securitiesFirmName이 null이 아니라 비활성화 전 이름 그대로 표시됨
        assertThat(detailResult.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> detailBody = objectMapper.readValue(utf8Body(detailResult), Map.class);
        assertThat(detailBody.get("securitiesFirmName"))
                .as("증권사가 비활성화돼도 기존 연결 계좌의 securitiesFirmName은 null이 되면 안 됩니다")
                .isEqualTo(firm.getName());

        // And: 목록 조회에서도 동일하게 이름이 유지됨
        MvcResult listResult = mockMvc.perform(get("/securities-accounts")
                        .param("investorId", String.valueOf(investorId))
                        .session(session))
                .andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> listBody = objectMapper.readValue(utf8Body(listResult), List.class);
        assertThat(listBody).hasSize(1);
        assertThat(listBody.get(0).get("securitiesFirmName"))
                .as("목록 조회에서도 증권사 비활성화 후 이름이 유지되어야 합니다")
                .isEqualTo(firm.getName());
    }

    @Test
    void updateSecuritiesAccountWithValidRequestReturns200AndUpdatesAccountNameAndFirm() throws Exception {
        // Given: 인증된 계정이 소유한 증권계좌가 있고, 변경 대상 증권사는 ACTIVE 상태임 (SF-5)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long originalFirmId = registerFirm();
        Long newFirmId = registerFirm();

        MvcResult registerResult = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", originalFirmId,
                "accountNumber", ACCOUNT_NUMBER,
                "accountName", "개인 MTS 계좌"));
        assertThat(registerResult.getResponse().getStatus())
                .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다").isEqualTo(201);
        long accountId = ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id")).longValue();
        createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

        // When: PATCH /securities-accounts/{id}로 accountName과 securitiesFirmId를 변경함 (SF-5)
        MvcResult result = updateSecuritiesAccount(accountId, Map.of(
                "accountName", "변경된 계좌명",
                "securitiesFirmId", newFirmId));

        // Then: 200 OK + 변경된 accountName/securitiesFirmId가 응답에 반영됨 (SF-5)
        assertThat(result.getResponse().getStatus())
                .as("정상 수정 요청은 200 OK를 반환해야 합니다 (SF-5)").isEqualTo(200);
        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(response.get("accountName")).as("accountName이 변경되어야 합니다 (SF-5)").isEqualTo("변경된 계좌명");
        assertThat(((Number) response.get("securitiesFirmId")).longValue())
                .as("securitiesFirmId가 변경되어야 합니다 (SF-5)").isEqualTo(newFirmId);
        String masked = (String) response.get("accountNumber");
        assertThat(masked).as("계좌번호는 여전히 마스킹된 값이어야 합니다 (SF-5)")
                .isEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));

        // And: DB에도 변경 사항이 반영됨 (SF-5)
        SecuritiesAccount updated = securitiesAccountRepository.findById(accountId).orElseThrow();
        assertThat(updated.getAccountName()).isEqualTo("변경된 계좌명");
        assertThat(updated.getSecuritiesFirmId()).isEqualTo(newFirmId);
    }

    @Test
    void updateSecuritiesAccountIgnoresAccountNumberChangeInRequest() throws Exception {
        // Given: 인증된 계정이 소유한 증권계좌가 있음 (SF-5: accountNumber는 불변)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long firmId = registerFirm();

        MvcResult registerResult = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", firmId,
                "accountNumber", ACCOUNT_NUMBER));
        assertThat(registerResult.getResponse().getStatus())
                .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다").isEqualTo(201);
        long accountId = ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id")).longValue();
        createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

        // When: 요청 본문에 accountNumber 변경값을 포함해 PATCH 요청을 보냄 (SF-5)
        MvcResult result = updateSecuritiesAccount(accountId, Map.of(
                "accountName", "새 이름",
                "accountNumber", "000-000-000000"));

        // Then: 200 OK + accountNumber는 요청값과 무관하게 원래 값(마스킹) 그대로 유지됨 (SF-5)
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> response = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(response.get("accountNumber"))
                .as("accountNumber는 요청에 있어도 무시되고 기존 값이 유지되어야 합니다 (SF-5)")
                .isEqualTo(AccountNumberMasker.mask(ACCOUNT_NUMBER));

        // And: DB에 저장된 실제 계좌번호도 변경되지 않음 (SF-5)
        SecuritiesAccount updated = securitiesAccountRepository.findById(accountId).orElseThrow();
        assertThat(accountNumberEncryptor.decrypt(updated.getAccountNumber()))
                .as("DB의 계좌번호도 변경되면 안 됩니다 (SF-5)").isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    void updateSecuritiesAccountWithInactiveSecuritiesFirmReturns400() throws Exception {
        // Given: 인증된 계정이 소유한 증권계좌가 있고, 변경하려는 증권사가 INACTIVE 상태임 (SF-5)
        signupAndLogin();
        Long investorId = registerInvestorAndGetId();
        Long originalFirmId = registerFirm();
        Long inactiveFirmId = registerFirm();
        SecuritiesFirm inactiveFirm = securitiesFirmRepository.findById(inactiveFirmId).orElseThrow();
        inactiveFirm.setStatus("INACTIVE");
        securitiesFirmRepository.save(inactiveFirm);

        MvcResult registerResult = registerSecuritiesAccount(Map.of(
                "investorId", investorId,
                "securitiesFirmId", originalFirmId,
                "accountNumber", ACCOUNT_NUMBER));
        assertThat(registerResult.getResponse().getStatus())
                .as("테스트 사전 준비: 계좌 등록은 201이어야 합니다").isEqualTo(201);
        long accountId = ((Number) objectMapper.readValue(utf8Body(registerResult), Map.class).get("id")).longValue();
        createdAccounts.add(securitiesAccountRepository.findById(accountId).orElseThrow());

        // When: PATCH로 INACTIVE 증권사로 변경을 시도함 (SF-5)
        MvcResult result = updateSecuritiesAccount(accountId, Map.of("securitiesFirmId", inactiveFirmId));

        // Then: 400 Bad Request + VALIDATION_ERROR, 레코드는 변경되지 않음 (SF-5)
        assertThat(result.getResponse().getStatus())
                .as("INACTIVE 증권사로 변경 시도 시 400 Bad Request를 반환해야 합니다 (SF-5)").isEqualTo(400);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 VALIDATION_ERROR여야 합니다 (SF-5)").isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message")).isNotNull();

        SecuritiesAccount unchanged = securitiesAccountRepository.findById(accountId).orElseThrow();
        assertThat(unchanged.getSecuritiesFirmId())
                .as("INACTIVE 증권사 변경 시도는 반영되면 안 됩니다 (SF-5)").isEqualTo(originalFirmId);
    }

    @Test
    void updateSecuritiesAccountOwnedByOtherAccountReturns403() throws Exception {
        // Given: 인증된 계정 A가 존재하고, 다른 계정 B가 소유한 증권계좌가 등록되어 있음 (SF-5)
        signupAndLogin();
        Long otherInvestorId = createOtherAccountInvestor();
        Long securitiesFirmId = registerFirm();

        LocalDateTime now = LocalDateTime.now();
        SecuritiesAccount otherAccount = securitiesAccountRepository.save(SecuritiesAccount.builder()
                .investorId(otherInvestorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt("777-666-555444"))
                .accountName("다른계정계좌")
                .isDefault(true)
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        createdAccounts.add(otherAccount);

        // When: 계정 A가 계정 B의 증권계좌 ID로 PATCH /securities-accounts/{id} 요청을 보냄 (SF-5)
        MvcResult result = updateSecuritiesAccount(otherAccount.getId(), Map.of("accountName", "탈취 시도"));

        // Then: 403 Forbidden + FORBIDDEN, 레코드는 변경되지 않음 (SF-5)
        assertThat(result.getResponse().getStatus())
                .as("다른 계정 소유 계좌 수정 시도 시 403 Forbidden을 반환해야 합니다 (SF-5)").isEqualTo(403);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다 (SF-5)").isEqualTo("FORBIDDEN");
        assertThat(body.get("message")).isNotNull();

        SecuritiesAccount unchanged = securitiesAccountRepository.findById(otherAccount.getId()).orElseThrow();
        assertThat(unchanged.getAccountName())
                .as("소유권 없는 요청은 계좌를 변경하면 안 됩니다 (SF-5)").isEqualTo("다른계정계좌");
    }

    @Test
    void updateSecuritiesAccountWithUnknownIdReturns404() throws Exception {
        // Given: 존재하지 않는 증권계좌 ID가 제공됨 (SF-5)
        signupAndLogin();

        // When: PATCH /securities-accounts/{unknown_id} 요청을 보냄 (SF-5)
        MvcResult result = updateSecuritiesAccount(99999999L, Map.of("accountName", "새 이름"));

        // Then: 404 Not Found + NOT_FOUND (SF-5)
        assertThat(result.getResponse().getStatus())
                .as("존재하지 않는 계좌 수정 시도 시 404 Not Found를 반환해야 합니다 (SF-5)").isEqualTo(404);
        Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
        assertThat(body.get("error")).as("에러 코드는 NOT_FOUND여야 합니다 (SF-5)").isEqualTo("NOT_FOUND");
        assertThat(body.get("message")).isNotNull();
    }
}