package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** OpenDART에서 확인한 회사 한 건을 IPO 저장소에 반영한다. 호출자가 건별 트랜잭션을 소유한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class IpoSyncPersistence {
  private static final String SYNC_SOURCE_OPENDART = "OPENDART";

  private final IpoRepository ipoRepository;
  private final BusinessDayCalculator businessDayCalculator;
  private final Clock clock;
  private final OpenDartEquitySecuritiesClient equitySecuritiesClient;

  /**
   * 기존 레코드를 보존하거나 자동 동기화 IPO를 만들고 갱신한다. 가격은 DS006에서 가져오지 않고, 별도 파싱된 DartOfferingTerms를 통해 검증 후
   * 적용한다.
   */
  public Outcome persist(
      String corpCode,
      String corpName,
      OpenDartEquitySecurityDetail detail,
      boolean finalizedReport,
      boolean correctionReport,
      DartOfferingTerms offeringTerms) {
    if (detail.getRceptNo() != null
        && ipoRepository.existsByCorpCodeAndWithdrawnReceiptNo(corpCode, detail.getRceptNo())) {
      return Outcome.SKIPPED_WITHDRAWN;
    }
    LocalDate startDate = detail.getSubscriptionStartDate();
    LocalDate endDate = detail.getSubscriptionEndDate();
    if (startDate == null || detail.isSubscriptionEndUnreadable()) {
      log.warn("청약일 검증 불가로 자동 저장 보류: corpCode={}", corpCode);
      return Outcome.HELD_UNDETERMINED;
    }
    if (endDate != null && endDate.isBefore(startDate)) {
      log.warn("청약일 순서 오류로 자동 저장 보류: corpCode={}", corpCode);
      return Outcome.HELD_UNDETERMINED;
    }

    if (endDate == null && startDate != null) {
      endDate = businessDayCalculator.nextBusinessDay(startDate);
      log.debug(
          "청약 종료일 계산 (다음 영업일): corpCode={}, startDate={}, endDate={}",
          corpCode,
          startDate,
          endDate);
    }

    var existingByCorpCode =
        ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, RecordStatus.ACTIVE);
    if (existingByCorpCode.size() > 1) {
      // 동일 회사 ACTIVE 자동 공모가 2건 이상이면 어느 건인지 구분할 수 없어 갱신을 덮어쓰지 않고 보류한다
      log.warn(
          "동일 회사 ACTIVE 복수 건으로 자동 갱신 보류: corpCode={}, count={}",
          corpCode,
          existingByCorpCode.size());
      return Outcome.BLOCKED_MULTIPLE;
    }
    if (!existingByCorpCode.isEmpty()) {
      Ipo existing = existingByCorpCode.get(0);
      if (existing.getSyncSource() == null) {
        log.debug("수동 등록 레코드 존재로 건너뜀 (corpCode 매칭): corpCode={}", corpCode);
        return Outcome.SKIPPED_MANUAL;
      }
      if (!isSameOffering(existing, detail, correctionReport)) {
        // 동일 회차 근거가 없으면 가격뿐 아니라 일정·주식 수도 모두 보존한다.
        log.warn("신규 공모/정정 구분 불가로 자동 갱신 보류: corpCode={}", corpCode);
        return Outcome.HELD_UNDETERMINED;
      }
      updateExistingIpo(existing, corpName, detail, startDate, endDate, offeringTerms);
      log.debug("자동 동기화 레코드 갱신: corpCode={}", corpCode);
      return Outcome.UPDATED;
    }

    String normalizedName = IpoScheduleMergeService.normalizeName(corpName);
    boolean sameNameAndDate =
        ipoRepository
            .findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(startDate, RecordStatus.ACTIVE)
            .stream()
            .anyMatch(
                ipo ->
                    ipo.getIpoName() != null
                        && IpoScheduleMergeService.normalizeName(ipo.getIpoName())
                            .equals(normalizedName));
    if (sameNameAndDate) {
      log.debug("이름+시작일 매칭 레코드 존재로 건너뜀: corpName={}, startDate={}", corpName, startDate);
      return Outcome.SKIPPED_MANUAL;
    }

    try {
      ipoRepository.saveAndFlush(
          newIpo(corpCode, corpName, detail, startDate, endDate, offeringTerms));
      log.debug("신규 IPO 생성: corpCode={}, corpName={}", corpCode, corpName);
      return Outcome.CREATED;
    } catch (DataIntegrityViolationException e) {
      throw new IllegalStateException("중복 키 위반: " + e.getClass().getSimpleName(), e);
    }
  }

  /** DS001 원문과 원신고 접수번호를 검증한 동기화 경로다. DS006가 오래되었거나 누락되어도 원문 가격은 반영하고, 오래된 상세의 일정은 반영하지 않는다. */
  public Outcome persistVerifiedFiling(
      String corpCode,
      String corpName,
      String filingReceipt,
      String originalReceipt,
      OpenDartEquitySecurityDetail detail,
      DartOfferingTerms latestTerms,
      DartOfferingTerms historicalBand) {
    if (filingReceipt == null
        || originalReceipt == null
        || latestTerms == null
        || !filingReceipt.equals(latestTerms.sourceReceiptNo())) {
      return Outcome.HELD_UNDETERMINED;
    }
    if (ipoRepository.existsByCorpCodeAndWithdrawnReceiptNo(corpCode, originalReceipt)
        || ipoRepository.existsByCorpCodeAndWithdrawnReceiptNo(corpCode, filingReceipt)) {
      return Outcome.SKIPPED_WITHDRAWN;
    }
    var active =
        ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, RecordStatus.ACTIVE);
    if (active.size() > 1) {
      return Outcome.BLOCKED_MULTIPLE;
    }
    // 같은 접수번호의 DS006만 최신 일정의 보조 근거로 사용한다.
    boolean currentDetail = detail != null && filingReceipt.equals(detail.getRceptNo());
    boolean validRawSchedule =
        latestTerms.subscriptionStartDate() != null
            && latestTerms.subscriptionEndDate() != null
            && !latestTerms.subscriptionEndDate().isBefore(latestTerms.subscriptionStartDate());
    LocalDate start =
        validRawSchedule
            ? latestTerms.subscriptionStartDate()
            : currentDetail ? detail.getSubscriptionStartDate() : null;
    LocalDate end =
        validRawSchedule
            ? latestTerms.subscriptionEndDate()
            : currentDetail ? detail.getSubscriptionEndDate() : null;
    if (currentDetail && detail.isSubscriptionEndUnreadable() && !validRawSchedule) {
      start = null;
    }
    if (start != null && end == null && currentDetail) {
      end = businessDayCalculator.nextBusinessDay(start);
    }
    if (start != null && end != null && end.isBefore(start)) {
      start = null;
    }
    LocalDate allotment =
        latestTerms.allotmentDate() != null
            ? latestTerms.allotmentDate()
            : currentDetail ? detail.getAllotmentDate() : null;
    LocalDate refund =
        latestTerms.refundDate() != null
            ? latestTerms.refundDate()
            : currentDetail ? detail.getRefundDate() : null;
    Long shares = currentDetail ? detail.getOfferingShareCount() : null;

    if (!active.isEmpty()) {
      Ipo existing = active.get(0);
      if (existing.getSyncSource() == null) {
        return Outcome.SKIPPED_MANUAL;
      }
      if (!originalReceipt.equals(existing.getSourceReceiptNo())) {
        return Outcome.HELD_UNDETERMINED;
      }
      // 일정 출처를 독립 보존하는 컬럼이 없으므로 이미 검증한 더 최신 가격 공시보다
      // 오래된 공시는 일정까지 통째로 건너뛰어 회귀를 방지한다.
      if (isOlderThanVerifiedPrice(filingReceipt, existing)) {
        return Outcome.HELD_UNDETERMINED;
      }
      LocalDateTime now = LocalDateTime.now(clock);
      boolean currentSchedule = existing.mayApplyVerifiedSchedule(filingReceipt);
      existing.syncFromOpenDart(
          corpName,
          null,
          currentSchedule ? start : null,
          currentSchedule ? end : null,
          currentSchedule && existing.getAllotmentDate() == null ? allotment : null,
          currentSchedule ? refund : null,
          SYNC_SOURCE_OPENDART,
          now);
      if (currentSchedule && (start != null || end != null || allotment != null || refund != null)) {
        existing.recordVerifiedScheduleReceipt(filingReceipt);
      }
      if (currentDetail) {
        existing.applyVerifiedShareCount(shares, filingReceipt);
      }
      if (historicalBand != null && historicalBand.hasValidBand()) {
        existing.applyVerifiedOfferingTerms(historicalBand, now);
      }
      existing.applyVerifiedOfferingTerms(latestTerms, now);
      ipoRepository.saveAndFlush(existing);
      return Outcome.UPDATED;
    }

    // 신규 IPO는 원신고가 검증됐더라도 유효한 청약 시작/종료일 없이는 생성하지 않는다.
    if (start == null || end == null) {
      return Outcome.HELD_UNDETERMINED;
    }
    String normalizedName = IpoScheduleMergeService.normalizeName(corpName);
    final LocalDate verifiedStart = start;
    boolean duplicate =
        ipoRepository
            .findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
                verifiedStart, RecordStatus.ACTIVE)
            .stream()
            .anyMatch(
                ipo ->
                    ipo.getIpoName() != null
                        && IpoScheduleMergeService.normalizeName(ipo.getIpoName())
                            .equals(normalizedName));
    if (duplicate) {
      return Outcome.SKIPPED_MANUAL;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    Ipo ipo =
        Ipo.builder()
            .companyName(corpName)
            .ipoName(corpName)
            .corpCode(corpCode)
            .subscriptionStartDate(start)
            .subscriptionEndDate(end)
            .allotmentDate(allotment)
            .refundDate(refund)
            .offeringShareCount(shares)
            .lastImportedOfferingShareCount(shares)
            .lastVerifiedShareReceiptNo(currentDetail && shares != null ? filingReceipt : null)
            .lastVerifiedScheduleReceiptNo(filingReceipt)
            .status(RecordStatus.ACTIVE)
            .syncSource(SYNC_SOURCE_OPENDART)
            .sourceReceiptNo(originalReceipt)
            .lastImportedSubscriptionStartDate(start)
            .lastImportedSubscriptionEndDate(end)
            .lastImportedRefundDate(refund)
            .lastSyncedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    if (historicalBand != null && historicalBand.hasValidBand()) {
      ipo.applyVerifiedOfferingTerms(historicalBand, now);
    }
    ipo.applyVerifiedOfferingTerms(latestTerms, now);
    ipoRepository.saveAndFlush(ipo);
    return Outcome.CREATED;
  }

  private static boolean isOlderThanVerifiedPrice(String receipt, Ipo ipo) {
    return (ipo.getBandPriceSourceReceiptNo() != null
            && receipt.compareTo(ipo.getBandPriceSourceReceiptNo()) < 0)
        || (ipo.getFixedPriceSourceReceiptNo() != null
            && receipt.compareTo(ipo.getFixedPriceSourceReceiptNo()) < 0);
  }

  /** 공시 접수번호와 청약 시작일로 확인한 활성 자동 공모만 철회한다. 이미 철회된 경우 멱등하게 true 반환. */
  public boolean withdraw(String corpCode, OpenDartEquitySecurityDetail detail) {
    if (detail.getRceptNo() == null || detail.getSubscriptionStartDate() == null) {
      return false;
    }
    // 이미 같은 접수번호로 철회 처리된 경우 멱등 성공 처리 (save 없이 true 반환)
    if (ipoRepository.existsByCorpCodeAndWithdrawnReceiptNo(corpCode, detail.getRceptNo())) {
      return true;
    }
    var existing =
        ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, RecordStatus.ACTIVE);
    if (existing.size() != 1
        || existing.get(0).getSyncSource() == null
        || !detail.getSubscriptionStartDate().equals(existing.get(0).getSubscriptionStartDate())) {
      return false;
    }
    existing.get(0).withdraw(detail.getRceptNo(), LocalDateTime.now(clock));
    ipoRepository.saveAndFlush(existing.get(0));
    return true;
  }

  public IpoRepository getIpoRepository() {
    return ipoRepository;
  }

  private Ipo newIpo(
      String corpCode,
      String corpName,
      OpenDartEquitySecurityDetail detail,
      LocalDate startDate,
      LocalDate endDate,
      DartOfferingTerms offeringTerms) {
    LocalDateTime now = LocalDateTime.now(clock);
    Ipo ipo =
        Ipo.builder()
            .companyName(corpName)
            .ipoName(corpName)
            .corpCode(corpCode)
            .stockCode(null)
            .subscriptionStartDate(startDate)
            .subscriptionEndDate(endDate)
            .listingDate(null)
            .allotmentDate(detail.getAllotmentDate())
            .refundDate(detail.getRefundDate())
            .offeringShareCount(detail.getOfferingShareCount())
            .lastImportedOfferingShareCount(detail.getOfferingShareCount())
            .lastVerifiedShareReceiptNo(detail.getRceptNo())
            .lastVerifiedScheduleReceiptNo(detail.getRceptNo())
            .status(RecordStatus.ACTIVE)
            .syncSource(SYNC_SOURCE_OPENDART)
            .sourceReceiptNo(detail.getRceptNo())
            .lastImportedSubscriptionStartDate(startDate)
            .lastImportedSubscriptionEndDate(endDate)
            .lastImportedRefundDate(detail.getRefundDate())
            .lastSyncedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

    // 원문에서 검증된 가격 정보가 있으면 적용
    if (offeringTerms != null) {
      ipo.applyVerifiedOfferingTerms(offeringTerms, now);
    }

    return ipo;
  }

  /**
   * 이 상세가 기존 자동 공모와 같은 공모인지 판단한다.
   *
   * <ul>
   *   <li>기존 행의 source_receipt_no가 상세의 접수번호와 일치하면 같은 공모(확정).
   *   <li>정정·발행조건확정 공시(correctionReport)이면, 호출 전 corp당 ACTIVE가 1건임이 보장되므로 청약 시작일이 달라도 같은 공모로 본다(시작일
   *       변경 정정 반영).
   *   <li>기존 행이 레거시(source_receipt_no 없음)면 청약 시작일이 같을 때만 같은 공모로 본다.
   *   <li>그 외(다른 접수번호·다른 시작일·정정 근거 없음)는 구분할 수 없으므로 갱신을 보류해야 한다.
   * </ul>
   */
  private boolean isSameOffering(
      Ipo existing, OpenDartEquitySecurityDetail detail, boolean correctionReport) {
    if (detail.getRceptNo() != null
        && existing.getSourceReceiptNo() != null
        && existing.getSourceReceiptNo().equals(detail.getRceptNo())) {
      return true;
    }
    // 정정 유형이라는 사실이나 회사당 활성 행 하나만으로는 동일 회차가 증명되지 않는다.
    // 접수번호가 다른 정정은 근거 연결이 확인되기 전까지 보류한다.
    boolean sameStart =
        existing.getSubscriptionStartDate() != null
            && detail.getSubscriptionStartDate() != null
            && existing.getSubscriptionStartDate().equals(detail.getSubscriptionStartDate());
    if (!sameStart) {
      return false;
    }
    // 레거시 행은 청약 시작일이 같을 때만 같은 공모로 인정한다
    return existing.getSourceReceiptNo() == null;
  }

  private void updateExistingIpo(
      Ipo existing,
      String corpName,
      OpenDartEquitySecurityDetail detail,
      LocalDate startDate,
      LocalDate endDate,
      DartOfferingTerms offeringTerms) {
    LocalDateTime now = LocalDateTime.now(clock);
    // 과거 접수번호가 다시 도착한 경우 일정·주식 수는 후퇴시키지 않는다.
    boolean currentSchedule = existing.mayApplyVerifiedSchedule(detail.getRceptNo());
    existing.syncFromOpenDart(
        corpName,
        null,
        currentSchedule ? startDate : null,
        currentSchedule ? endDate : null,
        currentSchedule && existing.getAllotmentDate() == null ? detail.getAllotmentDate() : null,
        currentSchedule ? detail.getRefundDate() : null,
        SYNC_SOURCE_OPENDART,
        now);
    if (currentSchedule) {
      existing.recordVerifiedScheduleReceipt(detail.getRceptNo());
    }
    existing.applyVerifiedShareCount(detail.getOfferingShareCount(), detail.getRceptNo());
    // 원문에서 검증된 가격 정보가 있으면 적용
    if (offeringTerms != null) {
      existing.applyVerifiedOfferingTerms(offeringTerms, now);
    }
    // 레거시 행(source_receipt_no 없음)만 이 시점에 원공시 receipt를 연결한다.
    // 이미 receipt가 있는 행은 철회 매칭 근거를 유지하기 위해 덮어쓰지 않는다
    if (existing.getSourceReceiptNo() == null && detail.getRceptNo() != null) {
      existing.recordSyncSourceReceipt(detail.getRceptNo());
    }
    ipoRepository.saveAndFlush(existing);
  }

  enum Outcome {
    CREATED,
    UPDATED,
    SKIPPED_MANUAL,
    SKIPPED_WITHDRAWN,
    BLOCKED_MULTIPLE,
    HELD_UNDETERMINED
  }
}
