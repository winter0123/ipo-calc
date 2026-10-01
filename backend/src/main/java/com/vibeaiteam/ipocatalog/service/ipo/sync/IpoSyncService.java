package com.vibeaiteam.ipocatalog.service.ipo.sync;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IPO 자동 동기화 서비스.
 *
 * <p>OpenDART DS001(공시검색)으로 최근 공시 회사 목록을 조회하고, DS006(지분증권 상세)으로 각 회사의 상세 정보를 조회하여 {@code ipos} 테이블에
 * upsert한다. OpenDART 루프가 중단 없이 끝나면 ustockplus 일정(상장예정일·배정일·환불일)을 병합한다. 주요 규칙:
 *
 * <ul>
 *   <li>DS001 응답의 {@code stock_code}가 null/빈 문자열인 건만 IPO로 동기화 (값 있으면 유상증자로 보고 skippedNonIpo++)
 *   <li>{@code subscriptionStartDate}가 null이면 failures에 기록하고 건너뜀
 *   <li>청약 종료일: 파싱된 {@code subscriptionEndDate} 있으면 사용, 없으면 {@code
 *       BusinessDayCalculator.nextBusinessDay(start)}
 *   <li>Upsert 규칙:
 *       <ol>
 *         <li>{@code findByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, ACTIVE)} 존재 시:
 *             <ul>
 *               <li>{@code syncSource == null} (수동 등록) → 절대 수정하지 않고 skippedManual++
 *               <li>그 외 → 가격 밴드·수량·청약일·회사명 갱신, {@code lastSyncedAt} 갱신, updated++
 *             </ul>
 *         <li>corpCode로 못 찾으면 {@code (ipoName=corpName, startDate)}로 {@code
 *             findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull} 확인: 존재 시(수동 등록 등) →
 *             수정하지 않고 skippedManual++
 *         <li>둘 다 없으면 신규 생성: {@code companyName=ipoName=corpName}, {@code corpCode}, {@code
 *             stockCode=null}, 밴드 두 값={@code offerPrice}, {@code fixedPrice=null}, {@code
 *             offeringShareCount}, 시작/종료일, {@code syncSource="OPENDART"}, {@code lastSyncedAt=now},
 *             {@code status="ACTIVE"}, created++
 *       </ol>
 *   <li>{@code fixedPrice}, {@code listingDate}는 동기화가 절대 덮어쓰지 않음 (수동 입력 보호; 갱신 시 null로 지우지도 않음)
 *   <li>IPO 1건 처리 실패({@code DataIntegrityViolationException} 등)는 그 건만 failures에 기록하고 다음 건으로 계속 (건별
 *       독립 트랜잭션)
 *   <li>{@code OpenDartAuthenticationException} 또는 statusCode {@code "020"}의 {@code
 *       OpenDartApiException}이면 즉시 중단하고 그때까지의 결과 반환 ({@code aborted=true})
 *   <li>그 외 {@code OpenDartApiException}은 그 건만 failures에 기록
 *   <li>OpenDART 루프가 중단(aborted) 없이 완료되면 {@code IpoScheduleMergeService.merge(LocalDate.now())}를
 *       호출해 ustockplus 일정을 병합한다. 병합 중 예외가 나도 OpenDART 결과는 그대로 반환한다 (try-catch, WARN 로그).
 *       병합 건수는 {@code IpoSyncResult.scheduleMerged}에 기록한다.
 *   <li>로그에 API 키를 절대 남기지 않음 (키는 URL 쿼리 파라미터)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoSyncService {
  private static final String SYNC_SOURCE_OPENDART = "OPENDART";

  private final OpenDartCorpDisclosureClient corpDisclosureClient;
  private final OpenDartEquitySecuritiesClient equitySecuritiesClient;
  private final IpoRepository ipoRepository;
  private final BusinessDayCalculator businessDayCalculator;
  private final TransactionTemplate transactionTemplate;
  private final IpoScheduleMergeService ipoScheduleMergeService;
  private final Clock clock;

  /**
   * 최근 지정된 일수 내 IPO 정보를 동기화한다.
   *
   * @param daysBack 조회할 과거 일수 (예: 90 → 최근 90일)
   * @return 동기화 결과
   */
  public IpoSyncResult sync(int daysBack) {
    IpoSyncResult result =
        IpoSyncResult.builder()
            .created(0)
            .updated(0)
            .skippedManual(0)
            .skippedNonIpo(0)
            .failureCount(0)
            .failures(new ArrayList<>())
            .aborted(false)
            .build();

    // Step 1: DS001 공시검색으로 최근 지분증권 증권신고서 제출 회사 목록 조회
    List<OpenDartDisclosureItem> companies = fetchCompaniesOrAbort(daysBack, result);
    if (companies == null) {
      return result;
    }

    log.info("DS001 조회 결과: {}개 회사", companies.size());

    // Step 2: 각 회사별 DS006 상세 조회 및 upsert
    for (OpenDartDisclosureItem company : companies) {
      if (!processCompany(result, company)) {
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
      result.setAborted(true);
      result.setAbortReason("인증 오류: " + e.getMessage());
      log.error("OpenDART 인증 오류로 동기화 중단: {}", e.getMessage());
      return null;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        result.setAborted(true);
        result.setRetryable(false);
        result.setAbortReason("요청 제한 초과(020): " + e.getMessage());
        log.error("OpenDART 요청 제한 초과로 동기화 중단: {}", e.getMessage());
        return null;
      }
      // 일반 API 오류 (예: 800 시스템 점검) → 재시도 가능으로 표시
      result.setAborted(true);
      result.setRetryable(true);
      result.setAbortReason("API 오류: " + e.getMessage());
      log.error("OpenDART API 오류로 동기화 중단 (재시도 가능): {}", e.getMessage());
      return null;
    }
  }

  /**
   * 한 회사(DS001의 한 건)에 대한 DS006 조회·검증·upsert를 처리한다.
   * 루프를 중단해야 하면 false, 계속 진행하면 true를 반환한다.
   *
   * @param result 동기화 결과 객체
   * @param company DS001 조회 결과의 한 건
   * @return 계속 진행하면 true, 중단(인증 오류/요청 제한) 시 false
   */
  private boolean processCompany(IpoSyncResult result, OpenDartDisclosureItem company) {
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
      details = equitySecuritiesClient.getEquitySecurityDetail(corpCode);
    } catch (OpenDartAuthenticationException e) {
      result.setAborted(true);
      result.setAbortReason("인증 오류: " + e.getMessage());
      log.error("OpenDART 인증 오류로 동기화 중단 (corpCode={}): {}", corpCode, e.getMessage());
      return false;
    } catch (OpenDartApiException e) {
      if ("020".equals(e.getStatusCode())) {
        result.setAborted(true);
        result.setAbortReason("요청 제한 초과(020): " + e.getMessage());
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

    // 보통 1건만 옴, 첫 번째 건 사용
    OpenDartEquitySecurityDetail detail = details.get(0);

    // subscriptionStartDate가 null이면 실패 처리
    if (detail.getSubscriptionStartDate() == null) {
      recordFailure(
          result, corpCode, corpName, "청약 시작일 파싱 실패 (sbd: " + detail.getSbd() + ")");
      log.warn("청약 시작일 파싱 실패: corpCode={}, sbd={}", corpCode, detail.getSbd());
      return true;
    }

    // 건별 독립 트랜잭션으로 처리 (한 건 실패가 다른 건에 영향 없도록)
    transactionTemplate.executeWithoutResult(
        status -> {
          try {
            processSingleIpo(result, corpCode, corpName, detail);
          } catch (Exception e) {
            // 트랜잭션 롤백 표시 (이미 롤백됨)
            status.setRollbackOnly();
            recordFailure(result, corpCode, corpName, "처리 중 예외: " + e.getMessage());
            log.warn("IPO 처리 실패 (계속 진행): corpCode={}, error={}", corpCode, e.getMessage());
          }
        });

    return true;
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

  /** 단일 IPO 처리 (독립 트랜잭션 내부에서 호출). */
  private void processSingleIpo(
      IpoSyncResult result, String corpCode, String corpName, OpenDartEquitySecurityDetail detail) {

    LocalDate startDate = detail.getSubscriptionStartDate();
    LocalDate endDate = detail.getSubscriptionEndDate();

    // 종료일이 없으면 다음 영업일로 계산
    if (endDate == null) {
      endDate = businessDayCalculator.nextBusinessDay(startDate);
      log.debug(
          "청약 종료일 계산 (다음 영업일): corpCode={}, startDate={}, endDate={}",
          corpCode,
          startDate,
          endDate);
    }

    Long offerPrice = detail.getOfferPrice();
    Long offeringShareCount = detail.getOfferingShareCount();

    // 1) corpCode로 기존 자동 동기화 레코드 조회
    var existingByCorpCode =
        ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, RecordStatus.ACTIVE);
    if (existingByCorpCode.isPresent()) {
      Ipo existing = existingByCorpCode.get();
      if (existing.getSyncSource() == null) {
        // 수동 등록 레코드 → 절대 수정하지 않음
        result.setSkippedManual(result.getSkippedManual() + 1);
        log.debug("수동 등록 레코드 존재로 건너뜀 (corpCode 매칭): corpCode={}", corpCode);
        return;
      }
      // 자동 동기화 레코드 → 갱신 (fixedPrice, listingDate 보존)
      updateExistingIpo(existing, corpName, offerPrice, offeringShareCount, startDate, endDate);
      result.setUpdated(result.getUpdated() + 1);
      log.debug("자동 동기화 레코드 갱신: corpCode={}", corpCode);
      return;
    }

    // 2) (ipoName, subscriptionStartDate)로 수동 등록 레코드 등 조회
    var existingByNameAndDate =
        ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            corpName, startDate, RecordStatus.ACTIVE);
    if (existingByNameAndDate.isPresent()) {
      // 수동 등록 또는 다른 경로로 생성된 레코드 → 수정하지 않음
      result.setSkippedManual(result.getSkippedManual() + 1);
      log.debug("이름+시작일 매칭 레코드 존재로 건너뜀: corpName={}, startDate={}", corpName, startDate);
      return;
    }

    // 3) 신규 생성
    LocalDateTime now = LocalDateTime.now(clock);
    Ipo newIpo =
        Ipo.builder()
            .companyName(corpName)
            .ipoName(corpName)
            .corpCode(corpCode)
            .stockCode(null)
            .lowerBandPrice(offerPrice)
            .upperBandPrice(offerPrice)
            .fixedPrice(null) // 동기화가 절대 설정하지 않음
            .subscriptionStartDate(startDate)
            .subscriptionEndDate(endDate)
            .listingDate(null) // 동기화가 절대 설정하지 않음
            .offeringShareCount(offeringShareCount)
            .status(RecordStatus.ACTIVE)
            .syncSource(SYNC_SOURCE_OPENDART)
            .lastSyncedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

    try {
      ipoRepository.saveAndFlush(newIpo);
      result.setCreated(result.getCreated() + 1);
      log.debug("신규 IPO 생성: corpCode={}, corpName={}", corpCode, corpName);
    } catch (DataIntegrityViolationException e) {
      // 동시성 등으로 인한 중복 키 위반 → 실패 처리
      throw new RuntimeException("중복 키 위반: " + e.getMessage(), e);
    }
  }

  /** 기존 자동 동기화 레코드 갱신 (fixedPrice, listingDate 보존). */
  private void updateExistingIpo(
      Ipo existing,
      String corpName,
      Long offerPrice,
      Long offeringShareCount,
      LocalDate startDate,
      LocalDate endDate) {

    existing.setCompanyName(corpName);
    existing.setIpoName(corpName);
    if (offerPrice != null) {
      existing.setLowerBandPrice(offerPrice);
      existing.setUpperBandPrice(offerPrice);
    }
    if (offeringShareCount != null) {
      existing.setOfferingShareCount(offeringShareCount);
    }
    existing.setSubscriptionStartDate(startDate);
    existing.setSubscriptionEndDate(endDate);
    // fixedPrice, listingDate는 절대 수정하지 않음 (수동 입력 보호)
    existing.setSyncSource(SYNC_SOURCE_OPENDART);
    existing.setLastSyncedAt(LocalDateTime.now(clock));
    existing.setUpdatedAt(LocalDateTime.now(clock));

    ipoRepository.saveAndFlush(existing);
  }
}
