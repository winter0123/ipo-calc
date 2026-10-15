package com.ipocalc.ipocatalog.service.ipo.detail;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 공모주 일정 동기화(04:30) 뒤 매일 05:00 KST에 공모주 상세 정보를 DART에서 갱신한다. 일정 동기화와 같은 설정으로 켜고 끈다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ipo-sync.enabled", havingValue = "true", matchIfMissing = true)
public class IpoDetailSyncScheduler {

  private final IpoDetailSyncService syncService;

  /** 매일 05:00 KST. */
  @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
  public void syncDaily() {
    syncService.syncRecent();
  }
}
