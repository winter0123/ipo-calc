package com.ipocalc.investormanagement.controller.investor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.accountauthentication.AccountAuthenticationApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.testsupport.AuthTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * AC 인용: docs/spec/investor-management.md - AC-001: 투자자 등록 성공 — 인증된 계정이 존재하고 유효한 표시 이름이 제공되면, POST
 * /investors 요청 시 201 Created + 신규 투자자 레코드 생성, 응답에 투자자 id/표시
 * 이름/account_id/status=ACTIVE/deleted_at=null 포함 - AC-002: 투자자 등록 실패 (빈 이름) — display_name이 빈 문자열이면
 * 400 Bad Request + VALIDATION_ERROR, 투자자 레코드 미생성 - AC-003: 투자자 등록 실패 (계정 미인증) — 인증되지 않은 요청이면 401
 * Unauthorized, 투자자 레코드 미생성 - AC-004: 투자자 목록 조회 (본인 계정 소유만) — GET /investors 요청 시 본인 계정 소유 투자자만 반환,
 * 다른 계정의 투자자는 포함되지 않음 - AC-005: 다른 계정 투자자 상세 조회 — 계정 A가 계정 B 소유 투자자의 ID로 GET /investors/{id} 요청 시
 * 403 Forbidden, 계정 B의 투자자 정보를 반환하지 않음 - AC-006: 투자자 상세 조회 성공 — 본인 소유 투자자의 GET /investors/{id} 요청 시
 * 200 OK + 상세 정보 반환 - AC-007: 투자자 수정 성공 — PATCH /investors/{id} 요청 시 200 OK + 수정된 표시 이름과 updatedAt
 * 반영 - AC-008: 투자자 수정 실패 (빈 이름) — displayName이 빈 문자열이면 400 Bad Request + VALIDATION_ERROR, 투자자 정보
 * 변경되지 않음.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class InvestorControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private InvestorRepository investorRepository;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;
  private Long accountId;
  private String otherEmail;
  private Long otherAccountId;

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
    cleanupAccount(testEmail);
    cleanupAccount(otherEmail);
  }

  private void cleanupAccount(String email) {
    Account account = accountRepository.findByEmail(email);
    if (account != null) {
      Long acctId = account.getId();
      List<Investor> investors =
          investorRepository.findAll().stream()
              .filter(i -> acctId.equals(i.getAccountId()))
              .collect(Collectors.toList());
      investorRepository.deleteAll(investors);
      investorRepository.deleteAll(investorRepository.findByAccountId(account.getId()));
      accountRepository.delete(account);
    }
  }

  private void signupAndLogin() throws Exception {
    AuthTestSupport.signup(mockMvc, objectMapper, session, testEmail, testPassword, testName);
    accountId = accountRepository.findByEmail(testEmail).getId();
    // 이 클래스는 POST /investors 동작을 독립 검증하므로 가입 시 자동 생성된 본인 투자자를 제거한다.
    investorRepository.deleteAll(investorRepository.findByAccountId(accountId));
    AuthTestSupport.login(mockMvc, objectMapper, session, testEmail, testPassword);
  }

  private List<Investor> investorsOfAccount() {
    return investorRepository.findAll().stream()
        .filter(i -> accountId != null && accountId.equals(i.getAccountId()))
        .collect(Collectors.toList());
  }

  private void registerInvestor(String displayName, String relationship) throws Exception {
    // POST /investors를 통해 본인 계정(세션) 소유 투자자를 등록한다 (AC-001 성공 경로 활용).
    var request =
        Map.of(
            "displayName", displayName,
            "relationship", relationship);
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("테스트 사전 준비: 투자자 등록은 201이어야 합니다").isEqualTo(201);
  }

  private Long createOtherAccountInvestor() {
    // 다른 계정(계정 B)을 직접 생성하고 그 계정 소유 투자자 1명을 저장한다.
    long nonce = ThreadLocalRandom.current().nextLong();
    otherEmail = "other-investor-owner-" + nonce + "@example.com";
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
    otherAccountId = other.getId();
    Investor saved =
        investorRepository.save(
            Investor.builder()
                .accountId(otherAccountId)
                .displayName("다른계정투자자")
                .relationship("OTHER")
                .status(RecordStatus.ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return saved.getId();
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

    var request =
        Map.of(
            "displayName", "홍길동",
            "relationship", "SELF");

    // When: POST /investors 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
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
        .as("응답의 account_id는 현재 인증된 계정 ID여야 합니다")
        .isEqualTo(accountId);
    assertThat(response.get("status")).as("status는 ACTIVE여야 합니다 (AC-001)").isEqualTo("ACTIVE");
    assertThat(response.get("deletedAt")).as("deleted_at은 null이어야 합니다 (AC-001)").isNull();
    assertThat(response.get("createdAt")).as("created_at이 포함되어야 합니다").isNotNull();
    assertThat(response.get("updatedAt")).as("updated_at이 포함되어야 합니다").isNotNull();

    // And: DB에 신규 투자자 레코드가 생성됨
    List<Investor> investors = investorsOfAccount();
    assertThat(investors).as("계정 소유 투자자 레코드가 1건 생성되어야 합니다").hasSize(1);
    assertThat(investors.get(0).getDisplayName()).isEqualTo("홍길동");
    assertThat(investors.get(0).getRelationship()).isEqualTo("SELF");
    assertThat(investors.get(0).getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(investors.get(0).getDeletedAt()).isNull();
  }

  @Test
  void registerInvestorWithFriendRelationship() throws Exception {
    signupAndLogin();

    registerInvestor("친구투자자", "FRIEND");

    assertThat(investorsOfAccount())
        .singleElement()
        .satisfies(investor -> {
          assertThat(investor.getDisplayName()).isEqualTo("친구투자자");
          assertThat(investor.getRelationship()).isEqualTo("FRIEND");
        });
  }

  @Test
  void registerInvestorWithEmptyDisplayNameReturns400ValidationError() throws Exception {
    // Given: 인증된 계정이 존재함 (AC-002)
    signupAndLogin();

    var request =
        Map.of(
            "displayName", "   ",
            "relationship", "SELF");

    // When: POST /investors 요청에 display_name이 빈 문자열(공백만)인 경우
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 400 Bad Request + VALIDATION_ERROR
    int status = result.getResponse().getStatus();
    assertThat(status).as("빈 표시 이름 요청 시 400 Bad Request를 반환해야 합니다 (AC-002)").isEqualTo(400);

    var responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
        .isEqualTo("VALIDATION_ERROR");
    assertThat(responseMap.get("message")).isNotNull();

    // And: 투자자 레코드가 생성되지 않음
    assertThat(investorsOfAccount()).as("검증 실패 시 투자자 레코드가 생성되지 않아야 합니다 (AC-002)").isEmpty();
  }

  @Test
  void registerInvestorWithDisplayNameOver50CharsReturns400ValidationError() throws Exception {
    // Given: 인증된 계정이 존재함 (spec: display_name 1-50자 제한)
    signupAndLogin();

    String over50 = "가".repeat(51);
    var request = Map.of("displayName", over50);

    // When: POST /investors 요청에 50자 초과 표시 이름이 제공됨
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 400 Bad Request + VALIDATION_ERROR, 레코드 미생성
    int status = result.getResponse().getStatus();
    assertThat(status).as("50자 초과 표시 이름 요청 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

    var responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다")
        .isEqualTo("VALIDATION_ERROR");
    assertThat(investorsOfAccount()).as("50자 초과 시 투자자 레코드가 생성되지 않아야 합니다").isEmpty();
  }

  @Test
  void registerInvestorWithoutAuthenticationReturns401() throws Exception {
    // Given: 인증되지 않은 요청이 들어옴 (세션 없음) (AC-003)

    var request = Map.of("displayName", "홍길동");

    // When: POST /investors 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();

    // Then: 401 Unauthorized 응답을 반환함 (AC-003)
    int status = result.getResponse().getStatus();
    assertThat(status).as("인증되지 않은 요청은 401 Unauthorized를 반환해야 합니다 (AC-003)").isEqualTo(401);
  }

  @Test
  void listReturnsOnlyOwnedInvestorsExcludingOtherAccount() throws Exception {
    // Given: 인증된 계정(본인)이 투자자 2명을 소유하고, 다른 계정(B)도 투자자 1명을 소유함 (AC-004)
    signupAndLogin();
    registerInvestor("홍길동", "SELF");
    registerInvestor("김영희", "SPOUSE");
    Long otherInvestorId = createOtherAccountInvestor();

    // When: GET /investors 요청을 보냄
    MvcResult result = mockMvc.perform(get("/investors").session(session)).andReturn();

    // Then: 200 OK + 본인 계정 소유 투자자 2명만 반환되고 다른 계정의 투자자는 포함되지 않음
    assertThat(result.getResponse().getStatus()).as("목록 조회 시 200 OK여야 합니다 (AC-004)").isEqualTo(200);
    List<Map<String, Object>> body = objectMapper.readValue(utf8Body(result), List.class);
    assertThat(body).as("본인 계정 소유 투자자 2명만 반환되어야 합니다 (AC-004)").hasSize(2);
    for (Map<String, Object> investor : body) {
      long accountIdInResponse = ((Number) investor.get("accountId")).longValue();
      assertThat(accountIdInResponse)
          .as("응답에 포함된 모든 투자자는 본인 계정 소유여야 합니다 (AC-004)")
          .isEqualTo(accountId);
      assertThat(((Number) investor.get("id")).longValue())
          .as("다른 계정의 투자자는 목록에 포함되지 않아야 합니다 (AC-004)")
          .isNotEqualTo(otherInvestorId);
    }
  }

  @Test
  void getInvestorOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 계정 B의 투자자 ID가 알려져 있음 (AC-005)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();

    // When: 계정 A가 GET /investors/{accountB_investor_id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(get("/investors/" + otherInvestorId).session(session)).andReturn();

    // Then: 403 Forbidden 응답을 반환하고 계정 B의 투자자 정보를 반환하지 않음 (AC-005)
    int status = result.getResponse().getStatus();
    assertThat(status).as("다른 계정 투자자 상세 조회 시 403 Forbidden이어야 합니다 (AC-005)").isEqualTo(403);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 FORBIDDEN이어야 합니다 (AC-005)")
        .isEqualTo("FORBIDDEN");
    assertThat(responseMap.get("message")).isNotNull();
    assertThat(responseMap.get("displayName")).as("계정 B의 투자자 정보가 응답에 포함되면 안 됩니다 (AC-005)").isNull();
    assertThat(responseMap.get("accountId"))
        .as("계정 B의 accountId가 응답에 포함되면 안 됩니다 (AC-005)")
        .isNull();
  }

  @Test
  void getOwnInvestorReturns200WithDetails() throws Exception {
    // Given: 인증된 계정이 특정 투자자를 소유하고 있음 (AC-006)
    signupAndLogin();
    MvcResult created =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "displayName", "홍길동",
                                "relationship", "SELF")))
                    .session(session))
            .andReturn();
    Long investorId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();

    // When: GET /investors/{valid_investor_id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(get("/investors/" + investorId).session(session)).andReturn();

    // Then: 200 OK 응답을 반환하고 해당 투자자의 상세 정보를 반환함 (AC-006)
    int status = result.getResponse().getStatus();
    assertThat(status).as("본인 소유 투자자 상세 조회 시 200 OK여야 합니다 (AC-006)").isEqualTo(200);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(((Number) body.get("id")).longValue()).isEqualTo(investorId);
    assertThat(body.get("displayName")).isEqualTo("홍길동");
    assertThat(body.get("relationship")).isEqualTo("SELF");
    assertThat(((Number) body.get("accountId")).longValue()).isEqualTo(accountId);
    assertThat(body.get("status")).isEqualTo("ACTIVE");
    assertThat(body.get("deletedAt")).isNull();
    assertThat(body.get("createdAt")).isNotNull();
    assertThat(body.get("updatedAt")).isNotNull();
  }

  @Test
  void getInvestorWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 계정이 존재함 (spec: 존재하지 않는 투자자는 404 NOT_FOUND)
    signupAndLogin();

    // When: 존재하지 않는 투자자 ID로 GET /investors/{id} 요청을 보냄
    MvcResult result = mockMvc.perform(get("/investors/99999999").session(session)).andReturn();

    // Then: 404 Not Found + NOT_FOUND 오류
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 투자자 상세 조회 시 404여야 합니다").isEqualTo(404);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
  }

  @Test
  void listWithStatusBothReturnsActiveAndInactive() throws Exception {
    // Given: 본인 계정이 ACTIVE 투자자 1명(등록)과 INACTIVE 투자자 1명(직접 저장)을 소유함
    signupAndLogin();
    registerInvestor("홍길동", "SELF");
    LocalDateTime now = LocalDateTime.now();
    investorRepository.save(
        Investor.builder()
            .accountId(accountId)
            .displayName("김영희")
            .relationship("SPOUSE")
            .status(RecordStatus.INACTIVE)
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: GET /investors?status=BOTH 요청을 보냄
    MvcResult result =
        mockMvc.perform(get("/investors").param("status", "BOTH").session(session)).andReturn();

    // Then: 200 OK + ACTIVE/INACTIVE 투자자 모두 반환 (status=BOTH)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = objectMapper.readValue(utf8Body(result), List.class);
    assertThat(body).as("status=BOTH 시 ACTIVE와 INACTIVE 모두 반환되어야 합니다").hasSize(2);
    assertThat(body)
        .extracting(inv -> inv.get("status"))
        .as("ACTIVE와 INACTIVE가 모두 포함되어야 합니다")
        .containsExactlyInAnyOrder("ACTIVE", "INACTIVE");
  }

  @Test
  void listWithDefaultStatusReturnsOnlyActive() throws Exception {
    // Given: 본인 계정이 ACTIVE 투자자 1명과 INACTIVE 투자자 1명을 소유함
    signupAndLogin();
    registerInvestor("홍길동", "SELF");
    LocalDateTime now = LocalDateTime.now();
    investorRepository.save(
        Investor.builder()
            .accountId(accountId)
            .displayName("김영희")
            .relationship("SPOUSE")
            .status(RecordStatus.INACTIVE)
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: status 파라미터 없이 GET /investors 요청을 보냄 (기본값 ACTIVE)
    MvcResult result = mockMvc.perform(get("/investors").session(session)).andReturn();

    // Then: 200 OK + ACTIVE 투자자만 반환됨
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> body = objectMapper.readValue(utf8Body(result), List.class);
    assertThat(body).as("기본 status(ACTIVE) 시 ACTIVE 투자자만 반환되어야 합니다").hasSize(1);
    assertThat(body.get(0).get("status")).isEqualTo("ACTIVE");
    assertThat(body.get(0).get("displayName")).isEqualTo("홍길동");
  }

  @Test
  void listWithInvalidStatusReturns400ValidationError() throws Exception {
    // Given: 인증된 계정이 존재함
    signupAndLogin();

    // When: 유효하지 않은 status 값으로 GET /investors 요청을 보냄
    MvcResult result =
        mockMvc.perform(get("/investors").param("status", "INVALID").session(session)).andReturn();

    // Then: 400 Bad Request + VALIDATION_ERROR
    int status = result.getResponse().getStatus();
    assertThat(status).as("유효하지 않은 status는 400 Bad Request여야 합니다").isEqualTo(400);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다")
        .isEqualTo("VALIDATION_ERROR");
  }

  private Long registerAndGetId(String displayName) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/investors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("displayName", displayName, "relationship", "SELF")))
                    .session(session))
            .andReturn();
    assertThat(created.getResponse().getStatus())
        .as("테스트 사전 준비: 투자자 등록은 201이어야 합니다")
        .isEqualTo(201);
    return ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
  }

  private Long createInvestorWithStatus(RecordStatus status) {
    LocalDateTime now = LocalDateTime.now();
    Investor saved =
        investorRepository.save(
            Investor.builder()
                .accountId(accountId)
                .displayName("상태투자자")
                .relationship("OTHER")
                .status(status)
                .deletedAt(status == RecordStatus.INACTIVE ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return saved.getId();
  }

  @Test
  void updateInvestorWithValidRequestReturns200AndUpdatedFields() throws Exception {
    // Given: 인증된 계정이 특정 투자자를 소유하고 있고, 새로운 표시 이름이 제공됨 (AC-007)
    signupAndLogin();
    Long investorId = registerAndGetId("홍길동");
    LocalDateTime beforeUpdate =
        investorRepository.findById(investorId).orElseThrow().getUpdatedAt();

    var request =
        Map.of(
            "displayName", "수정된표시명",
            "relationship", "SPOUSE");

    // When: PATCH /investors/{investor_id} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/" + investorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 200 OK 응답을 반환하고, 투자자 정보가 업데이트됨 (AC-007)
    int status = result.getResponse().getStatus();
    assertThat(status).as("유효한 수정 요청 시 200 OK를 반환해야 합니다 (AC-007)").isEqualTo(200);

    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("displayName")).as("응답에 수정된 표시 이름이 반영되어야 합니다 (AC-007)").isEqualTo("수정된표시명");

    // And: 관계 및 updatedAt이 수정되어 저장되고, 응답에 수정된 updatedAt이 반영됨 (AC-007)
    Investor saved = investorRepository.findById(investorId).orElseThrow();
    assertThat(saved.getRelationship()).as("수정된 관계가 저장되어야 합니다").isEqualTo("SPOUSE");
    LocalDateTime responseUpdatedAt = LocalDateTime.parse((String) body.get("updatedAt"));
    assertThat(responseUpdatedAt)
        .as("응답 updatedAt이 수정 시각으로 갱신되어야 합니다 (AC-007)")
        .isAfter(beforeUpdate);
  }

  @Test
  void updateDisplayNameOnlyKeepsRelationshipUnchanged() throws Exception {
    // Given: 본인 계정 소유 투자자 등록됨 (relationship=SELF), displayName만 수정 요청 (PATCH 부분 수정)
    signupAndLogin();
    Long investorId = registerAndGetId("홍길동");

    var request = Map.of("displayName", "새표시명");

    // When: PATCH /investors/{id} 요청에 displayName만 전달
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/" + investorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 200 OK + displayName만 변경되고 relationship은 유지됨 (부분 수정)
    assertThat(result.getResponse().getStatus()).as("정상 수정 요청 시 200 OK여야 합니다").isEqualTo(200);
    Investor saved = investorRepository.findById(investorId).orElseThrow();
    assertThat(saved.getDisplayName()).as("displayName이 변경되어야 합니다").isEqualTo("새표시명");
    assertThat(saved.getRelationship()).as("미전달된 relationship은 유지되어야 합니다").isEqualTo("SELF");
  }

  @Test
  void updateInvestorWithEmptyDisplayNameReturns400AndDataUnchanged() throws Exception {
    // Given: 인증된 계정이 특정 투자자를 소유하고 있음 (AC-008)
    signupAndLogin();
    Long investorId = registerAndGetId("홍길동");
    Investor before = investorRepository.findById(investorId).orElseThrow();

    var request =
        Map.of(
            "displayName", "   ",
            "relationship", "SPOUSE");

    // When: PATCH /investors/{investor_id} 요청에 displayName이 빈 문자열(공백만)인 경우
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/" + investorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 400 Bad Request + VALIDATION_ERROR (AC-008)
    int status = result.getResponse().getStatus();
    assertThat(status).as("빈 표시 이름 수정 요청 시 400 Bad Request를 반환해야 합니다 (AC-008)").isEqualTo(400);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-008)")
        .isEqualTo("VALIDATION_ERROR");

    // And: 투자자 정보가 변경되지 않음 (AC-008)
    Investor after = investorRepository.findById(investorId).orElseThrow();
    assertThat(after.getDisplayName()).as("표시 이름이 변경되지 않아야 합니다 (AC-008)").isEqualTo("홍길동");
    assertThat(after.getRelationship()).as("관계가 변경되지 않아야 합니다 (AC-008)").isEqualTo("SELF");
    assertThat(after.getUpdatedAt())
        .as("updatedAt이 변경되지 않아야 합니다 (AC-008)")
        .isEqualTo(before.getUpdatedAt());
  }

  @Test
  void updateInactiveInvestorReturns200WithUpdatedFields() throws Exception {
    // Given: INACTIVE 상태(비활성화된) 투자자를 본인 계정이 소유하고 있음 (엣지케이스 7)
    signupAndLogin();
    Long investorId = createInvestorWithStatus(RecordStatus.INACTIVE);

    var request =
        Map.of(
            "displayName", "비활성투자자수정",
            "relationship", "SPOUSE");

    // When: INACTIVE 투자자에게 PATCH /investors/{id} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/" + investorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 200 OK + 표시 이름/관계 수정이 정상 반영됨 (엣지케이스 7)
    int status = result.getResponse().getStatus();
    assertThat(status).as("INACTIVE 투자자 수정 시 200 OK여야 합니다 (엣지케이스 7)").isEqualTo(200);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("displayName")).isEqualTo("비활성투자자수정");
    assertThat(body.get("status"))
        .as("status는 INACTIVE가 유지되어야 합니다 (엣지케이스 7)")
        .isEqualTo("INACTIVE");
    Investor saved = investorRepository.findById(investorId).orElseThrow();
    assertThat(saved.getRelationship())
        .as("INACTIVE 투자자의 관계 수정이 저장되어야 합니다 (엣지케이스 7)")
        .isEqualTo("SPOUSE");
  }

  @Test
  void updateInvestorWithEmptyRelationshipStoresNull() throws Exception {
    // Given: 본인 계정 소유 투자자가 relationship=SELF를 가짐 (엣지케이스 3: 빈 관계 필드는 NULL로 저장)
    signupAndLogin();
    Long investorId = registerAndGetId("홍길동");

    var request = Map.of("relationship", "");

    // When: PATCH /investors/{id} 요청에 relationship이 빈 문자열로 전달됨
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/" + investorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .session(session))
            .andReturn();

    // Then: 200 OK + relationship이 NULL로 저장됨 (엣지케이스 3)
    assertThat(result.getResponse().getStatus()).as("빈 관계 필드 수정 요청 시 200 OK여야 합니다").isEqualTo(200);
    Investor saved = investorRepository.findById(investorId).orElseThrow();
    assertThat(saved.getRelationship()).as("빈 relationship은 NULL로 저장되어야 합니다 (엣지케이스 3)").isNull();
  }

  @Test
  void updateInvestorWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 계정이 존재함 (spec: 존재하지 않는 투자자는 404 NOT_FOUND)
    signupAndLogin();

    // When: 존재하지 않는 투자자 ID로 PATCH /investors/{id} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/99999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("displayName", "수정")))
                    .session(session))
            .andReturn();

    // Then: 404 Not Found + NOT_FOUND 오류
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 투자자 수정 시 404여야 합니다").isEqualTo(404);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
  }

  @Test
  void updateInvestorOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 계정 B의 투자자 ID가 알려져 있음 (AC-005와 동일한 소유권 로직)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();

    // When: 계정 A가 PATCH /investors/{accountB_investor_id} 요청을 보냄
    MvcResult result =
        mockMvc
            .perform(
                patch("/investors/" + otherInvestorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("displayName", "수정")))
                    .session(session))
            .andReturn();

    // Then: 403 Forbidden + FORBIDDEN, 계정 B 투자자 정보 미변경
    int status = result.getResponse().getStatus();
    assertThat(status).as("다른 계정 투자자 수정 시 403 Forbidden이어야 합니다").isEqualTo(403);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다").isEqualTo("FORBIDDEN");
    Investor saved = investorRepository.findById(otherInvestorId).orElseThrow();
    assertThat(saved.getDisplayName()).as("계정 B의 투자자 정보가 변경되면 안 됩니다").isEqualTo("다른계정투자자");
  }

  @Test
  void deleteInvestorReturns200AndSetsInactiveAndDeletedAt() throws Exception {
    // Given: 인증된 계정이 특정 투자자를 소유하고 있음 (AC-009)
    signupAndLogin();
    Long investorId = registerAndGetId("홍길동");
    assertThat(investorRepository.findById(investorId).orElseThrow().getStatus())
        .as("사전 조건: 삭제 전 status는 ACTIVE여야 합니다")
        .isEqualTo(RecordStatus.ACTIVE);

    // When: DELETE /investors/{investor_id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(delete("/investors/" + investorId).session(session)).andReturn();

    // Then: 200 OK 응답을 반환하고 성공 메시지를 포함함 (AC-009)
    int status = result.getResponse().getStatus();
    assertThat(status).as("정상 비활성화 요청 시 200 OK를 반환해야 합니다 (AC-009)").isEqualTo(200);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("message")).as("성공 메시지를 반환해야 합니다 (AC-009)").isEqualTo("투자자가 비활성화되었습니다.");

    // And: 투자자의 status가 INACTIVE로 변경되고 deletedAt에 현재 타임스탬프가 설정됨 (AC-009)
    Investor saved = investorRepository.findById(investorId).orElseThrow();
    assertThat(saved.getStatus())
        .as("status가 INACTIVE로 변경되어야 합니다 (AC-009)")
        .isEqualTo(RecordStatus.INACTIVE);
    assertThat(saved.getDeletedAt()).as("deletedAt에 현재 타임스탬프가 설정되어야 합니다 (AC-009)").isNotNull();
  }

  @Test
  void deleteAlreadyInactiveInvestorReturns200AndKeepsDeletedAt() throws Exception {
    // Given: 본인 계정 소유 투자자가 이미 비활성화(INACTIVE) 상태이고 deletedAt이 설정되어 있음 (엣지케이스 6)
    signupAndLogin();
    LocalDateTime originalDeletedAt = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    Long investorId =
        investorRepository
            .save(
                Investor.builder()
                    .accountId(accountId)
                    .displayName("비활성투자자")
                    .relationship("OTHER")
                    .status(RecordStatus.INACTIVE)
                    .deletedAt(originalDeletedAt)
                    .createdAt(originalDeletedAt)
                    .updatedAt(originalDeletedAt)
                    .build())
            .getId();

    // When: 이미 INACTIVE 상태인 투자자에게 DELETE /investors/{id} 요청을 다시 보냄 (멱등)
    MvcResult result =
        mockMvc.perform(delete("/investors/" + investorId).session(session)).andReturn();

    // Then: 에러 없이 200 OK 응답을 반환함 (엣지케이스 6 - 멱등성)
    int status = result.getResponse().getStatus();
    assertThat(status).as("이미 INACTIVE인 투자자 재비활성화 시 에러가 아니라 200 OK여야 합니다 (엣지케이스 6)").isEqualTo(200);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("message")).isEqualTo("투자자가 비활성화되었습니다.");

    // And: 기존 deletedAt 값이 그대로 유지되고 status도 INACTIVE가 유지됨 (멱등성)
    Investor saved = investorRepository.findById(investorId).orElseThrow();
    assertThat(saved.getStatus())
        .as("status는 INACTIVE가 유지되어야 합니다 (엣지케이스 6)")
        .isEqualTo(RecordStatus.INACTIVE);
    assertThat(saved.getDeletedAt())
        .as("기존 deletedAt이 그대로 유지되어야 합니다 (엣지케이스 6)")
        .isEqualTo(originalDeletedAt);
  }

  @Test
  void deleteInvestorWithUnknownIdReturns404() throws Exception {
    // Given: 인증된 계정이 존재함 (spec: 존재하지 않는 투자자는 404 NOT_FOUND)
    signupAndLogin();

    // When: 존재하지 않는 투자자 ID로 DELETE /investors/{id} 요청을 보냄
    MvcResult result = mockMvc.perform(delete("/investors/99999999").session(session)).andReturn();

    // Then: 404 Not Found + NOT_FOUND 오류
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 투자자 비활성화 시 404여야 합니다").isEqualTo(404);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
  }

  @Test
  void deleteInvestorOwnedByOtherAccountReturns403() throws Exception {
    // Given: 인증된 계정 A가 존재하고, 계정 B의 투자자 ID가 알려져 있음 (소유권 검증 재사용)
    signupAndLogin();
    Long otherInvestorId = createOtherAccountInvestor();

    // When: 계정 A가 DELETE /investors/{accountB_investor_id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(delete("/investors/" + otherInvestorId).session(session)).andReturn();

    // Then: 403 Forbidden + FORBIDDEN, 계정 B의 투자자 상태는 변경되지 않음
    int status = result.getResponse().getStatus();
    assertThat(status).as("다른 계정 투자자 비활성화 시 403 Forbidden이어야 합니다").isEqualTo(403);
    var responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).as("에러 코드는 FORBIDDEN이어야 합니다").isEqualTo("FORBIDDEN");
    Investor other = investorRepository.findById(otherInvestorId).orElseThrow();
    assertThat(other.getStatus()).as("계정 B의 투자자 상태가 변경되면 안 됩니다").isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void deleteInvestorWithoutAuthenticationReturns401() throws Exception {
    // Given: 인증되지 않은 요청이 들어옴 (AC-003과 동일한 미인증 규칙)
    signupAndLogin();
    Long investorId = registerAndGetId("홍길동");
    MockHttpSession noSession = new MockHttpSession();

    // When: 세션 없이 DELETE /investors/{id} 요청을 보냄
    MvcResult result =
        mockMvc.perform(delete("/investors/" + investorId).session(noSession)).andReturn();

    // Then: 401 Unauthorized 응답을 반환함
    int status = result.getResponse().getStatus();
    assertThat(status).as("미인증 삭제 요청은 401 Unauthorized를 반환해야 합니다").isEqualTo(401);
  }
}
