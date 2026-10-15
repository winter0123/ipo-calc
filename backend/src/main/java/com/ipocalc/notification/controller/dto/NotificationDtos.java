package com.ipocalc.notification.controller.dto;

/** 알림 API 요청·응답 DTO 모음. */
public final class NotificationDtos {

  private NotificationDtos() {}

  /** VAPID 공개키 응답. enabled=false이면 publicKey는 null. */
  public record VapidKeyResponse(boolean enabled, String publicKey) {}

  /** 브라우저 PushSubscription.toJSON() 의 keys 부분. */
  public record Keys(String p256dh, String auth) {}

  /** 구독 등록 요청(PushSubscription.toJSON() 형태). */
  public record SubscribeRequest(String endpoint, Keys keys) {}

  /** 구독 해제 요청. */
  public record UnsubscribeRequest(String endpoint) {}

  /** 알림 종류 설정(요청·응답 공용). */
  public record PreferencesDto(boolean subscriptionDeadline, boolean listingDay) {}

  /** 테스트 알림 결과: 성공한 기기 수. */
  public record TestResponse(int sent) {}
}
