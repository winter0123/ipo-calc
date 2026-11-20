package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartRightsIssueListingParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartRightsIssueListingParser.Evidence;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 유상증자 발행실적보고서로 누락된 신주 상장예정일만 보완한다. 회사 코드만 같거나 환불일이 유사한 보고서는 절대로 자동 연결하지 않는다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DartRightsIssueListingService {
  private final IpoRepository ipoRepository;
  private final DartIpoFilingClient filingClient;
  private final DartRightsIssueListingParser parser = new DartRightsIssueListingParser();
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /** 한 번의 보완 결과. 애매한 공시는 자동 저장하지 않고 수동 확인 대상으로 센다. */
  public record Result(int saved, int reviewRequired, int failures) {}

  /** 청약 대상 기간으로 자동 수집 행을 한정하되, 보고서는 청약 이후 120일까지 찾는다. */
  public Result backfill(LocalDate firstSubscription, LocalDate lastSubscription) {
    List<Ipo> rows =
        ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE);
    int saved = 0;
    int review = 0;
    int failures = 0;
    for (Ipo candidate : rows) {
      if (candidate.getListingDate() != null
          || candidate.getCorpCode() == null
          || candidate.getSubscriptionStartDate() == null
          || candidate.getSubscriptionEndDate() == null
          || candidate.getSubscriptionStartDate().isBefore(firstSubscription)
          || candidate.getSubscriptionStartDate().isAfter(lastSubscription)) {
        continue;
      }
      LocalDate start = candidate.getSubscriptionStartDate();
      LocalDate end = candidate.getSubscriptionEndDate().plusDays(120);
      LocalDate now = LocalDate.now(clock);
      if (end.isAfter(now)) {
        end = now;
      }
      if (start.isAfter(end)) {
        continue;
      }
      try {
        var documents = filingClient.findIssuanceResults(candidate.getCorpCode(), start, end);
        var originalIdentity =
            candidate.getSourceReceiptNo() == null
                ? null
                : filingClient.downloadDocument(candidate.getSourceReceiptNo())
                    .map(parser::issueIdentity).orElse(null);
        LocalDate listing = null;
        String evidenceReceipt = null;
        boolean ambiguous = false;
        // 최신 접수부터 검사한다. 같은 회차 정정본의 새 날짜가 우선한다.
        for (var filing : documents) {
          var xml = filingClient.downloadDocument(filing.rceptNo());
          if (xml.isEmpty()) {
            continue;
          }
          List<Evidence> sameIssue =
              parser.parse(xml.get()).stream()
                  .filter(e -> isSameIssue(candidate, e, originalIdentity))
                  .filter(e -> e.listingDate().isAfter(candidate.getSubscriptionEndDate()))
                  .toList();
          var dates = sameIssue.stream().map(Evidence::listingDate).distinct().toList();
          if (dates.size() > 1) {
            ambiguous = true;
            break;
          }
          if (dates.size() == 1) {
            listing = dates.get(0);
            evidenceReceipt = filing.rceptNo();
            break;
          }
        }
        if (ambiguous) {
          review++;
          log.warn("유상 신주 상장일 상충으로 보완 보류: ipoId={}", candidate.getId());
          continue;
        }
        if (listing == null || evidenceReceipt == null) {
          continue;
        }
        final LocalDate dateToSave = listing;
        final String receiptToSave = evidenceReceipt;
        Boolean changed =
            transactionTemplate.execute(
                status -> {
                  var found = ipoRepository.findByIdAndDeletedAtIsNull(candidate.getId());
                  if (found.isEmpty()) {
                    return false;
                  }
                  Ipo ipo = found.get();
                  if (ipo.getSyncSource() == null
                      || ipo.getListingDate() != null
                      || !Objects.equals(ipo.getCorpCode(), candidate.getCorpCode())) {
                    return false;
                  }
                  boolean applied =
                      ipo.fillMissingRightsListing(
                          dateToSave, receiptToSave, LocalDateTime.now(clock));
                  if (applied) {
                    ipoRepository.saveAndFlush(ipo);
                  }
                  return applied;
                });
        if (Boolean.TRUE.equals(changed)) {
          saved++;
        }
      } catch (RuntimeException e) {
        failures++;
        log.warn(
            "유상 신주 상장일 보완 실패: ipoId={}, cause={}", candidate.getId(), e.getClass().getSimpleName());
      }
    }
    return new Result(saved, review, failures);
  }

  private static boolean isSameIssue(
      Ipo ipo, Evidence evidence,
      DartRightsIssueListingParser.IssueIdentity original) {
    String sourceReceipt = ipo.getSourceReceiptNo();
    if (sourceReceipt == null || sourceReceipt.isBlank()) {
      return false;
    }
    // 명시된 원신고번호가 다른 회차이면 동일 회사라도 절대 연결하지 않는다.
    if (evidence.originalReceipt() != null) {
      return sourceReceipt.equals(evidence.originalReceipt());
    }
    // 원신고 연결번호가 누락된 보고서는 원공시의 납입일 및 신주 수와 동시에 대조한다.
    // 앱의 환불일은 납입일로 쓰지 않는다.
    return original != null && original.paymentDate() != null
        && original.issuedShares() != null
        && evidence.paymentDate() != null && evidence.issuedShares() != null
        && ipo.getOfferingShareCount() != null
        && original.paymentDate().equals(evidence.paymentDate())
        && original.issuedShares().equals(evidence.issuedShares())
        && original.issuedShares().equals(ipo.getOfferingShareCount());
  }
}
