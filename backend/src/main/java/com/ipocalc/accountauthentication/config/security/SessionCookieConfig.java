package com.ipocalc.accountauthentication.config.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session(JDBC)이 내려주는 세션 쿠키 설정. Boot의 {@code server.servlet.session.cookie.*} 자동 매핑은
 * 내장 서버가 있을 때만
 * 적용돼 MockMvc 테스트와 달라지므로, 같은 프로퍼티를 읽어 직접 만든다. 이름은 기존 JSESSIONID를 유지한다.
 */
@Configuration
public class SessionCookieConfig {

  /** 쿠키 Max-Age는 세션 비활동 만료와 같아 응답마다 다시 써서 슬라이딩된다. */
  @Bean
  public DefaultCookieSerializer cookieSerializer(
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
      @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
      @Value("${server.servlet.session.cookie.max-age:#{null}}") Duration maxAge) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("JSESSIONID");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(secure);
    serializer.setSameSite(
        sameSite.isEmpty()
            ? sameSite
            : sameSite.substring(0, 1).toUpperCase() + sameSite.substring(1).toLowerCase());
    if (maxAge != null) {
      serializer.setCookieMaxAge((int) maxAge.toSeconds());
    }
    return serializer;
  }
}
