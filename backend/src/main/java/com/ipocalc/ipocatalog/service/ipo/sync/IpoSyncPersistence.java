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

  /** 기존 레코드를 보존하거나 자동 동기화 IPO를 만들고 갱신한다. */
  public Outcome persist(String corpCode, String corpName, OpenDartEquitySecurityDetail detail) {
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
        ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull(corpCode, RecordStatus.ACTIVE);
    if (existingByCorpCode.isPresent()) {
      Ipo existing = existingByCorpCode.get();
      if (existing.getSyncSource() == null) {
        log.debug("수동 등록 레코드 존재로 건너뜀 (corpCode 매칭): corpCode={}", corpCode);
        return Outcome.SKIPPED_MANUAL;
      }
      updateExistingIpo(existing, corpName, detail, startDate, endDate);
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
      ipoRepository.saveAndFlush(newIpo(corpCode, corpName, detail, startDate, endDate));
      log.debug("신규 IPO 생성: corpCode={}, corpName={}", corpCode, corpName);
      return Outcome.CREATED;
    } catch (DataIntegrityViolationException e) {
      throw new RuntimeException("중복 키 위반: " + e.getMessage(), e);
    }
  }

  public IpoRepository getIpoRepository() {
    return ipoRepository;
  }

  private Ipo newIpo(
      String corpCode,
      String corpName,
      OpenDartEquitySecurityDetail detail,
      LocalDate startDate,
      LocalDate endDate) {
    LocalDateTime now = LocalDateTime.now(clock);
    return Ipo.builder()
        .companyName(corpName)
        .ipoName(corpName)
        .corpCode(corpCode)
        .stockCode(null)
        .lowerBandPrice(detail.getOfferPrice())
        .upperBandPrice(detail.getOfferPrice())
        .fixedPrice(null)
        .subscriptionStartDate(startDate)
        .subscriptionEndDate(endDate)
        .listingDate(null)
        .allotmentDate(detail.getAllotmentDate())
        .refundDate(detail.getRefundDate())
        .offeringShareCount(detail.getOfferingShareCount())
        .status(RecordStatus.ACTIVE)
        .syncSource(SYNC_SOURCE_OPENDART)
        .lastSyncedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private void updateExistingIpo(
      Ipo existing,
      String corpName,
      OpenDartEquitySecurityDetail detail,
      LocalDate startDate,
      LocalDate endDate) {
    LocalDateTime now = LocalDateTime.now(clock);
    existing.syncFromOpenDart(
        corpName,
        detail.getOfferPrice(),
        detail.getOfferingShareCount(),
        startDate,
        endDate,
        detail.getAllotmentDate(),
        detail.getRefundDate(),
        SYNC_SOURCE_OPENDART,
        now);
    ipoRepository.saveAndFlush(existing);
  }

  enum Outcome {
    CREATED,
    UPDATED,
    SKIPPED_MANUAL
  }
}
