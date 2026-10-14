package com.vibeaiteam.ipocatalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** IPO 동기화용 백그라운드 실행기 설정. */
@Configuration
public class IpoSyncExecutorConfig {

  /**
   * IPO 동기화 전용 단일 스레드 실행기.
   *
   * <p>동기화는 순차 처리가 원칙(한 달씩, 락 공유)이므로 단일 스레드로 충분하다.
   * {@code IpoSyncService.runLock}과 함께 사용하여 스케줄러·관리자 실행 간 동시 실행을 방지한다.
   */
  @Bean(name = "ipoSyncExecutor")
  public ThreadPoolTaskExecutor ipoSyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("ipo-sync-");
    executor.setRejectedExecutionHandler((r, e) -> {
      // 큐가 꽉 차면 실행하지 않고 로그만 남김 (이미 실행 중이란 뜻)
      log.warn("IPO 동기화 실행기 큐 포화 - 작업 거절 (이미 실행 중일 가능성)");
    });
    executor.initialize();
    return executor;
  }

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(IpoSyncExecutorConfig.class);
}