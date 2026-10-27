package com.ipocalc.accountauthentication.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ipocalc.IpoCalcApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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

/**
 * 요청 제한을 끈 설정(app.rate-limit.enabled=false)에서도 필터가 만들어지지 않고 어떤 요청도 제한되지 않는다.
 *
 * <p>한도는 1로 줄여 두어 필터가 살아 있기만 해도 바로 429가 나올 상황을 만든다.
 */
@SpringBootTest(
    properties = {
      "app.rate-limit.enabled=false",
      "app.rate-limit.public-read-per-minute=1",
      "app.rate-limit.auth-per-minute=1"
    })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class RateLimitDisabledTest {

  private static final String LOGIN_BODY =
      "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectProvider<RateLimitFilter> rateLimitFilterProvider;

  @Test
  void doesNotCreateFilterBeanWhenDisabled() {
    assertThat(rateLimitFilterProvider.getIfAvailable()).isNull();
  }

  @Test
  void doesNotLimitAnyRequestWhenDisabled() throws Exception {
    String ip = "10.40.0.1";

    for (int i = 0; i < 5; i++) {
      MvcResult read = getFrom(ip);
      assertThat(read.getResponse().getStatus()).isEqualTo(200);
      assertThat(read.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    for (int i = 0; i < 3; i++) {
      MvcResult login =
          mockMvc
              .perform(
                  post("/auth/login")
                      .with(
                          mockRequest -> {
                            mockRequest.setRemoteAddr(ip);
                            return mockRequest;
                          })
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(LOGIN_BODY))
              .andReturn();
      assertThat(login.getResponse().getStatus()).isEqualTo(401);
      assertThat(login.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }
  }

  private MvcResult getFrom(String remoteAddr) throws Exception {
    return mockMvc
        .perform(
            get("/market-holidays")
                .with(
                    mockRequest -> {
                      mockRequest.setRemoteAddr(remoteAddr);
                      return mockRequest;
                    }))
        .andReturn();
  }
}
