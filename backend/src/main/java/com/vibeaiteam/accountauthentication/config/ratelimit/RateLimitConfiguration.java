package com.vibeaiteam.accountauthentication.config.ratelimit;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * IP별 요청 제한 필터를 등록한다.
 *
 * <p>{@code app.rate-limit.enabled=false}면 필터 빈 자체를 만들지 않는다. 필터 빈은 Spring Security 필터 체인 안에서만 동작하도록
 * 서블릿 컨테이너 자동 등록을 막는다.
 */
@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfiguration {

  /**
   * 설정 값과 시각, JSON 직렬화기로 제한 필터 빈을 만든다.
   *
   * <p>JPA 슬라이스 테스트({@code @DataJpaTest})처럼 Jackson 빈이 없는 컨텍스트에서도 필터가 빈 생성에 실패하지 않도록 직렬화기는 없으면 새로
   * 만든다.
   */
  @Bean
  public RateLimitFilter rateLimitFilter(
      RateLimitProperties properties, Clock clock, ObjectProvider<ObjectMapper> objectMapper) {
    ObjectMapper mapper = objectMapper.getIfAvailable(() -> JsonMapper.builder().build());
    return new RateLimitFilter(properties, new FixedWindowRateLimiter(clock), mapper);
  }

  /** 시큐리티 필터 체인 밖(서블릿 컨테이너)에 필터가 중복 등록되지 않게 등록을 비활성화한다. */
  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimitFilter rateLimitFilter) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(rateLimitFilter);
    registration.setEnabled(false);
    return registration;
  }
}
