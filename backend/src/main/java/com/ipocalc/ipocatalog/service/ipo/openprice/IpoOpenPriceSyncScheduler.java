package com.ipocalc.ipocatalog.service.ipo.openprice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 상장일 시초가 동기화 스케줄러.
 *
 * <p>매일 14:30 KST에 실행되어 상장일이 지난 공모주의 시초가를 수집한다. {@code app.open-price-sync.enabled=true}일 때만
 * 활성화된다(기본값 true).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "app.open-price-sync.enabled", havingValue = "true", matchIfMissing = true)
public class IpoOpenPriceSyncScheduler {

  private final IpoOpenPriceSyncService ipoOpenPriceSyncService;

  /**
   * 매일 14:30 KST 실행.
   *
   * <p>cron 표현식: 초 분 시 일 월 요일 {@code 0 30 14 * * *} = 매일 14:30:00 zone: Asia/Seoul (KST)
   */
  @Scheduled(cron = "0 30 14 * * *", zone = "Asia/Seoul")
  public void syncDaily() {
    log.info("일일 시초가 동기화 시작");
    IpoOpenPriceSyncService.SyncResult result = ipoOpenPriceSyncService.syncOpenPrices();
    log.info("일일 시초가 동기화 완료: {}", result.summary());
  }
}
