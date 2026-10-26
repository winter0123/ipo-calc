package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IPO 자동 동기화 서비스.
 *
 * <p>OpenDART DS001(공시검색)으로 회사 목록을 조회하고 DS006(지분증권 상세) 결과를 검증해 회사별로 처리한다. IPO 저장은 {@link
 * IpoSyncPersistence}에 위임하고, OpenDART 루프가 중단 없이 끝나면 ustockplus 일정을 병합한다. 주요 규칙:
 *
 * <ul>
 *   <li>DS001 응답에서 {@code stock_code}가 있는 건은 IPO가 아니므로 건너뛴다.
 *   <li>DS006 상세를 검증한 뒤 회사별 독립 transaction 안에서 {@link IpoSyncPersistence}를 호출한다.
 *   <li>인증 오류와 요청 제한은 전체 실행을 중단하고, 그 외 회사별 오류는 실패에 기록한 뒤 계속 처리한다.
 *   <li>OpenDART 루프가 중단 없이 완료되면 ustockplus 일정을 병합한다. 병합 실패는 OpenDART 결과를 바꾸지 않는다.
 *   <li>로그에 API 키를 절대 남기지 않는다 (키는 URL 쿼리 파라미터).
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoSyncService {
  private final OpenDartCorpDisclosureClient corpDisclosureClient;
  private final OpenDartEquitySecuritiesClient equitySecuritiesClient;
  private final IpoSyncPersistence ipoSyncPersistence;
  private final TransactionTemplate transactionTemplate;
  private final IpoScheduleMergeService ipoScheduleMergeService;
  private final Clock clock;

  /** 동기화 중복 실행 방지(스케줄러·관리자 수동 실행 공용). 같은 스레드는 재진입한다. */
  private final ReentrantLock runLock = new ReentrantLock();

  /** IPO 저장소 접근자 (범위 동기화 후 상세 동기화 대상 조회용). */
  public IpoRepository getIpoRepository() {
    return ipoSyncPersistence.getIpoRepository();
  }

  /**
   * 락을 잡고 작업을 실행한다. 이미 다른 동기화가 돌고 있으면 기다리지 않고 {@link IpoSyncBusyException}.
   *
   * @param task 락 안에서 실행할 작업
   * @return 작업 결과
   */
  public <T> T runExclusively(Supplier<T> task) {
    if (!runLock.tryLock()) {
      throw new IpoSyncBusyException();
    }
    try {
      return task.get();
    } finally {
      runLock.unlock();
    }
  }

  /**
   * 최근 지정된 일수 내 IPO 정보를 동기화한다(동시 실행 시 {@link IpoSyncBusyException}).
   *
   * @param daysBack 조회할 과거 일수 (예: 90 → 최근 90일)
   * @return 동기화 결과
   */
  public IpoSyncResult sync(int daysBack) {
    return runExclusively(() -> doSync(daysBack));
  }

  /**
   * 지정된 월 범위(YYYY-MM)에 대해 한 달씩 IPO 정보를 동기화한다(동시 실행 시 {@link IpoSyncBusyException}).
   *
   * <p>각 월에 대해 1일~말일로 DS001 검색하고, DS006 조회 시 해당 월 1일~오늘(또는 말일)로 기간을 제한한다. 기존 {@link #sync(int)}와
   * 동일한 upsert 규칙(수동 등록 보호, 유상증자 건너뜀 등)을 적용한다.
   *
   * @param fromMonth 시작 월 (YYYY-MM, 포함)
   * @param toMonth 종료 월 (YYYY-MM, 포함)
   * @return 동기화 결과 (마지막 달 결과, 누적은 상태 관리자에서 별도 관리)
   */
  public IpoSyncResult syncByMonthRange(YearMonth fromMonth, YearMonth toMonth) {
    return runExclusively(() -> doSyncByMonthRange(fromMonth, toMonth));
  }

  private IpoSyncResult doSyncByMonthRange(YearMonth fromMonth, YearMonth toMonth) {
    // 전체 기간의 마지막 날까지로 DS006 조회 기간 설정 (fromMonth 1일 ~ toMonth 말일 또는 오늘)
    LocalDate rangeStart = fromMonth.atDay(1);
    LocalDate rangeEnd = toMonth.atEndOfMonth();
    LocalDate today = LocalDate.now(clock);
    if (rangeEnd.isAfter(today)) {
      rangeEnd = today;
    }

    IpoSyncResult lastMonthResult = null;
    YearMonth current = fromMonth;

    while (!current.isAfter(toMonth)) {
      LocalDate monthStart = current.atDay(1);
      LocalDate monthEnd = current.atEndOfMonth();
      if (monthEnd.isAfter(today)) {
        monthEnd = today;
      }

      log.info("월별 동기화 시작: {} ({} ~ {})", current, monthStart, monthEnd);

      // 해당 월의 DS001 검색
      IpoSyncResult monthResult = doSyncForMonth(monthStart, monthEnd, rangeStart, rangeEnd);

      lastMonthResult = monthResult;

      // 중단 시 즉시 반환
      if (monthResult.isAborted()) {
        log.warn("월별 동기화 중단: month={}, reason={}", current, monthResult.getAbortReason());
        return monthResult;
      }

      current = current.plusMonths(1);
    }

    // 전체 기간이 중단 없이 완료되면 ustockplus 일정 병합 (마지막 달 기준으로 한 번만)
    if (lastMonthResult != null && !lastMonthResult.isAborted()) {
      mergeSchedule(lastMonthResult);
    }

    return lastMonthResult != null ? lastMonthResult : emptyResult();
  }

  private IpoSyncResult doSyncForMonth(
      LocalDate monthStart, LocalDate monthEnd, LocalDate rangeStart, LocalDate rangeEnd) {
    IpoSyncResult result = emptyResult();

    // Step 1: DS001 공시검색으로 해당 월 공시 회사 목록 조회
    List<OpenDartDisclosureItem> companies =
        fetchCompaniesForMonthOrAbort(monthStart, monthEnd, result);
    if (companies == null) {
      return result;
    }

    log.info("DS001 조회 결과 ({}): {}개 회사", monthStart, companies.size());

    // Step 2: 각 회사별 DS006 상세 조회 및 upsert (DS006 조회 기간은 rangeStart ~ rangeEnd)
    for (OpenDartDisclosureItem company : companies) {
      if (!processCompanyWithRange(result, company, rangeStart, rangeEnd)) {
        return result;
      }
    }

    log.info(
        "월별 동기화 완료: created={}, updated={}, skippedManual={}, skippedNonIpo={}, "
            + "failures={}, aborted={}",
        result.getCreated(),
        result.getUpdated(),
        result.getSkippedManual(),
        result.getSkippedNonIpo(),
        result.getFailureCount(),
        result.isAborted());

    return result;
  }

  /** 지정된 월의 DS001 공시검색을 수행한다. */
  private List<OpenDartDisclosureItem> fetchCompaniesForMonthOrAbort(
      LocalDate monthStart, LocalDate monthEnd, IpoSyncResult result) {
    try {
      return corpDisclosureClient.searchByDateRange(monthStart, monthEnd);
    } catch (OpenDartAuthenticationException e) {
      abort(result, "인증 오류: " + e.getMessage(), false);
      log.error("OpenDART 인증 오류로 동기화 중단: {}", e.getMessage());
      return null;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020): " + e.getMessage(), false);
        log.error("OpenDART 요청 제한 초과로 동기화 중단: {}", e.getMessage());
        return null;
      }
      abort(result, "API 오류: " + e.getMessage(), true);
      log.error("OpenDART API 오류로 동기화 중단 (재시도 가능): {}", e.getMessage());
      return null;
    }
  }

  /** 한 회사 처리 (DS006 조회 기간을 rangeStart~rangeEnd로 지정). */
  private boolean processCompanyWithRange(
      IpoSyncResult result,
      OpenDartDisclosureItem company,
      LocalDate rangeStart,
      LocalDate rangeEnd) {
    return processCompany(
        result,
        company,
        corpCode -> equitySecuritiesClient.getEquitySecurityDetail(corpCode, rangeStart, rangeEnd));
  }

  private static IpoSyncResult emptyResult() {
    return IpoSyncResult.builder()
        .created(0)
        .updated(0)
        .skippedManual(0)
        .skippedNonIpo(0)
        .failureCount(0)
        .failures(new ArrayList<>())
        .aborted(false)
        .build();
  }

  private IpoSyncResult doSync(int daysBack) {
    IpoSyncResult result = emptyResult();

    // Step 1: DS001 공시검색으로 최근 지분증권 증권신고서 제출 회사 목록 조회
    List<OpenDartDisclosureItem> companies = fetchCompaniesOrAbort(daysBack, result);
    if (companies == null) {
      return result;
    }

    log.info("DS001 조회 결과: {}개 회사", companies.size());

    // Step 2: 각 회사별 DS006 상세 조회 및 upsert
    for (OpenDartDisclosureItem company : companies) {
      if (!processCompany(result, company, equitySecuritiesClient::getEquitySecurityDetail)) {
        return result;
      }
    }

    log.info(
        "동기화 완료: created={}, updated={}, skippedManual={}, skippedNonIpo={}, "
            + "failures={}, aborted={}",
        result.getCreated(),
        result.getUpdated(),
        result.getSkippedManual(),
        result.getSkippedNonIpo(),
        result.getFailureCount(),
        result.isAborted());

    mergeSchedule(result);

    return result;
  }

  /**
   * DS001 공시검색을 수행하고, 중단 사유가 있으면 result에 기록 후 null을 반환한다.
   *
   * @param daysBack 조회할 과거 일수
   * @param result 동기화 결과 객체 (중단 시 aborted/abortReason/retryable 설정됨)
   * @return 회사 목록, 중단 시 null
   */
  private List<OpenDartDisclosureItem> fetchCompaniesOrAbort(int daysBack, IpoSyncResult result) {
    try {
      return corpDisclosureClient.searchRecentEquitySecuritiesCompanies(daysBack);
    } catch (OpenDartAuthenticationException e) {
      abort(result, "인증 오류: " + e.getMessage(), false);
      log.error("OpenDART 인증 오류로 동기화 중단: {}", e.getMessage());
      return null;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020): " + e.getMessage(), false);
        log.error("OpenDART 요청 제한 초과로 동기화 중단: {}", e.getMessage());
        return null;
      }
      // 일반 API 오류 (예: 800 시스템 점검) → 재시도 가능으로 표시
      abort(result, "API 오류: " + e.getMessage(), true);
      log.error("OpenDART API 오류로 동기화 중단 (재시도 가능): {}", e.getMessage());
      return null;
    }
  }

  /**
   * 한 회사(DS001의 한 건)에 대한 DS006 조회·검증·upsert를 처리한다. 루프를 중단해야 하면 false, 계속 진행하면 true를 반환한다.
   *
   * @param result 동기화 결과 객체
   * @param company DS001 조회 결과의 한 건
   * @param detailFetcher 회사별 DS006 상세 조회 함수
   * @return 계속 진행하면 true, 중단(인증 오류/요청 제한) 시 false
   */
  private boolean processCompany(
      IpoSyncResult result,
      OpenDartDisclosureItem company,
      Function<String, List<OpenDartEquitySecurityDetail>> detailFetcher) {
    String corpCode = company.getCorpCode();
    String corpName = company.getCorpName();
    String stockCode = company.getStockCode();

    // D-3: stock_code가 있으면 유상증자로 보고 건너뜀
    if (stockCode != null && !stockCode.isBlank()) {
      result.setSkippedNonIpo(result.getSkippedNonIpo() + 1);
      log.debug(
          "stock_code 존재로 건너뜀 (유상증자 추정): corpCode={}, corpName={}, stockCode={}",
          corpCode,
          corpName,
          stockCode);
      return true;
    }

    // DS006 상세 조회
    List<OpenDartEquitySecurityDetail> details;
    try {
      details = detailFetcher.apply(corpCode);
    } catch (OpenDartAuthenticationException e) {
      abort(result, "인증 오류: " + e.getMessage(), false);
      log.error("OpenDART 인증 오류로 동기화 중단 (corpCode={}): {}", corpCode, e.getMessage());
      return false;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020): " + e.getMessage(), false);
        log.error("OpenDART 요청 제한 초과로 동기화 중단 (corpCode={}): {}", corpCode, e.getMessage());
        return false;
      }
      // 그 외 API 오류는 해당 건만 실패 처리하고 계속
      recordFailure(result, corpCode, corpName, "DS006 조회 실패: " + e.getMessage());
      log.warn("DS006 조회 실패 (계속 진행): corpCode={}, error={}", corpCode, e.getMessage());
      return true;
    }

    if (details == null || details.isEmpty()) {
      recordFailure(result, corpCode, corpName, "DS006 상세 정보 없음 (빈 응답)");
      log.warn("DS006 빈 응답: corpCode={}, corpName={}", corpCode, corpName);
      return true;
    }

    // 정정 공시가 있으면 여러 건 → 접수번호가 가장 큰(최신) 건을 명시적으로 선택
    OpenDartEquitySecurityDetail detail =
        details.stream()
            .max(
                Comparator.comparing(
                    OpenDartEquitySecurityDetail::getRceptNo,
                    Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElseThrow();

    // subscriptionStartDate가 null이면 실패 처리
    if (detail.getSubscriptionStartDate() == null) {
      recordFailure(result, corpCode, corpName, "청약 시작일 파싱 실패 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 시작일 파싱 실패: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }

    // 끝일 구분자는 있는데 못 읽은 경우: 다음 영업일로 조용히 채우지 않고 실패 기록
    if (detail.isSubscriptionEndUnreadable()) {
      recordFailure(result, corpCode, corpName, "청약 종료일 파싱 실패 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 종료일 파싱 실패: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }
    if (detail.getSubscriptionEndDate() != null
        && detail.getSubscriptionEndDate().isBefore(detail.getSubscriptionStartDate())) {
      recordFailure(result, corpCode, corpName, "청약 종료일이 시작일보다 앞섬 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 종료일이 시작일보다 앞섬: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }

    // 건별 독립 트랜잭션으로 처리 (한 건 실패가 다른 건에 영향 없도록)
    transactionTemplate.executeWithoutResult(
        status -> {
          try {
            IpoSyncPersistence.Outcome outcome =
                ipoSyncPersistence.persist(corpCode, corpName, detail);
            switch (outcome) {
              case CREATED -> result.setCreated(result.getCreated() + 1);
              case UPDATED -> result.setUpdated(result.getUpdated() + 1);
              case SKIPPED_MANUAL -> result.setSkippedManual(result.getSkippedManual() + 1);
              default -> throw new IllegalStateException("Unexpected IPO sync outcome: " + outcome);
            }
          } catch (Exception e) {
            // 트랜잭션 롤백 표시 (이미 롤백됨)
            status.setRollbackOnly();
            recordFailure(result, corpCode, corpName, "처리 중 예외: " + e.getMessage());
            log.warn("IPO 처리 실패 (계속 진행): corpCode={}, error={}", corpCode, e.getMessage());
          }
        });

    return true;
  }

  private static void abort(IpoSyncResult result, String reason, boolean retryable) {
    result.setAborted(true);
    result.setRetryable(retryable);
    result.setAbortReason(reason);
  }

  private static void recordFailure(
      IpoSyncResult result, String corpCode, String corpName, String reason) {
    result
        .getFailures()
        .add(IpoSyncFailure.builder().corpCode(corpCode).corpName(corpName).reason(reason).build());
    result.setFailureCount(result.getFailureCount() + 1);
  }

  /** OpenDART 루프가 중단 없이 완료된 경우에만 ustockplus 일정을 병합한다. 병합 예외는 삼킨다. */
  private void mergeSchedule(IpoSyncResult result) {
    if (result.isAborted()) {
      result.setScheduleMerged(0);
      return;
    }
    try {
      int merged = ipoScheduleMergeService.merge(LocalDate.now(clock));
      result.setScheduleMerged(merged);
      log.info("ustockplus 일정 병합 완료: merged={}", merged);
    } catch (Exception e) {
      log.warn("ustockplus 일정 병합 중 예외 발생 (OpenDART 결과 유지): {}", e.getMessage());
      result.setScheduleMerged(0);
    }
  }
}
