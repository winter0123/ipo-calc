package com.vibeaiteam.ipocatalog.service.ipo.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IPO 자동 동기화 스케줄러.
 *
 * <p>매일 04:30 KST에 실행되어 최근 N일(기본 90일) 내 IPO 정보를 동기화한다. {@code app.ipo-sync.enabled=true}일 때만
 * 활성화된다(기본값 true).
 *
 * <p>실패 시 지수 백오프로 최대 3회 재시도한다(5분 → 10분 → 20분). 재시도 대상: (a) {@code sync()}가 {@code
 * IllegalStateException}이 아닌 {@code RuntimeException}을 던진 경우, (b) {@code result.isAborted()}이고
 * {@code result.isRetryable()}인 경우. 재시도하지 않음: 정상 종료(부분 실패 포함), 인증 오류, 요청 제한 020, {@code
 * IllegalStateException}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.ipo-sync.enabled", havingValue = "true", matchIfMissing = true)
public class IpoSyncScheduler {

  private static final List<Duration> RETRY_DELAYS =
      List.of(Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(20));

  private final IpoSyncService ipoSyncService;
  private final TaskScheduler taskScheduler;

  @Value("${app.ipo-sync.days-back:90}")
  private int daysBack;

  /**
   * 매일 04:30 KST 실행.
   *
   * <p>cron 표현식: 초 분 시 일 월 요일 {@code 0 30 4 * * *} = 매일 04:30:00 zone: Asia/Seoul (KST)
   */
  @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
  public void syncDaily() {
    log.info("일일 IPO 동기화 시작: daysBack={}", daysBack);
    runSync(0);
  }

  /**
   * 재시도 포함 동기화 실행.
   *
   * @param attempt 현재 시도 횟수 (0 = 최초 실행, 1~3 = 재시도)
   */
  public void runSync(int attempt) {
    IpoSyncResult result;
    try {
      result = ipoSyncService.sync(daysBack);
    } catch (IllegalStateException e) {
      log.warn("IPO 동기화 설정 오류로 중단 (재시도 안 함): {}", e.getClass().getSimpleName());
      return;
    } catch (RuntimeException e) {
      // HTTP 클라이언트 예외 메시지·스택에 쿼리스트링의 API 키가 들어갈 수 있어 클래스명만 남긴다
      log.error("IPO 동기화 중 예외 발생 (attempt={}): {}", attempt, e.getClass().getSimpleName());
      scheduleRetryIfNeeded(attempt);
      return;
    }

    // 결과 요약 로그
    log.info(
        "IPO 동기화 완료: created={}, updated={}, skippedManual={}, skippedNonIpo={}, "
            + "failures={}, aborted={}, retryable={}",
        result.getCreated(),
        result.getUpdated(),
        result.getSkippedManual(),
        result.getSkippedNonIpo(),
        result.getFailureCount(),
        result.isAborted(),
        result.isRetryable());

    // 건별 실패 로그 (API 키, URI 절대 로그에 남기지 않음)
    for (IpoSyncFailure failure : result.getFailures()) {
      log.error("IPO 동기화 실패: corpCode={}, reason={}", failure.getCorpCode(), failure.getReason());
    }

    // 재시도 판단
    if (result.isAborted() && result.isRetryable()) {
      scheduleRetryIfNeeded(attempt);
    }
    // aborted지만 retryable=false(인증/020) 또는 정상 종료(aborted=false) → 재시도 안 함
  }

  private void scheduleRetryIfNeeded(int attempt) {
    if (attempt >= RETRY_DELAYS.size()) {
      log.error("최대 재시도 횟수({}) 초과 — 다음 일일 주기에 재시도", RETRY_DELAYS.size());
      return;
    }

    Duration delay = RETRY_DELAYS.get(attempt);
    Instant nextRun = Instant.now().plus(delay);

    log.info("IPO 동기화 재시도 예약: attempt={}, delay={}, nextRun={}", attempt + 1, delay, nextRun);

    taskScheduler.schedule(() -> runSync(attempt + 1), nextRun);
  }
}
