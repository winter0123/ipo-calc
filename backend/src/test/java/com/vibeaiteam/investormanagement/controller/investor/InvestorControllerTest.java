package com.vibeaiteam.investormanagement.controller.investor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AC 인용: docs/spec/investor-management.md
 * - AC-001: 투자자 등록 성공 — 인증된 계정이 존재하고 유효한 표시 이름이 제공되면,
 *           POST /investors 요청 시 201 Created + 신규 투자자 레코드 생성,
 *           응답에 투자자 id/표시 이름/account_id/status=ACTIVE/deleted_at=null 포함
 * - AC-002: 투자자 등록 실패 (빈 이름) — display_name이 빈 문자열이면
 *           400 Bad Request + VALIDATION_ERROR, 투자자 레코드 미생성
 * - AC-003: 투자자 등록 실패 (계정 미인증) — 인증되지 않은 요청이면
 *           401 Unauthorized, 투자자 레코드 미생성
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class InvestorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InvestorRepository investorRepository;

    private String testEmail;
    private String testPassword;
    private String testName;
    private MockHttpSession session;
    private Long accountId;

    @BeforeEach
    void setUp() {
        long nonce = ThreadLocalRandom.current().nextLong();
        testEmail = "investor-owner-" + nonce + "@example.com";
        testPassword = "securePass123!";
        testName = "투자자테스터";
        session = new MockHttpSession();
    }

    @AfterEach
    void tearDown() {
        // 테스트에서 생성한 투자자/계정 정리 - email 중복 충돌 방지 (FK: investors.account_id → accounts.id)
        Account account = accountRepository.findByEmail(testEmail);
        if (account != null) {
            Long acctId = account.getId();
            List<Investor> investors = investorRepository.findAll().stream()
                    .filter(i -> acctId.equals(i.getAccountId()))
                    .collect(Collectors.toList());
            investorRepository.deleteAll(investors);
            accountRepository.delete(account);
        }
    }

    private void signupAndLogin() throws Exception {
        // 회원가입(/accounts) → 자동 로그인 세션 생성 후 로그인(/auth/login)으로 세션 확정
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

    private List<Investor> investorsOfAccount() {
        return investorRepository.findAll().stream()
                .filter(i -> accountId != null && accountId.equals(i.getAccountId()))
                .collect(Collectors.toList());
    }

    private String utf8Body(MvcResult result) {
        // MockMvc는 응답 헤더에 charset이 없는 application/json을 ISO-8859-1로 디코딩하므로
        // 바이트를 UTF-8로 직접 읽어 한글/특수문자 깨짐을 방지한다.
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void registerInvestorWithValidRequestReturns201AndResponseFields() throws Exception {
        // Given: 인증된 계정이 존재하고 유효한 표시 이름이 제공됨 (AC-001)
        signupAndLogin();

        var request = Map.of(
                "displayName", "홍길동",
                "relationship", "SELF"
        );

        // When: POST /investors 요청을 보냄
        MvcResult result = mockMvc.perform(post("/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(session))
                .andReturn();

        // Then: 201 Created 응답을 반환하고 신규 투자자 레코드가 생성됨
        int status = result.getResponse().getStatus();
        assertThat(status).as("유효한 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);

        String responseBody = utf8Body(result);
        var response = objectMapper.readValue(responseBody, Map.class);
        assertThat(response.get("id")).as("응답에 투자자 id가 포함되어야 합니다").isNotNull();
        assertThat(response.get("displayName")).as("응답에 표시 이름이 포함되어야 합니다").isEqualTo("홍길동");
        assertThat(((Number) response.get("accountId")).longValue())
                .as("응답의 account_id는 현재 인증된 계정 ID여야 합니다").isEqualTo(accountId);
        assertThat(response.get("status")).as("status는 ACTIVE여야 합니다 (AC-001)").isEqualTo("ACTIVE");
        assertThat(response.get("deletedAt")).as("deleted_at은 null이어야 합니다 (AC-001)").isNull();
        assertThat(response.get("createdAt")).as("created_at이 포함되어야 합니다").isNotNull();
        assertThat(response.get("updatedAt")).as("updated_at이 포함되어야 합니다").isNotNull();

        // And: DB에 신규 투자자 레코드가 생성됨
        List<Investor> investors = investorsOfAccount();
        assertThat(investors).as("계정 소유 투자자 레코드가 1건 생성되어야 합니다").hasSize(1);
        assertThat(investors.get(0).getDisplayName()).isEqualTo("홍길동");
        assertThat(investors.get(0).getRelationship()).isEqualTo("SELF");
        assertThat(investors.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(investors.get(0).getDeletedAt()).isNull();
    }

    @Test
    void registerInvestorWithEmptyDisplayNameReturns400ValidationError() throws Exception {
        // Given: 인증된 계정이 존재함 (AC-002)
        signupAndLogin();

        var request = Map.of(
                "displayName", "   ",
                "relationship", "SELF"
        );

        // When: POST /investors 요청에 display_name이 빈 문자열(공백만)인 경우
        MvcResult result = mockMvc.perform(post("/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(session))
                .andReturn();

        // Then: 400 Bad Request + VALIDATION_ERROR
        int status = result.getResponse().getStatus();
        assertThat(status).as("빈 표시 이름 요청 시 400 Bad Request를 반환해야 합니다 (AC-002)").isEqualTo(400);

        var responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(responseMap.get("error")).as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)").isEqualTo("VALIDATION_ERROR");
        assertThat(responseMap.get("message")).isNotNull();

        // And: 투자자 레코드가 생성되지 않음
        assertThat(investorsOfAccount()).as("검증 실패 시 투자자 레코드가 생성되지 않아야 합니다 (AC-002)").isEmpty();
    }

    @Test
    void registerInvestorWithDisplayNameOver50CharsReturns400ValidationError() throws Exception {
        // Given: 인증된 계정이 존재함 (spec: display_name 1-50자 제한)
        signupAndLogin();

        String over50 = "가".repeat(51);
        var request = Map.of(
                "displayName", over50
        );

        // When: POST /investors 요청에 50자 초과 표시 이름이 제공됨
        MvcResult result = mockMvc.perform(post("/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(session))
                .andReturn();

        // Then: 400 Bad Request + VALIDATION_ERROR, 레코드 미생성
        int status = result.getResponse().getStatus();
        assertThat(status).as("50자 초과 표시 이름 요청 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

        var responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(responseMap.get("error")).as("에러 코드는 VALIDATION_ERROR여야 합니다").isEqualTo("VALIDATION_ERROR");
        assertThat(investorsOfAccount()).as("50자 초과 시 투자자 레코드가 생성되지 않아야 합니다").isEmpty();
    }

    @Test
    void registerInvestorWithoutAuthenticationReturns401() throws Exception {
        // Given: 인증되지 않은 요청이 들어옴 (세션 없음) (AC-003)

        var request = Map.of(
                "displayName", "홍길동"
        );

        // When: POST /investors 요청을 보냄
        MvcResult result = mockMvc.perform(post("/investors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // Then: 401 Unauthorized 응답을 반환함 (AC-003)
        int status = result.getResponse().getStatus();
        assertThat(status).as("인증되지 않은 요청은 401 Unauthorized를 반환해야 합니다 (AC-003)").isEqualTo(401);
    }
}