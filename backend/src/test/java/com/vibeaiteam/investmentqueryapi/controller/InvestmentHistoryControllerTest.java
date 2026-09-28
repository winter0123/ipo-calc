package com.vibeaiteam.investmentqueryapi.controller;

import com.vibeaiteam.investmentqueryapi.controller.investmenthistory.InvestmentHistoryController;
import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.exception.InvestmentQueryValidationException;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InvestmentHistoryController REST API 테스트
 * SaleControllerTest과 동일한 테스트 패턴 사용:
 * @SpringBootTest + @AutoConfigureMockMvc + 실제 DB 저장 후 HTTP 요청 검증
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    com.vibeaiteam.accountauthentication.AccountAuthenticationApplication.class
})
class InvestmentHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvestmentHistoryQueryService queryService;

    @BeforeEach
    void setUp() {
        // SecurityContext에 인증 설정
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
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
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.Collections.emptyList(), 
                        org.springframework.data.domain.Pageable.ofSize(20), 
                        0));

        // When & Then: 200 OK
        mockMvc.perform(get("/investment-history"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void getHistory_size100초과_400() throws Exception {
        // When & Then: size > 100 이면 400 Bad Request
        mockMvc.perform(get("/investment-history?size=101"))
                .andExpect(status().isBadRequest());

        // Also test size exactly 100 should be OK
        mockMvc.perform(get("/investment-history?size=100"))
                .andExpect(status().isOk());
    }

    @Test
    void getHistory_page음수_400() throws Exception {
        // When & Then: page < 0 이면 400 Bad Request
        mockMvc.perform(get("/investment-history?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_with_investorId_200() throws Exception {
        when(queryService.getHistory(any(), any(), any(), eq(0), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.Collections.emptyList(), 
                        org.springframework.data.domain.Pageable.ofSize(20), 
                        0));

        mockMvc.perform(get("/investment-history?investorId=5"))
                .andExpect(status().isOk());
    }

    @Test
    void getHistory_with_ipoId_200() throws Exception {
        when(queryService.getHistory(any(), any(), any(), eq(0), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.Collections.emptyList(), 
                        org.springframework.data.domain.Pageable.ofSize(20), 
                        0));

        mockMvc.perform(get("/investment-history?ipoId=101"))
                .andExpect(status().isOk());
    }
}