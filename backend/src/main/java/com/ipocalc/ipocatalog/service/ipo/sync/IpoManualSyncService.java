package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncRangeRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.exception.IpoSyncRangeValidationException;
import com.ipocalc.ipocatalog.exception.OpenDartKeyMissingException;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSyncService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiProperties;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 관리자 "지금 가져오기". 새벽 스케줄러(04:30 목록·일정, 05:00 상세)와 같은 서비스를 같은 순서로 부른다.
 * 기간은 스케줄러와 같은 {@code app.ipo-sync.days-back}(기본 90일)이라 빈 DB도 한 번에 채운다.
 * 범위 지정 시(YYYY-MM) 한 달씩 순차 처리하며 백그라운드에서 실행된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoManualSyncService {

  private final IpoSyncService ipoSyncService;
  private final IpoDetailSyncService ipoDetailSyncService;
  private final OpenDartApiProperties openDartProperties;
  private final IpoSyncStateManager syncStateManager;
  // 같은 타입의 taskScheduler와 구분하려고 빈 이름(ipoSyncExecutor)과 같은 이름으로 주입받는다
  private final Executor ipoSyncExecutor;

  @Value("${app.ipo-sync.days-back:90}")
  private int daysBack;

  /** 허용된 최대 범위: 24개월. */
  private static final int MAX_MONTHS = 24;

  /** 최소 시작 월: 2015-01 (DART 데이터 실존 시점 근사). */
  private static final YearMonth MIN_FROM_MONTH = YearMonth.of(2015, 1);

  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

  /**
   * 목록 동기화 → 상세 동기화를 한 번 실행한다(기본 동작: 최근 N일).
   *
   * @return 건수 요약. 이미 실행 중이면 IpoSyncBusyException, 키가 없으면 OpenDartKeyMissingException
   */
  public IpoManualSyncResult syncNow() {
    if (openDartProperties.isApiKeyMissing()) {
      throw new OpenDartKeyMissingException();
    }
    return ipoSyncService.runExclusively(this::runDefault);
  }

  /**
   * 범위 지정 동기화를 백그라운드에서 시작한다.
   *
   * @param request 범위 요청 (fromMonth, toMonth). 비어 있으면 기본 동작과 동일하게 동기 실행.
   * @return 즉시 반환되는 상태 객체 (state=RUNNING). 실제 작업은 백그라운드에서 진행.
   * @throws IpoSyncRangeValidationException 범위 검증 실패 시
   * @throws IpoSyncBusyException 이미 실행 중일 때
   * @throws OpenDartKeyMissingException 키가 없을 때
   */
  public IpoSyncStatusResponse syncRange(IpoSyncRangeRequest request) {
    if (openDartProperties.isApiKeyMissing()) {
      throw new OpenDartKeyMissingException();
    }

    // 범위 검증
    YearMonth fromMonth = null;
    YearMonth toMonth = null;
    if (request.hasRange()) {
      try {
        if (request.getFromMonth() != null && !request.getFromMonth().isBlank()) {
          fromMonth = YearMonth.parse(request.getFromMonth(), MONTH_FORMATTER);
        }
        if (request.getToMonth() != null && !request.getToMonth().isBlank()) {
          toMonth = YearMonth.parse(request.getToMonth(), MONTH_FORMATTER);
        }
      } catch (DateTimeParseException e) {
        throw new IpoSyncRangeValidationException("월 형식(YYYY-MM)이 올바르지 않습니다: " + e.getMessage());
      }

      validateRange(fromMonth, toMonth);
    }

    // 락 시도 및 상태 초기화는 백그라운드 작업에서 수행 (동시 실행 방지)
    // 여기서는 검증만 하고 바로 202 반환, 백그라운드에서 runExclusively 호출

    // 상태를 RUNNING으로 설정 (범위 지정인 경우)
    if (fromMonth != null && toMonth != null) {
      int monthsTotal = calculateMonthsTotal(fromMonth, toMonth);
      syncStateManager.startRangeSync(
          fromMonth.format(MONTH_FORMATTER),
          toMonth.format(MONTH_FORMATTER),
          monthsTotal);
    } else {
      syncStateManager.startDefaultSync();
    }

    // 백그라운드 실행 (람다에서 사용하기 위해 effectively final 변수로 복사)
    final YearMonth finalFromMonth = fromMonth;
    final YearMonth finalToMonth = toMonth;
    ipoSyncExecutor.execute(() -> runBackground(finalFromMonth, finalToMonth));

    // 즉시 상태 반환
    return syncStateManager.getStatus();
  }

  /** 범위 검증. */
  private void validateRange(YearMonth fromMonth, YearMonth toMonth) {
    YearMonth nowMonth = YearMonth.now();

    if (fromMonth != null && fromMonth.isBefore(MIN_FROM_MONTH)) {
      throw new IpoSyncRangeValidationException(
          "fromMonth는 2015-01 이후여야 합니다. 입력값: " + fromMonth.format(MONTH_FORMATTER));
    }
    if (fromMonth != null && toMonth != null && fromMonth.isAfter(toMonth)) {
      throw new IpoSyncRangeValidationException(
          "fromMonth는 toMonth보다 이전이거나 같아야 합니다. fromMonth=" + fromMonth + ", toMonth=" + toMonth);
    }
    if (toMonth != null && toMonth.isAfter(nowMonth)) {
      throw new IpoSyncRangeValidationException(
          "toMonth는 이번 달(" + nowMonth.format(MONTH_FORMATTER)
              + ") 이후일 수 없습니다. 입력값: " + toMonth.format(MONTH_FORMATTER));
    }
    if (fromMonth != null && toMonth != null) {
      long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
      if (monthsBetween > MAX_MONTHS) {
        throw new IpoSyncRangeValidationException(
            "동기화 범위는 최대 24개월까지 가능합니다. 요청 범위: " + monthsBetween + "개월");
      }
    }
  }

  /** 전체 대상 월 수 계산. */
  private int calculateMonthsTotal(YearMonth fromMonth, YearMonth toMonth) {
    return (int) java.time.temporal.ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
  }

  /** 백그라운드에서 실행할 실제 동기화 작업. */
  private void runBackground(YearMonth fromMonth, YearMonth toMonth) {
    try {
      if (fromMonth != null && toMonth != null) {
        runRangeSync(fromMonth, toMonth);
      } else {
        runDefaultSync();
      }
    } catch (Exception e) {
      // 예외 클래스명만 로그에 남김 (키·URL 노출 금지)
      log.error("백그라운드 동기화 중 예외 발생: {}", e.getClass().getSimpleName());
      syncStateManager.markFailed("동기화 중 오류 발생: " + e.getClass().getSimpleName());
    }
  }

  /** 범위 지정 동기화 실행 (한 달씩). */
  private void runRangeSync(YearMonth fromMonth, YearMonth toMonth) {
    YearMonth current = fromMonth;
    YearMonth nowMonth = YearMonth.now();
    LocalDate today = LocalDate.now();

    List<Ipo> createdOrUpdatedIpos = new java.util.ArrayList<>();

    while (!current.isAfter(toMonth)) {
      // 락을 걸고 한 달 처리 (이미 runExclusively 안에서 처리됨)
      IpoSyncResult monthResult = ipoSyncService.syncByMonthRange(current, current);

      // 이 달에 생성/갱신된 공모주(corpCode 있음) 수집 → 상세 동기화 대상
      // (IpoSyncResult에는 개별 IPO 목록이 없으므로, 동기화 후 repo에서
      // 해당 월 조건으로 조회) 대신 여기서 간단히: 동기화가 중단되지 않았고
      // 생성/갱신 건수가 있으면 상세 동기화 돌림
      int detailSaved = 0;
      if (!monthResult.isAborted()
          && (monthResult.getCreated() > 0 || monthResult.getUpdated() > 0)) {
        // 해당 월의 청약 시작일을 가진 공모주 중 corpCode 있는 것 조회
        LocalDate monthStart = current.atDay(1);
        LocalDate monthEnd = current.atEndOfMonth();
        if (monthEnd.isAfter(today)) {
          monthEnd = today;
        }
        List<Ipo> monthIpos =
            ipoSyncService
                .getIpoRepository()
                .findBySubscriptionStartDateBetweenAndCorpCodeIsNotNullAndDeletedAtIsNull(
                    monthStart, monthEnd);
        detailSaved = ipoDetailSyncService.syncForIpos(monthIpos);
        createdOrUpdatedIpos.addAll(monthIpos);
      }

      // 상태 업데이트
      syncStateManager.incrementMonthDone(monthResult, detailSaved);

      // 중단 시 종료
      if (monthResult.isAborted()) {
        syncStateManager.markFailed("동기화 중단: " + monthResult.getAbortReason());
        return;
      }

      current = current.plusMonths(1);
    }

    // 모든 월 완료
    syncStateManager.completeRangeSync();
    log.info("범위 동기화 전체 완료: fromMonth={}, toMonth={}", fromMonth, toMonth);
  }

  /** 기본 동기화 실행 (동기). */
  private void runDefaultSync() {
    IpoManualSyncResult result = runDefault();
    syncStateManager.completeDefaultSync(result);
  }

  /** 기본 동기화 로직 (기존 run 메서드). */
  private IpoManualSyncResult runDefault() {
    IpoSyncResult r = ipoSyncService.sync(daysBack);
    int detail = r.isAborted() ? 0 : ipoDetailSyncService.syncRecent();
    log.info(
        "수동 IPO 동기화 완료: created={}, updated={}, failures={}, detail={}, aborted={}",
        r.getCreated(),
        r.getUpdated(),
        r.getFailureCount(),
        detail,
        r.isAborted());
    return new IpoManualSyncResult(
        r.getCreated(),
        r.getUpdated(),
        r.getSkippedManual() + r.getSkippedNonIpo(),
        r.getFailureCount(),
        r.getScheduleMerged(),
        detail,
        r.isAborted());
  }

  /**
   * 현재 동기화 상태를 반환한다.
   *
   * @return 상태 객체 (IDLE/RUNNING/DONE/FAILED)
   */
  public IpoSyncStatusResponse getSyncStatus() {
    return syncStateManager.getStatus();
  }
}
