package com.vibeaiteam.notification.service;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.notification.config.WebPushProperties;
import com.vibeaiteam.notification.domain.model.NotificationPreference;
import com.vibeaiteam.notification.domain.model.PushSubscription;
import com.vibeaiteam.notification.exception.NotificationDisabledException;
import com.vibeaiteam.notification.exception.NotificationNotFoundException;
import com.vibeaiteam.notification.exception.NotificationValidationException;
import com.vibeaiteam.notification.repository.NotificationPreferenceRepository;
import com.vibeaiteam.notification.repository.PushSubscriptionRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 푸시 구독 등록·해제, 알림 종류 설정, 테스트 발송. 모두 현재 로그인 계정 기준이다. */
@Service
public class NotificationSubscriptionService {

  /** endpoint 최대 길이(컬럼 길이와 같다). */
  static final int MAX_ENDPOINT_LENGTH = 1000;

  private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_\\-=+/]+");

  /**
   * 서버가 이 주소로 POST하므로(SSRF 방지) 주요 브라우저의 푸시 서비스 도메인만 받는다.
   * Chrome·Edge·Opera(FCM: fcm.googleapis.com 과 실측한 jmt17.google.com 같은 google.com 하위),
   * Firefox(Mozilla), Safari(Apple), 구형 Edge(Windows).
   */
  private static final List<String> ALLOWED_HOST_SUFFIXES =
      List.of(
          "fcm.googleapis.com",
          "google.com",
          "push.services.mozilla.com",
          "push.apple.com",
          "notify.windows.com");

  private final PushSubscriptionRepository subscriptions;
  private final NotificationPreferenceRepository preferences;
  private final PushDeliveryService delivery;
  private final SecurityUtils securityUtils;
  private final WebPushProperties properties;

  /** 의존성을 주입받는다. */
  public NotificationSubscriptionService(
      PushSubscriptionRepository subscriptions,
      NotificationPreferenceRepository preferences,
      PushDeliveryService delivery,
      SecurityUtils securityUtils,
      WebPushProperties properties) {
    this.subscriptions = subscriptions;
    this.preferences = preferences;
    this.delivery = delivery;
    this.securityUtils = securityUtils;
    this.properties = properties;
  }

  /** 푸시 기능 사용 가능 여부. */
  public boolean isEnabled() {
    return properties.isConfigured();
  }

  /** VAPID 공개키(비활성이면 null). */
  public String publicKey() {
    return isEnabled() ? properties.getPublicKey() : null;
  }

  /** 구독을 등록한다. 같은 endpoint가 있으면 키·소유 계정을 갱신하고 다시 활성화한다. */
  @Transactional
  public void subscribe(String endpoint, String p256dh, String auth, String userAgent) {
    requireEnabled();
    validate(endpoint, p256dh, auth);
    Long accountId = securityUtils.getCurrentAccountId();
    PushSubscription sub = subscriptions.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
    sub.setEndpoint(endpoint);
    sub.setAccountId(accountId);
    sub.setP256dh(p256dh);
    sub.setAuth(auth);
    sub.setUserAgent(
        userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
    sub.setStatus(RecordStatus.ACTIVE);
    sub.setDeletedAt(null);
    subscriptions.save(sub);
  }

  /** 본인 소유의 활성 구독만 해제한다. 없거나 남의 것이면 404. */
  @Transactional
  public void unsubscribe(String endpoint) {
    Long accountId = securityUtils.getCurrentAccountId();
    PushSubscription sub =
        subscriptions
            .findByEndpoint(endpoint == null ? "" : endpoint)
            .filter(
                s -> accountId.equals(s.getAccountId()) && s.getStatus() == RecordStatus.ACTIVE)
            .orElseThrow(() -> new NotificationNotFoundException("해당 기기의 알림 구독을 찾을 수 없습니다"));
    sub.cancel();
    subscriptions.save(sub);
  }

  /** 알림 종류 설정을 조회한다(행이 없으면 모두 켜짐). */
  @Transactional(readOnly = true)
  public NotificationPreference preferences() {
    Long accountId = securityUtils.getCurrentAccountId();
    return preferences.findById(accountId).orElseGet(() -> defaults(accountId));
  }

  /** 알림 종류 설정을 저장한다. */
  @Transactional
  public NotificationPreference updatePreferences(
      boolean subscriptionDeadline, boolean listingDay) {
    Long accountId = securityUtils.getCurrentAccountId();
    NotificationPreference pref =
        preferences.findById(accountId).orElseGet(() -> defaults(accountId));
    pref.setSubscriptionDeadline(subscriptionDeadline);
    pref.setListingDay(listingDay);
    return preferences.save(pref);
  }

  /** 내 모든 기기로 테스트 알림을 보내고 성공한 기기 수를 돌려준다. 기기가 없으면 400. */
  public int sendTest() {
    requireEnabled();
    Long accountId = securityUtils.getCurrentAccountId();
    if (subscriptions.findByAccountIdAndStatus(accountId, RecordStatus.ACTIVE).isEmpty()) {
      throw new NotificationValidationException("알림을 받도록 등록된 기기가 없습니다");
    }
    return delivery.deliver(
        accountId, "테스트 알림", "알림이 정상적으로 도착했습니다", "/notifications", "test");
  }

  private NotificationPreference defaults(Long accountId) {
    NotificationPreference pref = new NotificationPreference();
    pref.setAccountId(accountId);
    return pref;
  }

  private void requireEnabled() {
    if (!isEnabled()) {
      throw new NotificationDisabledException();
    }
  }

  private void validate(String endpoint, String p256dh, String auth) {
    if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
      throw new NotificationValidationException("endpoint가 올바르지 않습니다");
    }
    if (!isAllowedEndpoint(endpoint)) {
      throw new NotificationValidationException("지원하는 브라우저 푸시 서비스 주소(https)가 아닙니다");
    }
    if (p256dh == null || p256dh.length() > 200 || !BASE64URL.matcher(p256dh).matches()) {
      throw new NotificationValidationException("p256dh 키가 올바르지 않습니다");
    }
    if (auth == null || auth.length() > 100 || !BASE64URL.matcher(auth).matches()) {
      throw new NotificationValidationException("auth 키가 올바르지 않습니다");
    }
  }

  private static boolean isAllowedEndpoint(String endpoint) {
    try {
      URI uri = URI.create(endpoint);
      String host = uri.getHost();
      if (!"https".equals(uri.getScheme()) || host == null) {
        return false;
      }
      String h = host.toLowerCase(Locale.ROOT);
      return ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> h.equals(s) || h.endsWith("." + s));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
