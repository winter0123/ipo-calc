package com.vibeaiteam.accountauthentication.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * 요청 제한 설정이 필터 빈을 만들고 서블릿 자동 등록을 막는지 검증한다.
 *
 * <p>테스트에 {@code @Configuration} 중첩 클래스를 두면 애플리케이션 컴포넌트 스캔({@code com.vibeaiteam})에 함께 잡혀 다른 테스트
 * 컨텍스트까지 오염된다. 그래서 필요한 빈은 러너에 직접 등록한다.
 */
class RateLimitConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(RateLimitConfiguration.class)
          .withBean(Clock.class, () -> Clock.systemUTC())
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(RateLimitProperties.class, RateLimitProperties::new);

  @Test
  void registersFilterBeanWithServletRegistrationDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RateLimitFilter.class);
          assertThat(context).hasSingleBean(FilterRegistrationBean.class);
          FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
          assertThat(registration.isEnabled()).isFalse();
          assertThat(registration.getFilter()).isSameAs(context.getBean(RateLimitFilter.class));
        });
  }

  @Test
  void doesNotCreateFilterBeanWhenDisabled() {
    contextRunner
        .withPropertyValues("app.rate-limit.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RateLimitFilter.class);
              assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
            });
  }
}
