package com.vibeaiteam.notification.controller;

import com.vibeaiteam.accountauthentication.controller.dto.SuccessResponse;
import com.vibeaiteam.notification.controller.dto.NotificationDtos.PreferencesDto;
import com.vibeaiteam.notification.controller.dto.NotificationDtos.SubscribeRequest;
import com.vibeaiteam.notification.controller.dto.NotificationDtos.TestResponse;
import com.vibeaiteam.notification.controller.dto.NotificationDtos.UnsubscribeRequest;
import com.vibeaiteam.notification.controller.dto.NotificationDtos.VapidKeyResponse;
import com.vibeaiteam.notification.domain.model.NotificationPreference;
import com.vibeaiteam.notification.exception.NotificationValidationException;
import com.vibeaiteam.notification.service.NotificationSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 웹 푸시 구독·알림 설정 API. 모두 로그인이 필요하다. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationSubscriptionService service;

  /** VAPID 공개키. 서버에 키가 없으면 enabled=false(200)로 알려 화면이 안내를 보이게 한다. */
  @GetMapping("/vapid-public-key")
  public VapidKeyResponse vapidPublicKey() {
    return new VapidKeyResponse(service.isEnabled(), service.publicKey());
  }

  /** 이 기기의 구독을 등록한다(같은 endpoint면 갱신). 푸시 비활성이면 503. */
  @PostMapping("/subscriptions")
  public SuccessResponse subscribe(
      @RequestBody SubscribeRequest request,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    if (request == null || request.keys() == null) {
      throw new NotificationValidationException("구독 정보가 올바르지 않습니다");
    }
    service.subscribe(
        request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent);
    return SuccessResponse.of("이 기기에서 알림을 받습니다");
  }

  /** 본인 기기의 구독을 해제한다. */
  @DeleteMapping("/subscriptions")
  public SuccessResponse unsubscribe(@RequestBody UnsubscribeRequest request) {
    service.unsubscribe(request == null ? null : request.endpoint());
    return SuccessResponse.of("이 기기의 알림을 해제했습니다");
  }

  /** 알림 종류 설정 조회. */
  @GetMapping("/preferences")
  public PreferencesDto preferences() {
    return toDto(service.preferences());
  }

  /** 알림 종류 설정 저장. */
  @PutMapping("/preferences")
  public PreferencesDto updatePreferences(@RequestBody PreferencesDto request) {
    return toDto(service.updatePreferences(request.subscriptionDeadline(), request.listingDay()));
  }

  /** 내 모든 기기로 테스트 알림 발송. */
  @PostMapping("/test")
  public TestResponse test() {
    return new TestResponse(service.sendTest());
  }

  private PreferencesDto toDto(NotificationPreference p) {
    return new PreferencesDto(p.isSubscriptionDeadline(), p.isListingDay());
  }
}
