package com.vibeaiteam.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.TaskScheduler;

/** {@link IpoSyncScheduler} 단위 테스트 (Spring 컨텍스트 없이 Mockito만 사용). */
class IpoSyncSchedulerTest {

  @Mock private IpoSyncService ipoSyncService;
  @Mock private TaskScheduler taskScheduler;

  @Captor private ArgumentCaptor<Runnable> runnableCaptor;
  @Captor private ArgumentCaptor<Instant> instantCaptor;

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private IpoSyncScheduler scheduler;
  private AutoCloseable mockitoCloseable;
  private final List<Runnable> capturedRunnables = new ArrayList<>();

  @BeforeEach
  void setUp() {
    mockitoCloseable = MockitoAnnotations.openMocks(this);
    scheduler = new IpoSyncScheduler(ipoSyncService, taskScheduler, FIXED_CLOCK);
    // daysBack 필드는 @Value로 주입되므로 리플렉션으로 설정
    setField(scheduler, "daysBack", 90);
    capturedRunnables.clear();
  }

  @AfterEach
  void tearDown() throws Exception {
    mockitoCloseable.close();
  }

  @Test
  void runSync_normalResult_noRetryScheduled() {
    // given: 정상 결과 (aborted=false, retryable=false)
    IpoSyncResult result =
        IpoSyncResult.builder()
            .created(1)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(List.of())
            .aborted(false)
            .retryable(false)
            .build();
    when(ipoSyncService.sync(90)).thenReturn(result);

    // when
    scheduler.runSync(0);

    // then: 재시도 예약 없음
    verifyNoInteractions(taskScheduler);
    verify(ipoSyncService).sync(90);
  }

  @Test
  void runSync_partialFailure_noRetry() {
    // given: 부분 실패 (failureCount>0, aborted=false)
    IpoSyncResult result =
        IpoSyncResult.builder()
            .created(1)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(2)
            .failures(
                List.of(
                    IpoSyncFailure.builder().corpCode("001").corpName("A").reason("fail1").build(),
                    IpoSyncFailure.builder().corpCode("002").corpName("B").reason("fail2").build()))
            .aborted(false)
            .retryable(false)
            .build();
    when(ipoSyncService.sync(90)).thenReturn(result);

    // when
    scheduler.runSync(0);

    // then: 재시도 예약 없음 (부분 실패는 다음 날 주기에서 자연 재처리)
    verifyNoInteractions(taskScheduler);
  }

  @Test
  void runSync_abortedAndRetryable_schedulesRetry() {
    // given: 중단 + 재시도 가능 (첫 호출만 aborted=true, retryable=true, 두 번째는 정상)
    IpoSyncResult abortedResult =
        IpoSyncResult.builder()
            .created(0)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(List.of())
            .aborted(true)
            .retryable(true)
            .abortReason("API 오류: 시스템 점검")
            .build();
    IpoSyncResult successResult =
        IpoSyncResult.builder()
            .created(1)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(List.of())
            .aborted(false)
            .retryable(false)
            .build();
    when(ipoSyncService.sync(90)).thenReturn(abortedResult, successResult);

    // when
    scheduler.runSync(0);

    // then: taskScheduler.schedule 1회 호출, Instant가 now+5분 근처
    verify(taskScheduler).schedule(runnableCaptor.capture(), instantCaptor.capture());
    Instant scheduledTime = instantCaptor.getValue();
    assertThat(scheduledTime).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

    // 캡처된 Runnable 실행 시 attempt=1로 다시 sync 호출 → 성공 결과 반환
    Runnable retryTask = runnableCaptor.getValue();
    retryTask.run();
    verify(ipoSyncService, times(2)).sync(90); // 최초 1회 + 재시도 1회
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void runSync_abortedRetryableFalse_noRetry() {
    // given: 중단인데 재시도 불가 (인증 오류 또는 020)
    IpoSyncResult result =
        IpoSyncResult.builder()
            .created(0)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(List.of())
            .aborted(true)
            .retryable(false)
            .abortReason("인증 오류: 미등록 키")
            .build();
    when(ipoSyncService.sync(90)).thenReturn(result);

    // when
    scheduler.runSync(0);

    // then: 재시도 예약 없음
    verifyNoInteractions(taskScheduler);
  }

  @Test
  void runSync_syncThrowsRuntimeException_schedulesRetry() {
    // given: sync()가 RuntimeException 던짐 (네트워크 오류 등) - 첫 호출만 예외, 두 번째는 정상
    IpoSyncResult successResult =
        IpoSyncResult.builder()
            .created(1)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(List.of())
            .aborted(false)
            .retryable(false)
            .build();
    when(ipoSyncService.sync(90))
        .thenThrow(new RuntimeException("Connection timeout"))
        .thenReturn(successResult);

    // when
    scheduler.runSync(0);

    // then: 재시도 예약됨 (스케줄러 메서드 자체는 예외를 밖으로 던지지 않음)
    verify(taskScheduler).schedule(runnableCaptor.capture(), instantCaptor.capture());
    Instant scheduledTime = instantCaptor.getValue();
    assertThat(scheduledTime).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

    // 재시도 실행 시 성공
    runnableCaptor.getValue().run();
    verify(ipoSyncService, times(2)).sync(90);
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void runSync_syncThrowsIllegalStateException_noRetry() {
    // given: sync()가 IllegalStateException 던짐 (API 키 미설정)
    doThrow(new IllegalStateException("OpenDART API 키가 설정되지 않았습니다")).when(ipoSyncService).sync(90);

    // when
    scheduler.runSync(0);

    // then: 재시도 예약 없음, 예외 밖으로 안 나감
    verifyNoInteractions(taskScheduler);
  }

  @Test
  void runSync_threeRetriesThenStops() {
    // given: 4번 모두 실패 (aborted=true, retryable=true) - 모든 schedule 호출을 캡처
    IpoSyncResult abortedResult =
        IpoSyncResult.builder()
            .created(0)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(List.of())
            .aborted(true)
            .retryable(true)
            .abortReason("API 오류: 시스템 점검")
            .build();

    when(ipoSyncService.sync(90)).thenReturn(abortedResult);
    List<Instant> scheduledTimes = new ArrayList<>();
    when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
        .thenAnswer(
            invocation -> {
              capturedRunnables.add(invocation.getArgument(0));
              scheduledTimes.add(invocation.getArgument(1));
              return null;
            });

    scheduler.runSync(0);
    assertThat(capturedRunnables).hasSize(1);
    capturedRunnables.get(0).run();
    assertThat(capturedRunnables).hasSize(2);
    capturedRunnables.get(1).run();
    assertThat(capturedRunnables).hasSize(3);
    capturedRunnables.get(2).run(); // 4번째 실패 → 더 이상 예약하지 않는다
    assertThat(capturedRunnables).hasSize(3);

    verify(ipoSyncService, times(4)).sync(90);
    verify(taskScheduler, times(3)).schedule(any(Runnable.class), any(Instant.class));
    assertThat(scheduledTimes)
        .containsExactly(
            NOW.plus(Duration.ofMinutes(5)),
            NOW.plus(Duration.ofMinutes(10)),
            NOW.plus(Duration.ofMinutes(20)));
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field " + fieldName, e);
    }
  }
}
