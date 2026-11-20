package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.ipocatalog.exception.IpoSyncBusyException;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartFilingOrigin;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTermsParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
 *   <li>DS001의 {@code stock_code}는 동일 공모 확인 후 IPO에 저장하여 상장일 병합에 활용한다.
 *   <li>DS006 상세를 검증한 뒤 회사별 독립 transaction 안에서 {@link IpoSyncPersistence}를 호출한다.
 *   <li>가격은 DS006이 아닌 document.xml 원문에서 파싱한 검증된 값만 사용한다.
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
  private final DartIpoFilingClient dartIpoFilingClient;
  private final DartOfferingTermsParser offeringTermsParser;
  private final IpoSyncPersistence ipoSyncPersistence;
  private final TransactionTemplate transactionTemplate;
  private final IpoScheduleMergeService ipoScheduleMergeService;
  private final IpoReconciliationService ipoReconciliationService;
  private final IpoSyncRunRecorder ipoSyncRunRecorder;
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
    // DS006 조회 기간: 전월 1일 ~ toMonth 말일 또는 오늘
    // 정정 공시의 원공시 접수일이 이전 달에 있을 수 있어 범위를 한 달 앞당김
    LocalDate rangeStart = fromMonth.atDay(1).minusMonths(1);
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
      abort(result, "인증 오류", false);
      log.error("OpenDART 인증 오류로 동기화 중단: {}", e.getClass().getSimpleName());
      return null;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020)", false);
        log.error("OpenDART 요청 제한 초과로 동기화 중단: {}", e.getClass().getSimpleName());
        return null;
      }
      abort(result, "API 오류 (재시도 가능)", true);
      log.error("OpenDART API 오류로 동기화 중단 (재시도 가능): {}", e.getClass().getSimpleName());
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
    LocalDateTime startedAt = LocalDateTime.now(clock);
    IpoSyncResult result = emptyResult();

    // Step 1: 활성 자동 공모 재점검 — 신규 수집보다 먼저 실행해, 오래된 공모를 새 공시 upsert가 덮어쓰지 못하게 한다
    IpoReconcileResult reconcile = reconcileStep(result);

    List<OpenDartDisclosureItem> companies = null;
    if (reconcile.isAborted()) {
      // 인증 오류·020은 같은 키를 쓰므로 신규 수집도 중단하고 다음 일정 실행에서 재확인한다
      abort(result, reconcile.getAbortReason(), false);
      log.error("신규 수집 건너뜀 (재점검 중단): {}", reconcile.getAbortReason());
    } else {
      // Step 2: DS001 공시검색으로 최근 지분증권 증권신고서 제출 회사 목록 조회
      companies = fetchCompaniesOrAbort(daysBack, result);
    }
    if (companies != null) {
      log.info("DS001 조회 결과: {}개 회사", companies.size());

      // Step 3: 각 회사별 DS006 상세 조회 및 upsert
      for (OpenDartDisclosureItem company : companies) {
        if (!processCompany(result, company, equitySecuritiesClient::getEquitySecurityDetail)) {
          break;
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
    }

    recordDailyRun(startedAt, reconcile, result);

    return result;
  }

  /** 재점검 단계를 격리 실행한다. 재점검 실패가 신규 수집을 막지 않는다. */
  private IpoReconcileResult reconcileStep(IpoSyncResult result) {
    IpoReconcileResult reconcile;
    try {
      reconcile = ipoReconciliationService.reconcileActiveOfferings();
    } catch (Exception e) {
      // 재점검 단계 예외: 격리하고 신규 수집은 계속한다(인증/020은 서비스 안에서 aborted로 처리).
      // 성공 기록을 남기지 않고 failure 1건을 남겨 다음 실행이 재시도하게 한다
      log.error("재점검 단계 예외 (신규 수집은 계속): {}", e.getClass().getSimpleName());
      reconcile = new IpoReconcileResult();
      IpoSyncFailure failure =
          IpoSyncFailure.builder()
              .corpCode(null)
              .corpName(null)
              .reason("철회점검: 재점검 단계 예외: " + e.getClass().getSimpleName())
              .build();
      reconcile.getFailures().add(failure);
      reconcile.setFailed(1);
      result.getFailures().add(failure);
      result.setFailureCount(result.getFailureCount() + 1);
      return reconcile;
    }
    // 재점검 실패를 결과에 병합 (aborted 플래그는 바꾸지 않음)
    result.getFailures().addAll(reconcile.getFailures());
    result.setFailureCount(result.getFailureCount() + reconcile.getFailed());
    result.setUpdated(result.getUpdated() + reconcile.getWithdrawn() + reconcile.getRefreshed());
    return reconcile;
  }

  /** 일일 재점검 실행 결과를 ipo_sync_runs에 기록한다. 완료 판정은 SUCCESS 행만 인정한다. */
  private void recordDailyRun(
      LocalDateTime startedAt, IpoReconcileResult reconcile, IpoSyncResult result) {
    int collectFailureCount = Math.max(0, result.getFailureCount() - reconcile.getFailed());
    try {
      ipoSyncRunRecorder.recordDaily(
          startedAt, reconcile, result.getFailures(), result.isAborted(), collectFailureCount);
    } catch (Exception e) {
      log.warn("일일 재점검 실행 기록 실패: {}", e.getClass().getSimpleName());
    }
  }

  /**
   * 배포 후 자동복구: 활성 자동 공모 재점검만 실행하고 ipo_sync_runs(POST_DEPLOY_RECOVER)에 기록한다.
   *
   * @return 재점검 단계 결과(스케줄러/러너 로그용)
   */
  public IpoSyncResult recoverAfterDeploy() {
    return runExclusively(() -> doRecover());
  }

  private IpoSyncResult doRecover() {
    LocalDateTime startedAt = LocalDateTime.now(clock);
    IpoSyncResult result = emptyResult();
    IpoReconcileResult reconcile = reconcileStep(result);
    try {
      ipoSyncRunRecorder.recordRecover(startedAt, reconcile);
    } catch (Exception e) {
      log.warn("POST_DEPLOY_RECOVER 기록 실패: {}", e.getClass().getSimpleName());
    }
    log.info(
        "배포 후 재점검 완료: withdrawn={}, failed={}, aborted={}",
        reconcile.getWithdrawn(),
        reconcile.getFailed(),
        reconcile.isAborted());
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
      abort(result, "인증 오류", false);
      log.error("OpenDART 인증 오류로 동기화 중단: {}", e.getClass().getSimpleName());
      return null;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020)", false);
        log.error("OpenDART 요청 제한 초과로 동기화 중단: {}", e.getClass().getSimpleName());
        return null;
      }
      // 일반 API 오류 (예: 800 시스템 점검) → 재시도 가능으로 표시
      abort(result, "API 오류 (재시도 가능)", true);
      log.error("OpenDART API 오류로 동기화 중단 (재시도 가능): {}", e.getClass().getSimpleName());
      return null;
    }
  }

  /**
   * 한 회사(DS001의 한 건)에 대한 DS006 조회·검증·upsert를 처리한다. 루프를 중단해야 하면 false, 계속 진행하면 true를 반환한다.
   *
   * @param result 동기화 결과 객체
   * @param company DS001 공시검색 결과의 한 건
   * @param detailFetcher 회사별 DS006 상세 조회 함수
   * @return 계속 진행하면 true, 중단(인증 오류/요청 제한) 시 false
   */
  private boolean processCompany(
      IpoSyncResult result,
      OpenDartDisclosureItem company,
      Function<String, List<OpenDartEquitySecurityDetail>> detailFetcher) {
    String corpCode = company.getCorpCode();
    String corpName = company.getCorpName();

    // DS006 상세 조회
    List<OpenDartEquitySecurityDetail> details;
    try {
      details = detailFetcher.apply(corpCode);
    } catch (OpenDartAuthenticationException e) {
      abort(result, "인증 오류", false);
      log.error("OpenDART 인증 오류로 동기화 중단 (corpCode={}): {}", corpCode, e.getClass().getSimpleName());
      return false;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020)", false);
        log.error(
            "OpenDART 요청 제한 초과로 동기화 중단 (corpCode={}): {}", corpCode, e.getClass().getSimpleName());
        return false;
      }
      // 그 외 API 오류는 해당 건만 실패 처리하고 계속
      recordFailure(result, corpCode, corpName, "DS006 조회 실패 (status=" + safeStatus(e) + ")");
      log.warn("DS006 조회 실패 (계속 진행): corpCode={}, 예외={}", corpCode, e.getClass().getSimpleName());
      return true;
    }

    if (details == null || details.isEmpty()) {
      if (tryVerifiedFilingWithoutCurrentDetail(result, company, null)) {
        return !result.isAborted();
      }
      recordFailure(result, corpCode, corpName, "DS006 상세 정보 없음 (빈 응답)");
      log.warn("DS006 빈 응답: corpCode={}, corpName={}", corpCode, corpName);
      return true;
    }

    // DS006 상세 선택: company 종류에 따라 우선순위 다르게 적용
    OpenDartEquitySecurityDetail detail = selectDetail(details, company);
    boolean isWithdrawal = company.getRm() != null && company.getRm().contains("철");
    if (detail == null) {
      if (isWithdrawal) {
        // 철회 공시인데 DS006 매칭 실패: 실패 기록
        String reason = "철회 대상 원공시(" + company.getRceptNo() + ")에 대응하는 DS006 상세 없음: 자동 변경 보류";
        recordFailure(result, corpCode, corpName, reason);
      }
      // 상세가 없더라도 정정 원문에서 원공시 근거를 확보할 수 있다.
      if (!isWithdrawal && tryVerifiedFilingWithoutCurrentDetail(result, company, null)) {
        return !result.isAborted();
      }
      return true;
    }

    if (isWithdrawal) {
      // 이미 위에서 매칭된 detail 사용, 추가 검증은 생략 (매칭 실패 시 이미 리턴됨)
      transactionTemplate.executeWithoutResult(
          status -> {
            if (!ipoSyncPersistence.withdraw(corpCode, detail)) {
              recordFailure(result, corpCode, corpName, "철회 공모 매칭 실패: 자동 변경 보류");
            } else {
              result.setUpdated(result.getUpdated() + 1);
            }
          });
      return true;
    }

    // DS006 상세가 최신 접수번호와 일치해도 정정 공시의 원신고는 별도로 확인한다.
    // 이 검증을 생략하면 기존 IPO 원공시와 다른 접수번호를 영구적으로 갱신하지 못한다.
    boolean attemptedCorrection = company.isCorrectionOrFinalized();
    if (attemptedCorrection && tryVerifiedFilingWithoutCurrentDetail(result, company, detail)) {
      return !result.isAborted();
    }

    // 과거 DS006 상세가 폴백된 경우 최신 원문의 최초제출일을 확인한 뒤 갱신한다.
    // 이 경로가 증명되지 않으면 기존 저장 규칙에 따라 보수적으로 보류한다.
    if (company.getRceptNo() != null && !company.getRceptNo().equals(detail.getRceptNo())) {
      if (!attemptedCorrection && tryVerifiedFilingWithoutCurrentDetail(result, company, detail)) {
        return !result.isAborted();
      }
      recordFailure(result, corpCode, corpName, "DS001/DS006 접수번호 불일치, 원공시 연결 불가: 저장 보류");
      return true;
    }

    // subscriptionStartDate가 null이면 실패 처리
    if (detail.getSubscriptionStartDate() == null) {
      if (tryVerifiedFilingWithoutCurrentDetail(result, company, detail)) {
        return !result.isAborted();
      }
      recordFailure(result, corpCode, corpName, "청약 시작일 파싱 실패 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 시작일 파싱 실패: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }

    // 끝일 구분자는 있는데 못 읽은 경우: 다음 영업일로 조용히 채우지 않고 실패 기록
    if (detail.isSubscriptionEndUnreadable()) {
      if (tryVerifiedFilingWithoutCurrentDetail(result, company, detail)) {
        return !result.isAborted();
      }
      recordFailure(result, corpCode, corpName, "청약 종료일 파싱 실패 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 종료일 파싱 실패: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }
    if (detail.getSubscriptionEndDate() != null
        && detail.getSubscriptionEndDate().isBefore(detail.getSubscriptionStartDate())) {
      if (tryVerifiedFilingWithoutCurrentDetail(result, company, detail)) {
        return !result.isAborted();
      }
      recordFailure(result, corpCode, corpName, "청약 종료일이 시작일보다 앞섬 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 종료일이 시작일보다 앞섬: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }

    // 원문은 DS006 대체 상세가 아닌, 선택한 DS001 공시의 접수번호로 읽는다.
    DartOfferingTerms offeringTerms = null;
    String documentReceiptNo = company.getRceptNo();
    if (documentReceiptNo == null) {
      documentReceiptNo = detail.getRceptNo();
    }
    if (documentReceiptNo != null) {
      try {
        Optional<String> xmlOpt = dartIpoFilingClient.downloadDocument(documentReceiptNo);
        if (xmlOpt.isPresent()) {
          String xml = xmlOpt.get();
          if (!xml.isBlank()) {
            offeringTerms =
                offeringTermsParser.parse(xml, documentReceiptNo, company.isFinalizedReport());
          } else {
            log.warn("문서 원문 다운로드 결과 빈 값: rceptNo={}", documentReceiptNo);
            recordFailure(
                result, corpCode, corpName, "문서 원문 빈 값 (rceptNo=" + documentReceiptNo + ")");
          }
        } else {
          log.warn("문서 원문 다운로드 실패 (빈 응답): rceptNo={}", documentReceiptNo);
          recordFailure(
              result, corpCode, corpName, "문서 원문 다운로드 실패 (rceptNo=" + documentReceiptNo + ")");
        }
      } catch (Exception e) {
        log.warn(
            "문서 원문 다운로드/파싱 실패 (rceptNo={}): {}", documentReceiptNo, e.getClass().getSimpleName());
        recordFailure(
            result, corpCode, corpName, "문서 원문 파싱 실패 (rceptNo=" + documentReceiptNo + ")");
      }
    }

    // 반복 호출 방지: 최신 공시 접수번호와 저장된 출처 접수번호·파서 버전이 모두 같으면 건너뛰기
    // (persist 내부에서 처리)

    final DartOfferingTerms finalOfferingTerms = offeringTerms;

    // 건별 독립 트랜잭션으로 처리 (한 건 실패가 다른 건에 영향 없도록)
    transactionTemplate.executeWithoutResult(
        status -> {
          try {
            IpoSyncPersistence.Outcome outcome =
                ipoSyncPersistence.persist(
                    corpCode,
                    corpName,
                    detail,
                    company.isFinalizedReport(),
                    company.isCorrectionOrFinalized(),
                    finalOfferingTerms,
                    company.getStockCode());
            switch (outcome) {
              case CREATED -> result.setCreated(result.getCreated() + 1);
              case UPDATED -> result.setUpdated(result.getUpdated() + 1);
              case SKIPPED_WITHDRAWN -> log.debug("철회된 동일 공시 재생성 방지: corpCode={}", corpCode);
              case SKIPPED_MANUAL -> result.setSkippedManual(result.getSkippedManual() + 1);
              case BLOCKED_MULTIPLE ->
                  recordFailure(result, corpCode, corpName, "철회점검: 동일 회사 ACTIVE 복수 건");
              case HELD_UNDETERMINED ->
                  recordFailure(result, corpCode, corpName, "신규 공모/정정 구분 불가: 자동 갱신 보류");
              default -> throw new IllegalStateException("Unexpected IPO sync outcome: " + outcome);
            }
          } catch (Exception e) {
            // 트랜잭션 롤백 표시 (이미 롤백됨)
            status.setRollbackOnly();
            recordFailure(result, corpCode, corpName, "처리 중 예외: " + e.getClass().getSimpleName());
            log.warn(
                "IPO 처리 실패 (계속 진행): corpCode={}, 예외={}", corpCode, e.getClass().getSimpleName());
          }
        });

    return true;
  }

  /**
   * 최신 DS001 정정 원문이 최초제출일을 명시하면 DS006 일치 여부와 독립적으로 검증된 조건을 저장한다. 기존 수집기는 회사별 최신 공시만 반환하므로 원신고 날짜부터
   * DS001을 다시 조회한다.
   */
  private boolean tryVerifiedFilingWithoutCurrentDetail(
      IpoSyncResult result, OpenDartDisclosureItem filing, OpenDartEquitySecurityDetail fallback) {
    if (filing.getRceptNo() == null || !filing.isEquitySecuritiesReport()) {
      return false;
    }
    String xml;
    DartOfferingTerms latest;
    try {
      Optional<String> doc = dartIpoFilingClient.downloadDocument(filing.getRceptNo());
      if (doc.isEmpty() || doc.get().isBlank()) {
        return false;
      }
      xml = doc.get();
      List<OpenDartDisclosureItem> historical;
      String original;
      if (filing.isCorrectionOrFinalized()) {
        LocalDate firstSubmission = DartFilingOrigin.originalSubmissionDate(xml);
        if (firstSubmission == null) {
          return false;
        }
        historical =
            corpDisclosureClient.searchAllByDateRange(
                filing.getCorpCode(), firstSubmission, LocalDate.now(clock));
        original = DartFilingOrigin.resolveOriginalReceipt(filing, historical, xml);
      } else {
        // 원신고는 그 자체가 회차 식별 근거이므로 추가 DS001 조회가 필요 없다.
        historical = List.of(filing);
        original = filing.getRceptNo();
      }
      if (original == null) {
        recordFailure(
            result, filing.getCorpCode(), filing.getCorpName(), "최초제출일과 원공시 접수번호 연결 불가: 저장 보류");
        return true;
      }
      latest = offeringTermsParser.parse(xml, filing.getRceptNo(), filing.isFinalizedReport());
      if (latest == null) {
        return false;
      }
      DartOfferingTerms historicalBand = null;
      if (!latest.hasValidBand()) {
        List<OpenDartDisclosureItem> earlier =
            historical.stream()
                .filter(item -> item.getRceptNo() != null)
                .filter(item -> item.getRceptNo().compareTo(original) >= 0)
                .filter(item -> item.getRceptNo().compareTo(filing.getRceptNo()) < 0)
                .filter(OpenDartDisclosureItem::isEquitySecuritiesReport)
                .sorted(Comparator.comparing(OpenDartDisclosureItem::getRceptNo).reversed())
                .toList();
        for (OpenDartDisclosureItem item : earlier) {
          if (!item.isCorrectionOrFinalized() && !original.equals(item.getRceptNo())) {
            break;
          }
          Optional<String> earlierXml = dartIpoFilingClient.downloadDocument(item.getRceptNo());
          if (earlierXml.isPresent() && !earlierXml.get().isBlank()) {
            if (!original.equals(
                DartFilingOrigin.resolveOriginalReceipt(item, historical, earlierXml.get()))) {
              continue;
            }
            DartOfferingTerms candidate =
                offeringTermsParser.parse(
                    earlierXml.get(), item.getRceptNo(), item.isFinalizedReport());
            if (candidate != null && candidate.hasValidBand()) {
              historicalBand = candidate;
              break;
            }
          }
        }
      }
      final DartOfferingTerms band = historicalBand;
      IpoSyncPersistence.Outcome outcome =
          transactionTemplate.execute(
              status ->
                  ipoSyncPersistence.persistVerifiedFiling(
                      filing.getCorpCode(),
                      filing.getCorpName(),
                      filing.getRceptNo(),
                      original,
                      fallback,
                      latest,
                      band,
                      filing.getStockCode()));
      if (outcome == null) {
        recordFailure(result, filing.getCorpCode(), filing.getCorpName(), "원문 저장 결과 없음");
        return true;
      }
      switch (outcome) {
        case CREATED -> result.setCreated(result.getCreated() + 1);
        case UPDATED -> result.setUpdated(result.getUpdated() + 1);
        case SKIPPED_MANUAL -> result.setSkippedManual(result.getSkippedManual() + 1);
        case SKIPPED_WITHDRAWN -> log.debug("철회된 원공시 자동 재생성 방지: corpCode={}", filing.getCorpCode());
        case BLOCKED_MULTIPLE, HELD_UNDETERMINED ->
            recordFailure(
                result, filing.getCorpCode(), filing.getCorpName(), "원공시 회차 또는 일정 근거 부족: 자동 저장 보류");
        default -> throw new IllegalStateException("Unexpected IPO sync outcome: " + outcome);
      }
      return true;
    } catch (OpenDartAuthenticationException e) {
      abort(result, "인증 오류", false);
      return true;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        abort(result, "요청 제한 초과(020)", false);
        return true;
      }
      recordFailure(
          result,
          filing.getCorpCode(),
          filing.getCorpName(),
          "정정 원문 검증 API 오류 (" + safeStatus(e) + ")");
      return true;
    } catch (Exception e) {
      log.warn(
          "정정 원문 검증 보류: corpCode={}, error={}", filing.getCorpCode(), e.getClass().getSimpleName());
      return false;
    }
  }

  private static void abort(IpoSyncResult result, String reason, boolean retryable) {
    result.setAborted(true);
    result.setRetryable(retryable);
    result.setAbortReason(reason);
  }

  private static String safeStatus(OpenDartApiException e) {
    return e == null ? "-" : e.getStatusCode();
  }

  private static void recordFailure(
      IpoSyncResult result, String corpCode, String corpName, String reason) {
    result
        .getFailures()
        .add(IpoSyncFailure.builder().corpCode(corpCode).corpName(corpName).reason(reason).build());
    result.setFailureCount(result.getFailureCount() + 1);
  }

  /**
   * DS006 상세 목록에서 company 종류에 따라 최적의 상세를 선택한다.
   *
   * <ul>
   *   <li>company가 발행조건확정 공시이면, 그 rceptNo와 일치하는 DS006 상세를 최우선 선택한다.
   *   <li>company가 정정 공시(기재정정)이면, 그 rceptNo와 일치하는 DS006 상세를 선택한다.
   *   <li>company가 철회 공시이면, 철회 대상 원공시 rceptNo와 일치하는 DS006 상세를 선택한다.
   *   <li>그 외(일반 공시)는 최신 rceptNo(문자열 기준 내림차순) 상세를 선택한다.
   * </ul>
   *
   * @param details DS006 상세 목록
   * @param company DS001 공시검색 결과 한 건
   * @return 선택된 DS006 상세, 철회 케이스에서 매칭 실패 시 {@code null}
   */
  private static OpenDartEquitySecurityDetail selectDetail(
      List<OpenDartEquitySecurityDetail> details, OpenDartDisclosureItem company) {
    // 1순위: company가 발행조건확정 공시이면, 그 rceptNo와 일치하는 DS006 상세 찾기
    if (company.isFinalizedReport() && company.getRceptNo() != null) {
      Optional<OpenDartEquitySecurityDetail> finalizedDetail =
          details.stream().filter(d -> company.getRceptNo().equals(d.getRceptNo())).findFirst();
      if (finalizedDetail.isPresent()) {
        return finalizedDetail.get();
      }
      // DS006에 없으면 경고 로그 후 아래 로직으로 폴백
      log.warn(
          "발행조건확정 공시({})의 DS006 상세 없음, 폴백: corpCode={}",
          company.getRceptNo(),
          company.getCorpCode());
    }

    // 2순위: company가 정정 공시(기재정정)이면, 그 rceptNo와 일치하는 DS006 상세 찾기
    if (company.isCorrectionReport() && company.getRceptNo() != null) {
      Optional<OpenDartEquitySecurityDetail> correctionDetail =
          details.stream().filter(d -> company.getRceptNo().equals(d.getRceptNo())).findFirst();
      if (correctionDetail.isPresent()) {
        return correctionDetail.get();
      }
      // DS006에 없으면 경고 로그 후 아래 로직으로 폴백
      log.warn(
          "정정 공시({})의 DS006 상세 없음, 폴백: corpCode={}", company.getRceptNo(), company.getCorpCode());
    }

    // 3순위: 철회 공시면 철회 대상 원공시 rceptNo로 매칭
    if (company.getRm() != null && company.getRm().contains("철")) {
      return details.stream()
          .filter(d -> company.getRceptNo() != null && company.getRceptNo().equals(d.getRceptNo()))
          .findFirst()
          .orElse(null); // null이면 상위에서 실패 처리함
    }

    // 4순위: 일반 케이스 - 최신 rceptNo (기존 로직 유지)
    return details.stream()
        .max(
            Comparator.comparing(
                OpenDartEquitySecurityDetail::getRceptNo,
                Comparator.nullsFirst(Comparator.naturalOrder())))
        .orElseThrow();
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
      log.warn("ustockplus 일정 병합 중 예외 발생 (OpenDART 결과 유지): {}", e.getClass().getSimpleName());
      result.setScheduleMerged(0);
    }
  }
}
