package com.ipocalc.notification.service;

import com.ipocalc.notification.config.WebPushProperties;
import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 08:30 KST에 오늘 알림을 보낸다. {@code app.notification.enabled=false}이거나 VAPID 키가 없으면 하지 않는다. */
@Component
@Slf4j
@ConditionalOnProperty(
    name = "app.notification.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotificationScheduler {

  private final NotificationDispatchService dispatchService;
  private final WebPushProperties properties;
  private final Clock clock;

  /** 의존성을 주입받는다. */
  public NotificationScheduler(
      NotificationDispatchService dispatchService, WebPushProperties properties, Clock clock) {
    this.dispatchService = dispatchService;
    this.properties = properties;
    this.clock = clock;
  }

  /** 매일 08:30 KST 실행. */
  @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
  public void run() {
    if (!properties.isConfigured()) {
      log.info("푸시 알림 건너뜀: VAPID 키가 설정되지 않았습니다");
      return;
    }
    try {
      int sent = dispatchService.dispatch(LocalDate.now(clock));
      log.info("푸시 알림 발송 완료: {}건", sent);
    } catch (RuntimeException e) {
      log.error("푸시 알림 발송 중 예외: {}", e.getClass().getSimpleName());
    }
  }
}
