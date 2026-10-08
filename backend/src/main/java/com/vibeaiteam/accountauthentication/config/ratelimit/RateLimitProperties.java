package com.vibeaiteam.accountauthentication.config.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** IP별 요청 제한(rate limit) 설정 값. */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

  /** 제한 기능 켜기/끄기(끄면 필터 빈이 만들어지지 않는다). */
  private boolean enabled = true;

  /** 공개 조회(GET /ipos, /market-holidays) 분당 한도. */
  private int publicReadPerMinute = 120;

  /** 로그인·가입(POST /auth/login, POST /accounts) 분당 한도. */
  private int authPerMinute = 20;

  /** X-Forwarded-For 헤더를 신뢰할지 여부(기본 false, 위조 방지). */
  private boolean trustForwardedHeader = false;

  /** 제한 기능이 켜져 있는지 반환한다. */
  public boolean isEnabled() {
    return enabled;
  }

  /** 제한 기능 켜기/끄기를 설정한다(설정 바인딩 전용). */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /** 공개 조회 분당 한도를 반환한다. */
  public int getPublicReadPerMinute() {
    return publicReadPerMinute;
  }

  /** 공개 조회 분당 한도를 설정한다(설정 바인딩 전용). */
  public void setPublicReadPerMinute(int publicReadPerMinute) {
    this.publicReadPerMinute = publicReadPerMinute;
  }

  /** 로그인·가입 분당 한도를 반환한다. */
  public int getAuthPerMinute() {
    return authPerMinute;
  }

  /** 로그인·가입 분당 한도를 설정한다(설정 바인딩 전용). */
  public void setAuthPerMinute(int authPerMinute) {
    this.authPerMinute = authPerMinute;
  }

  /** X-Forwarded-For 신뢰 여부를 반환한다. */
  public boolean isTrustForwardedHeader() {
    return trustForwardedHeader;
  }

  /** X-Forwarded-For 신뢰 여부를 설정한다(설정 바인딩 전용). */
  public void setTrustForwardedHeader(boolean trustForwardedHeader) {
    this.trustForwardedHeader = trustForwardedHeader;
  }
}
