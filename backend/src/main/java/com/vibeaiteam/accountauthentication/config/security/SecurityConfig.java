package com.vibeaiteam.accountauthentication.config.security;

import com.vibeaiteam.accountauthentication.config.ratelimit.RateLimitFilter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/** Spring Security 설정. 세션 기반 인증을 사용하고 미인증 요청에는 401을 반환한다. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** 요청 인가 규칙, 세션 관리, 예외 처리를 구성하고 IP별 요청 제한 필터를 체인 앞쪽에 넣는다. */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectProvider<RateLimitFilter> rateLimitFilterProvider,
      SessionRegistry sessionRegistry)
      throws Exception {
    // 제한 필터가 없으면(비활성) 건너뛴다. 로그인·가입 시도를 세션·인가 처리보다 먼저 거절하기 위한 위치다.
    RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
    if (rateLimitFilter != null) {
      http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
    }
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/accounts",
                        "/auth/login",
                        "/auth/logout",
                        "/css/**",
                        "/js/**",
                        "/error")
                    .permitAll()
                    // 공개 조회 허용 (공모주 달력·휴장일: 로그인 불필요)
                    .requestMatchers(HttpMethod.GET, "/ipos", "/ipos/**", "/market-holidays")
                    .permitAll()
                    // 가입 승인 관리는 ADMIN만.
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    // 공용 카탈로그 쓰기는 ADMIN만. 스케줄러·동기화는 서비스를 직접 호출하므로 영향 없음.
                    .requestMatchers(HttpMethod.POST, "/ipos/**", "/securities-firms/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/ipos/**", "/securities-firms/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/ipos/**", "/securities-firms/**")
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
        // 로그인·로그아웃은 /auth/login·/auth/logout(컨트롤러)뿐이다. 폼 로그인·remember-me·기본 /logout은 쓰지 않는다
        // (폼 로그인은 잠금·요청 제한을 우회하고, remember-me는 로그아웃 뒤에도 인증을 되살린다).
        .formLogin(AbstractHttpConfigurer::disable)
        .rememberMe(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // 401만 돌려주는 API라 "원래 요청 저장"이 필요 없다. 켜 두면 인증 안 된 요청마다 세션(DB 행)이 생긴다.
        .requestCache(cache -> cache.disable())
        .sessionManagement(
            session ->
                session
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .sessionRegistry(sessionRegistry)
                    // 다른 곳에서 로그인해 만료된 세션도 API답게 401로 답한다(302 리다이렉트 아님).
                    .expiredSessionStrategy(
                        event -> event.getResponse().setStatus(HttpStatus.UNAUTHORIZED.value())));

    return http.build();
  }

  /**
   * 동시 세션 관리를 위한 세션 레지스트리 빈. 세션이 DB(Spring Session JDBC)에 있으므로 레지스트리도 DB 기준이어야 재시작 뒤에도
   * 비밀번호 변경·승인 취소 때 다른 세션을 끊을 수 있다.
   */
  @Bean
  public SessionRegistry sessionRegistry(
      ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepository) {
    FindByIndexNameSessionRepository<?> repository = sessionRepository.getIfAvailable();
    // 세션 저장소가 없는 JPA 슬라이스 테스트에서만 메모리 레지스트리로 대체된다.
    return repository == null
        ? new SessionRegistryImpl()
        : new SpringSessionBackedSessionRegistry<>(repository);
  }

  /** 세션 생성·소멸 이벤트를 발행하는 빈. */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  /** 인증 성공 시 세션을 레지스트리에 등록하는 전략 빈. */
  @Bean
  public RegisterSessionAuthenticationStrategy sessionAuthenticationStrategy(
      SessionRegistry sessionRegistry) {
    return new RegisterSessionAuthenticationStrategy(sessionRegistry);
  }

  /** bcrypt 기반 위임 PasswordEncoder 빈. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    String idForEncode = "bcrypt";
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put(idForEncode, new BCryptPasswordEncoder());
    return new DelegatingPasswordEncoder(idForEncode, encoders);
  }
}
