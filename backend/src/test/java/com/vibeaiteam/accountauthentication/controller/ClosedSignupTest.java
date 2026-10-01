package com.vibeaiteam.accountauthentication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 가입 모드 CLOSED: 가입을 받지 않고 계정을 만들지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.signup.mode=CLOSED")
class ClosedSignupTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AccountRepository accountRepository;

  @Test
  void signupIsRejectedWith403AndNoAccountIsCreated() throws Exception {
    String email = "closed-" + Math.abs(ThreadLocalRandom.current().nextLong()) + "@example.com";

    MvcResult result =
        mockMvc
            .perform(
                post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("email", email, "password", "securePass123!", "name", "닫힘"))))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .contains("SIGNUP_CLOSED");
    assertThat(accountRepository.existsByEmail(email)).isFalse();
  }
}
