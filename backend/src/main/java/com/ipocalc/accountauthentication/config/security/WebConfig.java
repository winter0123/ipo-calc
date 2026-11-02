package com.ipocalc.accountauthentication.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/** MVC 설정 - 인터셉터 등록. */
@Configuration
@RequiredArgsConstructor
public class WebConfig
    implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

  private final ReadOnlyImpersonationInterceptor readOnlyImpersonationInterceptor;

  @Override
  public void addInterceptors(
      org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
    registry
        .addInterceptor(readOnlyImpersonationInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico");
  }
}
