package com.ipocalc.accountauthentication.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.controller.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청 제한 필터를 시큐리티 필터 체인에 얹고 실제 요청으로 검증한다.
 *
 * <p>같은 IP의 카운터를 여러 테스트가 공유하지 않도록 테스트마다 서로 다른 remoteAddr를 쓴다. 한도는 인라인 프로퍼티로 공개 조회 3회, 로그인·가입 2회로
 * 줄였다.
 */
@SpringBootTest(
    properties = {"app.rate-limit.public-read-per-minute=3", "app.rate-limit.auth-per-minute=2"})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class RateLimitIntegrationTest {

  private static final String LIMIT_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void publicReadReturns429AfterLimitWithRetryAfterHeader() throws Exception {
    String ip = "10.30.0.1";

    for (int i = 0; i < 3; i++) {
      MvcResult allowed = getFrom("/market-holidays", ip, null);
      assertThat(allowed.getResponse().getStatus()).isEqualTo(200);
      assertThat(allowed.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    MvcResult limited = getFrom("/market-holidays", ip, null);
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    assertThat(limited.getResponse().getContentType()).isEqualTo("application/json;charset=UTF-8");
    ErrorResponse body = objectMapper.readValue(content(limited), ErrorResponse.class);
    assertThat(body.getError()).isEqualTo("RATE_LIMITED");
    assertThat(body.getMessage()).isEqualTo(LIMIT_MESSAGE);
  }

  @Test
  void loginIsLimitedButProtectedApisAreNot() throws Exception {
    String ip = "10.30.0.2";
    String loginBody = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";

    for (int i = 0; i < 2; i++) {
      MvcResult attempt = postFrom("/auth/login", ip, loginBody);
      assertThat(attempt.getResponse().getStatus()).isEqualTo(401);
    }

    MvcResult limited = postFrom("/auth/login", ip, loginBody);
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();

    // 로그인·가입이 아닌 보호 API는 같은 IP여도 제한되지 않는다.
    for (int i = 0; i < 5; i++) {
      MvcResult protectedApi = getFrom("/investors", ip, null);
      assertThat(protectedApi.getResponse().getStatus()).isEqualTo(401);
      assertThat(protectedApi.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }
  }

  @Test
  void forwardedForHeaderIsIgnoredByDefault() throws Exception {
    String ip = "10.30.0.5";

    for (int i = 0; i < 3; i++) {
      MvcResult allowed = getFrom("/market-holidays", ip, "203.0.113.50");
      assertThat(allowed.getResponse().getStatus()).isEqualTo(200);
    }

    // remoteAddr 기준으로 셌으므로 헤더를 붙여도 같은 IP는 계속 막힌다.
    MvcResult limited = getFrom("/market-holidays", ip, "203.0.113.50");
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);

    // 헤더에 적힌 IP는 카운터가 비어 있어 그대로 통과한다.
    MvcResult forwardedIp = getFrom("/market-holidays", "203.0.113.50", null);
    assertThat(forwardedIp.getResponse().getStatus()).isEqualTo(200);
  }

  private MvcResult getFrom(String uri, String remoteAddr, String forwardedFor) throws Exception {
    MockHttpServletRequestBuilder request =
        get(uri)
            .with(
                mockRequest -> {
                  mockRequest.setRemoteAddr(remoteAddr);
                  return mockRequest;
                });
    if (forwardedFor != null) {
      request.header("X-Forwarded-For", forwardedFor);
    }
    return mockMvc.perform(request).andReturn();
  }

  private MvcResult postFrom(String uri, String remoteAddr, String body) throws Exception {
    return mockMvc
        .perform(
            post(uri)
                .with(
                    mockRequest -> {
                      mockRequest.setRemoteAddr(remoteAddr);
                      return mockRequest;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private static String content(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
