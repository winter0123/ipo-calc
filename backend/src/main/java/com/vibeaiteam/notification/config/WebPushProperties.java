package com.vibeaiteam.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 웹 푸시(VAPID) 설정. 키가 하나라도 비어 있으면 푸시 기능 전체가 비활성이다. */
@Component
public class WebPushProperties {

  private final String publicKey;
  private final String privateKey;
  private final String subject;

  /** VAPID 공개키·개인키·연락처(mailto:)를 주입받는다. */
  public WebPushProperties(
      @Value("${app.webpush.public-key:}") String publicKey,
      @Value("${app.webpush.private-key:}") String privateKey,
      @Value("${app.webpush.subject:}") String subject) {
    this.publicKey = publicKey.trim();
    this.privateKey = privateKey.trim();
    this.subject = subject.trim();
  }

  /** 세 값이 모두 채워져 푸시를 쓸 수 있으면 true. */
  public boolean isConfigured() {
    return !publicKey.isEmpty() && !privateKey.isEmpty() && !subject.isEmpty();
  }

  public String getPublicKey() {
    return publicKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public String getSubject() {
    return subject;
  }
}
