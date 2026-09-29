package com.vibeaiteam.accountauthentication.config.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/login",
                        "/logout",
                        "/accounts",
                        "/auth/login",
                        "/auth/logout",
                        "/css/**",
                        "/js/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // D-I-006 (2026-09-21): CSRF 전역 비활성. 프로젝트 전체 로드맵(16개 Epic)에 브라우저 폼 제출 플로우가 없고
        // React SPA가 JSON으로 /auth/* 호출하는 방식뿐이며 CSRF 토큰 요구 스펙도 없음.
        // CSRF 토큰이 없으면 PATCH/DELETE 등 쓰기 API가 403으로 차단되어 Epic 2~12 전체에서 재발하므로 전역 비활성으로 결정.
        .csrf(csrf -> csrf.disable())
        // D-I-006 (2026-09-21): 미인증 요청은 401 Unauthorized 반환 (기존 formLogin 기본 302 리다이렉트 대체).
        // spec AC-003(투자자 등록 미인증 401) 및 Epics 전반의 JSON API 계약에 부합.
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .formLogin(form -> form.loginPage("/login").permitAll())
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll())
        .sessionManagement(
            session ->
                session
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .sessionRegistry(sessionRegistry())
                    .expiredUrl("/login?expired"))
        .rememberMe(
            remember ->
                remember
                    .key("account-authentication-remember-me-key")
                    .tokenValiditySeconds(1209600));

    return http.build();
  }

  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  public RegisterSessionAuthenticationStrategy sessionAuthenticationStrategy(
      SessionRegistry sessionRegistry) {
    return new RegisterSessionAuthenticationStrategy(sessionRegistry);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    String idForEncode = "bcrypt";
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put(idForEncode, new BCryptPasswordEncoder());
    return new DelegatingPasswordEncoder(idForEncode, encoders);
  }
}
