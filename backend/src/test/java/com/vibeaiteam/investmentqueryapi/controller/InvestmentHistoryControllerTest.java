package com.vibeaiteam.investmentqueryapi.controller;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryForbiddenException;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryNotFoundException;
import com.vibeaiteam.investmentqueryapi.service.investmenthistory.InvestmentHistoryQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * InvestmentHistoryController REST API 테스트 SaleControllerTest과 동일한 테스트 패턴 사용: @SpringBootTest
 * + @AutoConfigureMockMvc + 실제 DB 저장 후 HTTP 요청 검증.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(
    classes = {com.vibeaiteam.accountauthentication.AccountAuthenticationApplication.class})
class InvestmentHistoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private InvestmentHistoryQueryService queryService;

  @BeforeEach
  void setUp() {
    // SecurityContext에 인증 설정
    Authentication auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "test@test.com", null, java.util.Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getHistory_필터없는기본조회_200() throws Exception {
    // Given: 서비스에서 빈 페이지 반환
    when(queryService.getHistory(any(), any(), any(), eq(0), anyInt()))
        .thenReturn(
            new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(),
                org.springframework.data.domain.Pageable.ofSize(20),
                0));

    // When & Then: 200 OK
    mockMvc
        .perform(get("/investment-history"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void getHistory_size100초과_400() throws Exception {
    // When & Then: size > 100 이면 400 Bad Request
    mockMvc.perform(get("/investment-history?size=101")).andExpect(status().isBadRequest());

    // Also test size exactly 100 should be OK
    mockMvc.perform(get("/investment-history?size=100")).andExpect(status().isOk());
  }

  @Test
  void getHistory_page음수_400() throws Exception {
    // When & Then: page < 0 이면 400 Bad Request
    mockMvc.perform(get("/investment-history?page=-1")).andExpect(status().isBadRequest());
  }

  @Test
  void getHistory_with_investorId_200() throws Exception {
    when(queryService.getHistory(any(), any(), any(), eq(0), anyInt()))
        .thenReturn(
            new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(),
                org.springframework.data.domain.Pageable.ofSize(20),
                0));

    mockMvc.perform(get("/investment-history?investorId=5")).andExpect(status().isOk());
  }

  @Test
  void getHistory_with_ipoId_200() throws Exception {
    when(queryService.getHistory(any(), any(), any(), eq(0), anyInt()))
        .thenReturn(
            new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(),
                org.springframework.data.domain.Pageable.ofSize(20),
                0));

    mockMvc.perform(get("/investment-history?ipoId=101")).andExpect(status().isOk());
  }

  // ===========================================
  // SF-3 단건 상세 조회 테스트
  // ===========================================

  @Test
  void getHistoryDetail_존재하지않는subscriptionId_404() throws Exception {
    // Given: 존재하지 않는 subscriptionId
    when(queryService.getHistoryDetail(anyLong()))
        .thenThrow(new InvestmentQueryNotFoundException("해당 청약을 찾을 수 없습니다."));

    // When & Then: 404 Not Found
    mockMvc
        .perform(get("/investment-history/9999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void getHistoryDetail_타계정소유_403() throws Exception {
    // Given: 타 계정 소유 Subscription
    when(queryService.getHistoryDetail(anyLong()))
        .thenThrow(new InvestmentQueryForbiddenException("해당 데이터에 접근 권한이 없습니다."));

    // When & Then: 403 Forbidden
    mockMvc
        .perform(get("/investment-history/9999"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  @Test
  void getHistoryDetail_정상_배정_매도있음_200() throws Exception {
    // Given: 정상 응답 DTO
    InvestmentHistoryDto dto = new InvestmentHistoryDto();
    dto.setId(1L);
    dto.setSubscriptionQuantity(100L);
    when(queryService.getHistoryDetail(1L)).thenReturn(dto);

    // When & Then: 200 OK + DTO 필드 검증
    mockMvc
        .perform(get("/investment-history/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.subscriptionQuantity").value(100));
  }

  @Test
  void getHistoryDetail_정상_배정없음_200() throws Exception {
    // Given: 배정 없는 정상 응답 DTO
    InvestmentHistoryDto dto = new InvestmentHistoryDto();
    dto.setId(2L);
    dto.setSubscriptionQuantity(100L);
    dto.setRemainingQuantity(null);
    when(queryService.getHistoryDetail(2L)).thenReturn(dto);

    // When & Then: 200 OK
    mockMvc
        .perform(get("/investment-history/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(2));
  }
}
