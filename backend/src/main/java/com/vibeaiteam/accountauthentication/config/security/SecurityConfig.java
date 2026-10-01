package com.vibeaiteam.accountauthentication.config.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/**
 * Spring Security 설정. 세션 기반 인증을 사용하고 미인증 요청에는 401을 반환한다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * 요청 인가 규칙, 세션 관리, 예외 처리를 구성한다.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, @Value("${app.remember-me.key:}") String rememberMeKey)
      throws Exception {
    String effectiveRememberMeKey = resolveRememberMeKey(rememberMeKey);
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
                    // 가입 승인 관리는 ADMIN만.
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    // 공용 카탈로그 쓰기는 ADMIN만. 스케줄러·동기화는 서비스를 직접 호출하므로 영향 없음.
                    .requestMatchers(
                        HttpMethod.POST, "/ipos/**", "/securities-firms/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.PATCH, "/ipos/**", "/securities-firms/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.DELETE, "/ipos/**", "/securities-firms/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        // D-I-006 (2026-09-21): CSRF 전역 비활성. 프로젝트 전체 로드맵(16개 Epic)에 브라우저 폼 제출 플로우가 없고
        // React SPA가 JSON으로 /auth/* 호출하는 방식뿐이며 CSRF 토큰 요구 스펙도 없음.
        // CSRF 토큰이 없으면 PATCH/DELETE 등 쓰기 API가 403으로 차단되어 Epic 2~12 전체에서 재발하므로 전역 비활성으로 결정.
        .csrf(csrf -> csrf.disable())
        // D-I-006 (2026-09-21): 미인증 요청은 401 Unauthorized 반환 (기존 formLogin 기본 302 리다이렉트 대체).
        // spec AC-003(투자자 등록 미인증 401) 및 Epics 전반의 JSON API 계약에 부합.
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, denied) -> {
                          response.setStatus(HttpStatus.FORBIDDEN.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          response
                              .getWriter()
                              .write("{\"error\":\"FORBIDDEN\",\"message\":\"관리자 권한이 필요합니다\"}");
                        }))
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
                    .key(effectiveRememberMeKey)
                    .tokenValiditySeconds(1209600));

    return http.build();
  }

  private static String resolveRememberMeKey(String configured) {
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    log.warn("APP_REMEMBER_ME_KEY가 설정되지 않아 기동마다 임의 키를 생성한다. 재시작하면 기존 remember-me 쿠키는 무효가 된다.");
    return UUID.randomUUID().toString() + UUID.randomUUID();
  }

  /**
   * 동시 세션 관리를 위한 세션 레지스트리 빈.
   */
  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  /**
   * 세션 생성·소멸 이벤트를 발행하는 빈.
   */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  /**
   * 인증 성공 시 세션을 레지스트리에 등록하는 전략 빈.
   */
  @Bean
  public RegisterSessionAuthenticationStrategy sessionAuthenticationStrategy(
      SessionRegistry sessionRegistry) {
    return new RegisterSessionAuthenticationStrategy(sessionRegistry);
  }

  /**
   * bcrypt 기반 위임 PasswordEncoder 빈.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    String idForEncode = "bcrypt";
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put(idForEncode, new BCryptPasswordEncoder());
    return new DelegatingPasswordEncoder(idForEncode, encoders);
  }
}
