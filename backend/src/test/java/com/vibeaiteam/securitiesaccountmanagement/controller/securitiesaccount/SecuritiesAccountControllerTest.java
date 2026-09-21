package com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
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
}