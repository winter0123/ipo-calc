package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
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

  /** 기존 레코드를 보존하거나 자동 동기화 IPO를 만들고 갱신한다. correctionReport는 DS001의 정정/발행조건확정 공시 여부다. */
  public Outcome persist(
      String corpCode,
      String corpName,
      OpenDartEquitySecurityDetail detail,
      boolean finalizedReport,
      boolean correctionReport) {
    if (detail.getRceptNo() != null
        && ipoRepository.existsByCorpCodeAndWithdrawnReceiptNo(corpCode, detail.getRceptNo())) {
      return Outcome.SKIPPED_WITHDRAWN;
    }
    LocalDate startDate = detail.getSubscriptionStartDate();
    LocalDate endDate = detail.getSubscriptionEndDate();

    if (endDate == null) {
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
        // 새 공모인지 정정 공시인지 구분할 수 없으면 가격·주식수·회사명·접수번호를 포함한
        // 기존 값을 전부 보존하고 갱신을 보류한다. 다른 공모로 receipt를 덮어쓰면 철회 매칭이 망가진다
        log.warn("신규 공모/정정 구분 불가로 기존 자동 공모 갱신 보류: corpCode={}", corpCode);
        return Outcome.HELD_UNDETERMINED;
      }
      updateExistingIpo(existing, corpName, detail, startDate, endDate, finalizedReport);
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
          newIpo(corpCode, corpName, detail, startDate, endDate, finalizedReport));
      log.debug("신규 IPO 생성: corpCode={}, corpName={}", corpCode, corpName);
      return Outcome.CREATED;
    } catch (DataIntegrityViolationException e) {
      throw new IllegalStateException("중복 키 위반: " + e.getClass().getSimpleName(), e);
    }
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
      boolean finalizedReport) {
    LocalDateTime now = LocalDateTime.now(clock);
    Long confirmedPrice = finalizedReport ? detail.getOfferPrice() : null;
    return Ipo.builder()
        .companyName(corpName)
        .ipoName(corpName)
        .corpCode(corpCode)
        .stockCode(null)
        .lowerBandPrice(detail.getOfferPrice())
        .upperBandPrice(detail.getOfferPrice())
        .fixedPrice(confirmedPrice)
        .subscriptionStartDate(startDate)
        .subscriptionEndDate(endDate)
        .listingDate(null)
        .allotmentDate(detail.getAllotmentDate())
        .refundDate(detail.getRefundDate())
        .offeringShareCount(detail.getOfferingShareCount())
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
    // corp당 ACTIVE 1건 경로에서만 persist가 여기까지 오므로, 정정·확정 공시는 시작일 변경도 같은 공모
    if (correctionReport) {
      return true;
    }
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
      boolean finalizedReport) {
    LocalDateTime now = LocalDateTime.now(clock);
    Long confirmedPrice = finalizedReport ? detail.getOfferPrice() : null;
    existing.syncFromOpenDart(
        corpName,
        detail.getOfferPrice(),
        confirmedPrice,
        detail.getOfferingShareCount(),
        startDate,
        endDate,
        detail.getAllotmentDate(),
        detail.getRefundDate(),
        SYNC_SOURCE_OPENDART,
        now);
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
