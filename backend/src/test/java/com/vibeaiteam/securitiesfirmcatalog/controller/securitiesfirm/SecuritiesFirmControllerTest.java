package com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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

/**
 * AC 인용: docs/spec/securities-firm-catalog.md, docs/work-log/securities-firm-catalog.md - AC-001:
 * 증권사 등록 성공 — 유효한 증권사명이 제공되면 POST /securities-firms 요청 시 201 Created 응답 + 신규 SecuritiesFirm 생성, 응답에
 * id/name/externalCode/ businessRegistrationNumber/status=ACTIVE/createdAt/updatedAt 포함 - AC-002:
 * 증권사 등록 실패 (빈 이름) — name이 빈 문자열/공백만이면 400 Bad Request + VALIDATION_ERROR, 증권사 레코드 미생성 (edge: 255자
 * 초과도 400) - AC-003: 증권사 등록 실패 (중복 이름) — 동일 name의 ACTIVE 증권사가 이미 존재하면 409 Conflict + DUPLICATE_NAME
 * - AC-004: 증권사 목록 조회 성공 — GET /securities-firms 요청 시 200 OK + 전체 증권사 목록 포함 - AC-005: 증권사 상세 조회 성공
 * — 존재하는 id로 GET /securities-firms/{id} 요청 시 200 OK + 상세 정보 반환 - AC-006: 증권사 상세 조회 실패 — 존재하지 않는 id로
 * GET /securities-firms/{id} 요청 시 404 + NOT_FOUND - AC-007: 증권사 수정 성공 — 존재하는 id + 유효한 수정 데이터로 PATCH
 * /securities-firms/{id} 요청 시 200 OK + 필드/updatedAt 반영 (부분 수정: 요청에 포함된 필드만 갱신) - AC-008: 증권사 수정 실패
 * (빈 이름) — name이 빈 문자열/공백만이면 400 + VALIDATION_ERROR, 기존 데이터 미변경 - AC-009: 증권사 수정 실패 (중복 이름) — 다른
 * 활성(ACTIVE) 증권사와 동일한 이름으로 수정 시 409 + DUPLICATE_NAME (자기 자신과 동일한 현재 이름은 중복 아님 — 변경 없는 수정 허용) -
 * AC-010: 증권사 수정 실패 (존재하지 않는 ID) — 존재하지 않는 id로 PATCH 시 404 + NOT_FOUND - D-SF-003: deletedAt IS
 * NULL(=ACTIVE)만 목록/상세 조회에 노출 — 논리 삭제된 증권사는 둘 다에서 제외 - 글로벌 기준정보: 계정 소유권 검증 불필요(SecurityUtils 미사용)하나
 * 인증은 필요 (anyRequest().authenticated())
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesFirmControllerTest {

  private static final String STATUS_ACTIVE = "ACTIVE";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AccountRepository accountRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  private String testEmail;
  private String testPassword;
  private String testName;
  private MockHttpSession session;
  private final List<SecuritiesFirm> registeredFirms = new ArrayList<>();

  @BeforeEach
  void setUp() {
    long nonce = ThreadLocalRandom.current().nextLong();
    testEmail = "securities-owner-" + nonce + "@example.com";
    testPassword = "securePass123!";
    testName = "기준정보테스터";
    session = new MockHttpSession();
  }

  @AfterEach
  void tearDown() {
    cleanupAccount(testEmail);
    cleanupFirms();
  }

  private void cleanupAccount(String email) {
    Account account = accountRepository.findByEmail(email);
    if (account != null) {
      accountRepository.delete(account);
    }
  }

  private void cleanupFirms() {
    List<SecuritiesFirm> toDelete =
        registeredFirms.stream().filter(f -> f.getId() != null).collect(Collectors.toList());
    if (!toDelete.isEmpty()) {
      securitiesFirmRepository.deleteAll(toDelete);
    }
  }

  private void signupAndLogin() throws Exception {
    mockMvc
        .perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", testEmail,
                            "password", testPassword,
                            "name", testName)))
                .session(session))
        .andReturn();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "email", testEmail,
                            "password", testPassword)))
                .session(session))
        .andReturn();
  }

  private MvcResult registerRequest(Map<String, ?> request) throws Exception {
    return mockMvc
        .perform(
            post("/securities-firms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(session))
        .andReturn();
  }

  private String utf8Body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  void registerFirmWithValidRequestReturns201AndResponseFields() throws Exception {
    // Given: 인증된 사용자가 존재하고 유효한 증권사명이 제공됨 (AC-001)
    signupAndLogin();
    var request =
        Map.of(
            "name", "한국투자증권",
            "externalCode", "SC-0001",
            "businessRegistrationNumber", "123-45-67890");

    // When: POST /securities-firms 요청이 전송됨
    MvcResult result = registerRequest(request);

    // Then: 201 Created 응답을 반환하고 신규 SecuritiesFirm이 생성됨 (AC-001)
    int status = result.getResponse().getStatus();
    assertThat(status).as("유효한 등록 요청 시 201 Created를 반환해야 합니다 (AC-001)").isEqualTo(201);

    String responseBody = utf8Body(result);
    Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
    assertThat(response.get("id")).as("응답에 증권사 id가 포함되어야 합니다 (AC-001)").isNotNull();
    assertThat(response.get("name")).as("응답에 증권사명이 포함되어야 합니다 (AC-001)").isEqualTo("한국투자증권");
    assertThat(response.get("externalCode")).isEqualTo("SC-0001");
    assertThat(response.get("businessRegistrationNumber")).isEqualTo("123-45-67890");
    assertThat(response.get("status")).as("status는 ACTIVE여야 합니다 (AC-001)").isEqualTo(STATUS_ACTIVE);
    assertThat(response.get("createdAt")).as("createdAt이 포함되어야 합니다 (AC-001)").isNotNull();
    assertThat(response.get("updatedAt")).as("updatedAt이 포함되어야 합니다 (AC-001)").isNotNull();

    // And: DB에 신규 증권사 레코드가 생성됨 (AC-001)
    SecuritiesFirm saved =
        securitiesFirmRepository.findByNameAndStatus("한국투자증권", STATUS_ACTIVE).orElseThrow();
    assertThat(saved.getName()).isEqualTo("한국투자증권");
    assertThat(saved.getExternalCode()).isEqualTo("SC-0001");
    assertThat(saved.getBusinessRegistrationNumber()).isEqualTo("123-45-67890");
    assertThat(saved.getStatus()).isEqualTo(STATUS_ACTIVE);
    assertThat(saved.getDeletedAt()).isNull();
    registeredFirms.add(saved);
  }

  @Test
  void registerFirmWithEmptyNameReturns400ValidationError() throws Exception {
    // Given: 인증된 사용자가 존재함 (AC-002)
    signupAndLogin();

    // When: POST /securities-firms 요청에 name이 빈 문자열(공백만)인 경우
    MvcResult result = registerRequest(Map.of("name", "   "));

    // Then: 400 Bad Request + VALIDATION_ERROR (AC-002)
    int status = result.getResponse().getStatus();
    assertThat(status).as("빈 증권사명 요청 시 400 Bad Request를 반환해야 합니다 (AC-002)").isEqualTo(400);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-002)")
        .isEqualTo("VALIDATION_ERROR");
    assertThat(responseMap.get("message")).isNotNull();

    // And: 증권사 레코드가 생성되지 않음 (AC-002)
    assertThat(securitiesFirmRepository.findByNameAndStatus("", STATUS_ACTIVE)).isEmpty();
  }

  @Test
  void registerFirmWithNullNameReturns400ValidationError() throws Exception {
    // Given: 인증된 사용자가 존재하고 name이 null로 전달됨 (AC-002 엣지)
    signupAndLogin();

    // When: POST /securities-firms 요청에 name이 없이 전송됨
    MvcResult result = registerRequest(Map.of());

    // Then: 400 Bad Request + VALIDATION_ERROR (AC-002)
    int status = result.getResponse().getStatus();
    assertThat(status).as("name 누락 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).isEqualTo("VALIDATION_ERROR");
    assertThat(responseMap.get("message")).isNotNull();
  }

  @Test
  void registerFirmWithOver255NameReturns400ValidationError() throws Exception {
    // Given: 인증된 사용자가 존재하고 255자 초과 증권사명이 제공됨 (spec 엣지: 255자 초과)
    signupAndLogin();
    String over255 = "가".repeat(256);

    // When: POST /securities-firms 요청에 255자 초과 name이 전송됨
    MvcResult result = registerRequest(Map.of("name", over255));

    // Then: 400 Bad Request + VALIDATION_ERROR, 레코드 미생성
    int status = result.getResponse().getStatus();
    assertThat(status).as("255자 초과 증권사명 요청 시 400 Bad Request를 반환해야 합니다").isEqualTo(400);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).isEqualTo("VALIDATION_ERROR");
    assertThat(responseMap.get("message")).isNotNull();
  }

  @Test
  void registerFirmWithDuplicateActiveNameReturns409DuplicateName() throws Exception {
    // Given: 기존에 동일한 이름의 활성(ACTIVE) 증권사가 존재함 (AC-003)
    signupAndLogin();
    MvcResult first = registerRequest(Map.of("name", "미래에셋증권"));
    assertThat(first.getResponse().getStatus()).as("테스트 사전 준비: 최초 등록은 201이어야 합니다").isEqualTo(201);
    SecuritiesFirm existing =
        securitiesFirmRepository.findByNameAndStatus("미래에셋증권", STATUS_ACTIVE).orElseThrow();
    registeredFirms.add(existing);

    // When: 동일한 name으로 POST /securities-firms 요청이 전송됨
    MvcResult result = registerRequest(Map.of("name", "미래에셋증권"));

    // Then: 409 Conflict + DUPLICATE_NAME (AC-003)
    int status = result.getResponse().getStatus();
    assertThat(status).as("중복 이름 등록 시 409 Conflict를 반환해야 합니다 (AC-003)").isEqualTo(409);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 DUPLICATE_NAME이어야 합니다 (AC-003)")
        .isEqualTo("DUPLICATE_NAME");
    assertThat(responseMap.get("message")).isNotNull();

    // And: 추가 레코드가 생성되지 않음 (AC-003)
    List<SecuritiesFirm> matches =
        securitiesFirmRepository.findAll().stream()
            .filter(f -> "미래에셋증권".equals(f.getName()) && STATUS_ACTIVE.equals(f.getStatus()))
            .collect(Collectors.toList());
    assertThat(matches).as("중복 등록 실패 시 동일 활성 증권사 레코드는 1건뿐이어야 합니다 (AC-003)").hasSize(1);
  }

  @Test
  void listFirmsReturnsAllActiveFirmsAsList() throws Exception {
    // Given: 인증된 사용자가 존재하고 활성(ACTIVE) 증권사 2곳이 등록되어 있음 (AC-004)
    signupAndLogin();
    registerRequest(Map.of("name", "한국투자증권", "externalCode", "SC-0001")); // 201
    registerRequest(Map.of("name", "미래에셋증권", "externalCode", "SC-0002")); // 201
    List<SecuritiesFirm> active = securitiesFirmRepository.findByDeletedAtIsNull();
    registeredFirms.addAll(active);

    // When: GET /securities-firms 요청이 전송됨
    MvcResult result = mockMvc.perform(get("/securities-firms").session(session)).andReturn();

    // Then: 200 OK 응답을 반환하고 모든 증권사 목록이 포함됨 (AC-004)
    int status = result.getResponse().getStatus();
    assertThat(status).as("목록 조회 시 200 OK여야 합니다 (AC-004)").isEqualTo(200);
    List<Map<String, Object>> body = objectMapper.readValue(utf8Body(result), List.class);
    assertThat(body).as("등록된 모든 활성 증권사가 목록에 포함되어야 합니다 (AC-004)").hasSize(2);
    assertThat(body)
        .extracting(inv -> inv.get("name"))
        .as("등록된 활성 증권사 이름이 모두 포함되어야 합니다 (AC-004)")
        .containsExactlyInAnyOrder("한국투자증권", "미래에셋증권");
    for (Map<String, Object> firm : body) {
      assertThat(firm.get("status"))
          .as("목록 응답의 status는 ACTIVE여야 합니다 (AC-004)")
          .isEqualTo(STATUS_ACTIVE);
    }
  }

  @Test
  void listFirmsExcludesSoftDeletedFirm() throws Exception {
    // Given: 인증된 사용자가 존재하고 활성 증권사 1곳과 논리 삭제된 증권사 1곳이 존재함 (D-SF-003)
    signupAndLogin();
    registerRequest(Map.of("name", "한국투자증권")); // 201
    SecuritiesFirm active =
        securitiesFirmRepository.findByNameAndStatus("한국투자증권", STATUS_ACTIVE).orElseThrow();
    registeredFirms.add(active);

    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm deleted =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("폐지증권")
                .status("INACTIVE")
                .deletedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    registeredFirms.add(deleted);

    // When: GET /securities-firms 요청이 전송됨
    MvcResult result = mockMvc.perform(get("/securities-firms").session(session)).andReturn();

    // Then: 200 OK + 논리 삭제된 증권사는 목록에서 제외됨 (D-SF-003)
    int status = result.getResponse().getStatus();
    assertThat(status).as("목록 조회 시 200 OK여야 합니다").isEqualTo(200);
    List<Map<String, Object>> body = objectMapper.readValue(utf8Body(result), List.class);
    assertThat(body).as("논리 삭제된 증권사는 목록에서 제외되어야 합니다 (D-SF-003)").hasSize(1);
    assertThat(body.get(0).get("name")).as("활성 증권사만 목록에 포함되어야 합니다 (D-SF-003)").isEqualTo("한국투자증권");
  }

  @Test
  void getFirmByIdReturns200WithDetails() throws Exception {
    // Given: 인증된 사용자가 존재하고 등록된 증권사가 존재함 (AC-005)
    signupAndLogin();
    MvcResult created =
        registerRequest(
            Map.of(
                "name", "삼성증권",
                "externalCode", "SC-0003",
                "businessRegistrationNumber", "111-22-33333"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);

    // When: 존재하는 증권사 id로 GET /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc.perform(get("/securities-firms/" + firmId).session(session)).andReturn();

    // Then: 200 OK 응답을 반환하고 해당 증권사의 상세 정보가 반환됨 (AC-005)
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하는 증권사 상세 조회 시 200 OK여야 합니다 (AC-005)").isEqualTo(200);
    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(((Number) body.get("id")).longValue()).isEqualTo(firmId);
    assertThat(body.get("name")).isEqualTo("삼성증권");
    assertThat(body.get("externalCode")).isEqualTo("SC-0003");
    assertThat(body.get("businessRegistrationNumber")).isEqualTo("111-22-33333");
    assertThat(body.get("status")).isEqualTo(STATUS_ACTIVE);
    assertThat(body.get("createdAt")).isNotNull();
    assertThat(body.get("updatedAt")).isNotNull();
  }

  @Test
  void getFirmWithUnknownIdReturns404NotFound() throws Exception {
    // Given: 인증된 사용자가 존재함 (AC-006)
    signupAndLogin();

    // When: 존재하지 않는 증권사 id로 GET /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc.perform(get("/securities-firms/99999999").session(session)).andReturn();

    // Then: 404 Not Found 응답을 반환함 (AC-006)
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 증권사 상세 조회 시 404여야 합니다 (AC-006)").isEqualTo(404);
    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 NOT_FOUND여야 합니다 (AC-006)")
        .isEqualTo("NOT_FOUND");
    assertThat(responseMap.get("message")).isNotNull();
  }

  @Test
  void getSoftDeletedFirmReturns404NotFound() throws Exception {
    // Given: 인증된 사용자가 존재하고 논리 삭제된 증권사가 존재함 (D-SF-003)
    signupAndLogin();
    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm deleted =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("OLD증권")
                .status("INACTIVE")
                .deletedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    registeredFirms.add(deleted);

    // When: 논리 삭제된 증권사 id로 GET /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc.perform(get("/securities-firms/" + deleted.getId()).session(session)).andReturn();

    // Then: 404 Not Found 응답을 반환함 (D-SF-003 — deletedAt 설정 레코드는 상세 조회에서도 제외)
    int status = result.getResponse().getStatus();
    assertThat(status).as("논리 삭제된 증권사 상세 조회 시 404여야 합니다 (D-SF-003)").isEqualTo(404);
    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 NOT_FOUND여야 합니다 (D-SF-003)")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void updateFirmWithValidRequestReturns200AndReflectsFields() throws Exception {
    // Given: 인증된 사용자가 존재하고 등록된 증권사가 존재함 (AC-007)
    signupAndLogin();
    MvcResult created =
        registerRequest(
            Map.of(
                "name", "한국투자증권",
                "externalCode", "SC-0001",
                "businessRegistrationNumber", "123-45-67890"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);
    LocalDateTime oldUpdatedAt = LocalDateTime.now().minusDays(1);
    saved.setUpdatedAt(oldUpdatedAt);
    securitiesFirmRepository.save(saved);

    // When: PATCH /securities-firms/{id} 요청이 유효한 수정 데이터와 함께 전송됨
    MvcResult result =
        mockMvc
            .perform(
                patch("/securities-firms/" + firmId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name", "미래에셋증권",
                                "externalCode", "SC-0099",
                                "businessRegistrationNumber", "999-88-77777")))
                    .session(session))
            .andReturn();

    // Then: 200 OK 응답과 함께 증권사 정보가 업데이트됨 (AC-007)
    int status = result.getResponse().getStatus();
    assertThat(status).as("유효한 수정 요청 시 200 OK를 반환해야 합니다 (AC-007)").isEqualTo(200);

    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(((Number) body.get("id")).longValue()).isEqualTo(firmId);
    assertThat(body.get("name")).isEqualTo("미래에셋증권");
    assertThat(body.get("externalCode")).isEqualTo("SC-0099");
    assertThat(body.get("businessRegistrationNumber")).isEqualTo("999-88-77777");
    assertThat(body.get("status")).isEqualTo(STATUS_ACTIVE);
    assertThat(body.get("updatedAt")).as("수정 응답에 갱신된 updatedAt이 포함되어야 합니다 (AC-007)").isNotNull();

    // And: DB에 수정된 필드와 갱신된 updatedAt이 반영됨 (AC-007)
    SecuritiesFirm updated = securitiesFirmRepository.findById(firmId).orElseThrow();
    assertThat(updated.getName()).isEqualTo("미래에셋증권");
    assertThat(updated.getExternalCode()).isEqualTo("SC-0099");
    assertThat(updated.getBusinessRegistrationNumber()).isEqualTo("999-88-77777");
    assertThat(updated.getStatus()).isEqualTo(STATUS_ACTIVE);
    assertThat(updated.getUpdatedAt())
        .as("updatedAt이 수정 시각으로 갱신되어야 합니다 (AC-007)")
        .isAfter(oldUpdatedAt);
  }

  @Test
  void updateFirmWithOnlyNameReflectsPartialUpdate() throws Exception {
    // Given: 인증된 사용자가 존재하고 등록된 증권사가 존재함 (요청에 포함된 필드만 갱신)
    signupAndLogin();
    MvcResult created =
        registerRequest(
            Map.of(
                "name", "키움증권",
                "externalCode", "SC-0010",
                "businessRegistrationNumber", "123-45-11111"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);

    // When: name만 포함한 PATCH /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc
            .perform(
                patch("/securities-firms/" + firmId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "키움증권(변경)")))
                    .session(session))
            .andReturn();

    // Then: 200 OK + name만 변경되고 나머지 필드는 그대로 유지됨 (부분 수정)
    int status = result.getResponse().getStatus();
    assertThat(status).as("부분 수정 요청 시 200 OK를 반환해야 합니다").isEqualTo(200);

    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("name")).isEqualTo("키움증권(변경)");
    assertThat(body.get("externalCode")).isEqualTo("SC-0010");
    assertThat(body.get("businessRegistrationNumber")).isEqualTo("123-45-11111");

    SecuritiesFirm updated = securitiesFirmRepository.findById(firmId).orElseThrow();
    assertThat(updated.getName()).isEqualTo("키움증권(변경)");
    assertThat(updated.getExternalCode()).isEqualTo("SC-0010");
    assertThat(updated.getBusinessRegistrationNumber()).isEqualTo("123-45-11111");
  }

  @Test
  void updateFirmWithEmptyNameReturns400ValidationError() throws Exception {
    // Given: 인증된 사용자가 존재하고 등록된 증권사가 존재함 (AC-008)
    signupAndLogin();
    MvcResult created = registerRequest(Map.of("name", "키움증권"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);

    // When: 빈(공백만) 증권사명으로 PATCH /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc
            .perform(
                patch("/securities-firms/" + firmId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "   ")))
                    .session(session))
            .andReturn();

    // Then: 400 Bad Request + VALIDATION_ERROR (AC-008)
    int status = result.getResponse().getStatus();
    assertThat(status).as("빈 증권사명 수정 요청 시 400 Bad Request를 반환해야 합니다 (AC-008)").isEqualTo(400);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 VALIDATION_ERROR여야 합니다 (AC-008)")
        .isEqualTo("VALIDATION_ERROR");
    assertThat(responseMap.get("message")).isNotNull();

    // And: 기존 데이터가 변경되지 않음 (AC-008)
    SecuritiesFirm still = securitiesFirmRepository.findById(firmId).orElseThrow();
    assertThat(still.getName()).isEqualTo("키움증권");
  }

  @Test
  void updateFirmWithDuplicateActiveNameReturns409DuplicateName() throws Exception {
    // Given: 기존에 동일한 이름의 활성(ACTIVE) 증권사가 존재하고, 수정 대상 증권사가 존재함 (AC-009)
    signupAndLogin();
    MvcResult first = registerRequest(Map.of("name", "미래에셋증권"));
    assertThat(first.getResponse().getStatus()).as("테스트 사전 준비: 최초 등록은 201이어야 합니다").isEqualTo(201);
    SecuritiesFirm existing =
        securitiesFirmRepository.findByNameAndStatus("미래에셋증권", STATUS_ACTIVE).orElseThrow();
    registeredFirms.add(existing);

    MvcResult second = registerRequest(Map.of("name", "한국투자증권"));
    Long targetId =
        ((Number) objectMapper.readValue(utf8Body(second), Map.class).get("id")).longValue();
    SecuritiesFirm target = securitiesFirmRepository.findById(targetId).orElseThrow();
    registeredFirms.add(target);

    // When: 다른 활성 증권사와 동일한 이름으로 PATCH /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc
            .perform(
                patch("/securities-firms/" + targetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "미래에셋증권")))
                    .session(session))
            .andReturn();

    // Then: 409 Conflict + DUPLICATE_NAME (AC-009)
    int status = result.getResponse().getStatus();
    assertThat(status).as("중복 이름 수정 요청 시 409 Conflict를 반환해야 합니다 (AC-009)").isEqualTo(409);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 DUPLICATE_NAME이어야 합니다 (AC-009)")
        .isEqualTo("DUPLICATE_NAME");
    assertThat(responseMap.get("message")).isNotNull();

    // And: 기존 데이터가 변경되지 않음 (AC-009)
    SecuritiesFirm still = securitiesFirmRepository.findById(targetId).orElseThrow();
    assertThat(still.getName()).isEqualTo("한국투자증권");
  }

  @Test
  void updateFirmWithSameCurrentNameReturns200AllowsNoChange() throws Exception {
    // Given: 인증된 사용자가 존재하고 "삼성증권" 증권사가 존재함 (자기 자신과 동일 이름 → 중복 아님)
    signupAndLogin();
    MvcResult created = registerRequest(Map.of("name", "삼성증권"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);

    // When: 현재와 동일한 이름으로 PATCH /securities-firms/{id} 요청이 전송됨 (변경 없는 수정)
    MvcResult result =
        mockMvc
            .perform(
                patch("/securities-firms/" + firmId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "삼성증권")))
                    .session(session))
            .andReturn();

    // Then: 200 OK (자기 자신은 중복 대상에서 제외 — 변경 없는 경우 허용)
    int status = result.getResponse().getStatus();
    assertThat(status).as("현재와 동일한 이름으로 수정 시 200 OK를 반환해야 합니다 (자기 자신 제외)").isEqualTo(200);

    Map<String, Object> body = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(body.get("name")).isEqualTo("삼성증권");
    SecuritiesFirm still = securitiesFirmRepository.findById(firmId).orElseThrow();
    assertThat(still.getName()).isEqualTo("삼성증권");
  }

  @Test
  void updateFirmWithUnknownIdReturns404NotFound() throws Exception {
    // Given: 인증된 사용자가 존재함 (AC-010)
    signupAndLogin();

    // When: 존재하지 않는 증권사 id로 PATCH /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc
            .perform(
                patch("/securities-firms/99999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "아무개증권")))
                    .session(session))
            .andReturn();

    // Then: 404 Not Found 응답을 반환함 (AC-010)
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 증권사 수정 요청 시 404를 반환해야 합니다 (AC-010)").isEqualTo(404);

    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 NOT_FOUND여야 합니다 (AC-010)")
        .isEqualTo("NOT_FOUND");
    assertThat(responseMap.get("message")).isNotNull();
  }

  @Test
  void deactivateFirmReturns204AndMarksInactiveInDb() throws Exception {
    // Given: 인증된 사용자가 존재하고 등록된 증권사가 존재함 (AC-011)
    signupAndLogin();
    MvcResult created =
        registerRequest(
            Map.of(
                "name", "NH투자증권",
                "externalCode", "SC-0020",
                "businessRegistrationNumber", "123-45-00000"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);
    assertThat(saved.getStatus()).as("삭제 전 status는 ACTIVE여야 합니다").isEqualTo(STATUS_ACTIVE);
    assertThat(saved.getDeletedAt()).as("삭제 전 deletedAt은 null이어야 합니다").isNull();
    LocalDateTime oldUpdatedAt = LocalDateTime.now().minusDays(1);
    saved.setUpdatedAt(oldUpdatedAt);
    securitiesFirmRepository.save(saved);

    // When: DELETE /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc.perform(delete("/securities-firms/" + firmId).session(session)).andReturn();

    // Then: 204 No Content, 본문 없음 (AC-011)
    int status = result.getResponse().getStatus();
    assertThat(status).as("비활성화 성공 시 204 No Content를 반환해야 합니다 (AC-011)").isEqualTo(204);
    assertThat(result.getResponse().getContentAsByteArray())
        .as("204 응답의 본문은 비어 있어야 합니다 (AC-011)")
        .isEmpty();

    // And: DB에서 status=INACTIVE, deletedAt 설정, updatedAt 갱신 확인 (AC-011)
    SecuritiesFirm inDb = securitiesFirmRepository.findById(firmId).orElseThrow();
    assertThat(inDb.getStatus()).as("비활성화 후 status는 INACTIVE여야 합니다 (AC-011)").isEqualTo("INACTIVE");
    assertThat(inDb.getDeletedAt()).as("비활성화 후 deletedAt이 설정되어야 합니다 (AC-011)").isNotNull();
    assertThat(inDb.getUpdatedAt())
        .as("비활성화 후 updatedAt이 현재 시각으로 갱신되어야 합니다 (AC-011)")
        .isAfter(oldUpdatedAt);
  }

  @Test
  void deactivatedFirmIsExcludedFromList() throws Exception {
    // Given: 인증된 사용자가 존재하고 활성 증권사 2곳이 등록되어 있음 (D-SF-003 필터 재사용 검증)
    signupAndLogin();
    registerRequest(Map.of("name", "한국투자증권"));
    registerRequest(Map.of("name", "미래에셋증권"));
    List<SecuritiesFirm> active = securitiesFirmRepository.findByDeletedAtIsNull();
    registeredFirms.addAll(active);
    SecuritiesFirm target =
        active.stream().filter(f -> "미래에셋증권".equals(f.getName())).findFirst().orElseThrow();

    // When: 한 곳이 비활성화된 뒤 GET /securities-firms 요청이 전송됨
    int deleteStatus =
        mockMvc
            .perform(delete("/securities-firms/" + target.getId()).session(session))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(deleteStatus).as("테스트 사전 준비: 비활성화는 204여야 합니다").isEqualTo(204);
    MvcResult result = mockMvc.perform(get("/securities-firms").session(session)).andReturn();

    // Then: 200 OK + 비활성화된 증권사는 목록에서 제외됨 (D-SF-003)
    int status = result.getResponse().getStatus();
    assertThat(status).as("목록 조회 시 200 OK여야 합니다").isEqualTo(200);
    List<Map<String, Object>> body = objectMapper.readValue(utf8Body(result), List.class);
    assertThat(body).as("비활성화된 증권사는 목록에서 제외되어야 합니다").hasSize(1);
    assertThat(body.get(0).get("name")).as("활성 증권사만 목록에 포함되어야 합니다").isEqualTo("한국투자증권");
  }

  @Test
  void deactivatedFirmDetailReturns404NotFound() throws Exception {
    // Given: 인증된 사용자가 존재하고 등록된 증권사가 존재함
    signupAndLogin();
    MvcResult created = registerRequest(Map.of("name", "KB증권"));
    Long firmId =
        ((Number) objectMapper.readValue(utf8Body(created), Map.class).get("id")).longValue();
    SecuritiesFirm saved = securitiesFirmRepository.findById(firmId).orElseThrow();
    registeredFirms.add(saved);

    // When: 비활성화된 뒤 GET /securities-firms/{id} 요청이 전송됨
    int deleteStatus =
        mockMvc
            .perform(delete("/securities-firms/" + firmId).session(session))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(deleteStatus).as("테스트 사전 준비: 비활성화는 204여야 합니다").isEqualTo(204);
    MvcResult result =
        mockMvc.perform(get("/securities-firms/" + firmId).session(session)).andReturn();

    // Then: 비활성화된 증권사 상세 조회는 404 + NOT_FOUND (D-SF-003 삭제 필터가 상세 조회에서도 적용됨)
    int status = result.getResponse().getStatus();
    assertThat(status).as("비활성화된 증권사 상세 조회 시 404여야 합니다").isEqualTo(404);
    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error")).as("에러 코드는 NOT_FOUND여야 합니다").isEqualTo("NOT_FOUND");
  }

  @Test
  void deactivateFirmWithUnknownIdReturns404NotFound() throws Exception {
    // Given: 인증된 사용자가 존재함 (AC-013)
    signupAndLogin();

    // When: 존재하지 않는 증권사 id로 DELETE /securities-firms/{id} 요청이 전송됨
    MvcResult result =
        mockMvc.perform(delete("/securities-firms/99999999").session(session)).andReturn();

    // Then: 404 Not Found + NOT_FOUND (AC-013)
    int status = result.getResponse().getStatus();
    assertThat(status).as("존재하지 않는 증권사 비활성화 요청 시 404를 반환해야 합니다 (AC-013)").isEqualTo(404);
    Map<String, Object> responseMap = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(responseMap.get("error"))
        .as("에러 코드는 NOT_FOUND여야 합니다 (AC-013)")
        .isEqualTo("NOT_FOUND");
    assertThat(responseMap.get("message")).isNotNull();
  }

  @Test
  void registerFirmWithoutAuthenticationReturns401() throws Exception {
    // Given: 증권사는 글로벌 기준정보(계정 소유권 불필요)이나 인증된 사용자만 호출 가능 (SecurityConfig:
    // anyRequest().authenticated())

    // When: 세션 없는 POST /securities-firms 요청이 전송됨
    MvcResult result =
        mockMvc
            .perform(
                post("/securities-firms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("name", "미인증증권"))))
            .andReturn();

    // Then: 401 Unauthorized 응답을 반환함
    int status = result.getResponse().getStatus();
    assertThat(status).as("인증되지 않은 요청은 401 Unauthorized를 반환해야 합니다").isEqualTo(401);
  }
}
