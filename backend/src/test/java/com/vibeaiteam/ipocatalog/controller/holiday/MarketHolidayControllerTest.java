package com.vibeaiteam.ipocatalog.controller.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 시장 휴장일 공개 조회 API 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class MarketHolidayControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void getHolidays2026ReturnsSeedDataInAscendingOrder() throws Exception {
    // when: 2026년 휴장일 조회 (시드 데이터 17건)
    MvcResult result = mockMvc.perform(get("/market-holidays?year=2026")).andReturn();

    // then: 200 OK, 시드 17건이 날짜 오름차순으로 반환
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> holidays =
        objectMapper.readValue(utf8Body(result), new TypeReference<>() {});

    assertThat(holidays).hasSize(17);

    // 첫 번째: 2026-01-01 신정
    assertThat(holidays.get(0).get("date")).isEqualTo("2026-01-01");
    assertThat(holidays.get(0).get("name")).isEqualTo("신정");

    // 2026-10-09 한글날이 포함되어 있는지 확인
    boolean hasHangeulDay = holidays.stream()
        .anyMatch(h -> "2026-10-09".equals(h.get("date")) && "한글날".equals(h.get("name")));
    assertThat(hasHangeulDay).isTrue();

    // 응답에 source, id 키가 없는지 확인
    for (Map<String, Object> h : holidays) {
      assertThat(h).doesNotContainKey("source");
      assertThat(h).doesNotContainKey("id");
      assertThat(h).doesNotContainKey("createdAt");
    }
  }

  @Test
  void getHolidaysFutureYearReturnsEmptyArray() throws Exception {
    // when: 시드가 없는 먼 미래 연도 조회
    MvcResult result = mockMvc.perform(get("/market-holidays?year=2090")).andReturn();

    // then: 200 OK, 빈 배열
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> holidays =
        objectMapper.readValue(utf8Body(result), new TypeReference<>() {});
    assertThat(holidays).isEmpty();
  }

  @Test
  void getHolidaysWithoutYearParamReturnsCurrentYearData() throws Exception {
    // when: year 파라미터 없이 조회 (현재 연도)
    MvcResult result = mockMvc.perform(get("/market-holidays")).andReturn();

    // then: 200 OK, 현재 연도 데이터 반환 (건수는 단정하지 않음)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> holidays =
        objectMapper.readValue(utf8Body(result), new TypeReference<>() {});
    // 현재 연도에 데이터가 있을 수도 있고 없을 수도 있음 - 200만 확인
    assertThat(holidays).isNotNull();
  }

  @Test
  void getHolidaysInvalidYearBelowRangeReturns400() throws Exception {
    // when: 2000 미만 연도 조회
    MvcResult result = mockMvc.perform(get("/market-holidays?year=1999")).andReturn();

    // then: 400 VALIDATION_ERROR
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> error = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(error.get("error")).isEqualTo("VALIDATION_ERROR");
    assertThat(error.get("message")).isEqualTo("연도는 2000~2100 사이여야 합니다.");
  }

  @Test
  void getHolidaysInvalidYearAboveRangeReturns400() throws Exception {
    // when: 2100 초과 연도 조회
    MvcResult result = mockMvc.perform(get("/market-holidays?year=2101")).andReturn();

    // then: 400 VALIDATION_ERROR
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    Map<String, Object> error = objectMapper.readValue(utf8Body(result), Map.class);
    assertThat(error.get("error")).isEqualTo("VALIDATION_ERROR");
    assertThat(error.get("message")).isEqualTo("연도는 2000~2100 사이여야 합니다.");
  }

  @Test
  void getHolidaysWithoutLoginReturns200() throws Exception {
    // when: 로그인하지 않고 조회 (세션 없음)
    MvcResult result = mockMvc.perform(get("/market-holidays?year=2026")).andReturn();

    // then: 200 OK (공개 API)
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<Map<String, Object>> holidays =
        objectMapper.readValue(utf8Body(result), new TypeReference<>() {});
    assertThat(holidays).hasSize(17);
  }

  private String utf8Body(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}