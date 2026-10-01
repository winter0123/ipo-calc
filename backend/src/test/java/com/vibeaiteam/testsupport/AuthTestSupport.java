package com.vibeaiteam.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 테스트가 공통으로 쓰는 가입·로그인 도우미.
 *
 * <p>같은 {@link MockHttpSession}으로 {@code /accounts}(가입)와 {@code /auth/login}(로그인)을 차례로 호출해 로그인된 세션을
 * 만든다. 가입과 로그인 사이에 계정을 조회·수정해야 하는 테스트는 {@link #signup}과 {@link #login}을 따로 호출한다.
 */
public final class AuthTestSupport {

  private AuthTestSupport() {}

  /** 가입 후 같은 세션으로 로그인한다. */
  public static void signupAndLogin(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      MockHttpSession session,
      String email,
      String password,
      String name)
      throws Exception {
    signup(mockMvc, objectMapper, session, email, password, name);
    login(mockMvc, objectMapper, session, email, password);
  }

  /** 가입 요청(/accounts)을 보낸다. */
  public static void signup(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      MockHttpSession session,
      String email,
      String password,
      String name)
      throws Exception {
    mockMvc
        .perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", email, "password", password, "name", name)))
                .session(session))
        .andReturn();
  }

  /** 로그인 요청(/auth/login)을 보내 세션을 확정한다. */
  public static void login(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      MockHttpSession session,
      String email,
      String password)
      throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("email", email, "password", password)))
                .session(session))
        .andReturn();
  }
}
