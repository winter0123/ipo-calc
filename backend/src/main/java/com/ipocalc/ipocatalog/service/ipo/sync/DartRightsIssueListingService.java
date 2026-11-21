package com.ipocalc.ipocatalog.service.ipo.sync;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartFilingOrigin;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartRightsIssueListingParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartRightsIssueListingParser.Evidence;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      if ((candidate.getListingDate() != null
              && !"ESTIMATED_DART".equals(candidate.getListingDateEvidenceType()))
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
        if (documents.isEmpty()) {
          continue;
        }
        // 각 종목이 끝나면 XML 캐시 전체가 GC 대상이 된다.
        Map<String, String> documentCache = new HashMap<>();
        String sourceReceipt = candidate.getSourceReceiptNo();
        String originalXml =
            sourceReceipt == null ? null : document(filingClient, documentCache, sourceReceipt);
        var originalIdentity = originalXml == null ? null : parser.issueIdentity(originalXml);
        LocalDate sourceDate = receiptDate(sourceReceipt);
        List<DartIpoFilingClient.FilingRef> history =
            sourceDate == null
                ? List.of()
                : filingClient.findEquityFilings(candidate.getCorpCode(), sourceDate, end);
        List<OpenDartDisclosureItem> disclosures = new ArrayList<>();
        for (var filing : history) {
          disclosures.add(disclosure(candidate.getCorpCode(), filing));
        }
        // 원신고가 목록 조회 범위에 없어도 최초 접수번호를 회차 기준으로 보존한다.
        if (sourceDate != null
            && disclosures.stream().noneMatch(d -> sourceReceipt.equals(d.getRceptNo()))) {
          disclosures.add(
              OpenDartDisclosureItem.builder()
                  .corpCode(candidate.getCorpCode())
                  .rceptNo(sourceReceipt)
                  .rceptDt(sourceDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                  .reportNm("증권신고서(지분증권)")
                  .build());
        }
        LocalDate listing = null;
        String evidenceReceipt = null;
        boolean ambiguous = false;
        // 최신 접수부터 검사한다. 같은 회차 정정본의 새 날짜가 우선한다.
        for (var filing : documents) {
          String xml = document(filingClient, documentCache, filing.rceptNo());
          if (xml == null) {
            continue;
          }
          var finalIdentity =
              latestIdentity(
                  candidate,
                  history,
                  disclosures,
                  documentCache,
                  filing.receivedOn(),
                  originalIdentity);
          List<Evidence> sameIssue =
              parser.parse(xml).stream()
                  .filter(e -> isSameIssue(candidate, e, finalIdentity, xml))
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
          if (!documents.isEmpty()) {
            review++;
            log.warn(
                "유상 신주 상장일 회차 연결/증거 확인 필요: ipoId={}, corpCode={}, reports={}",
                candidate.getId(),
                candidate.getCorpCode(),
                documents.size());
          }
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
                      || (ipo.getListingDate() != null
                          && !"ESTIMATED_DART".equals(ipo.getListingDateEvidenceType()))
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

  private static LocalDate receiptDate(String receipt) {
    if (receipt == null || !receipt.matches("\\d{14}")) {
      return null;
    }
    try {
      return LocalDate.parse(receipt.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static OpenDartDisclosureItem disclosure(
      String corpCode, DartIpoFilingClient.FilingRef ref) {
    return OpenDartDisclosureItem.builder()
        .corpCode(corpCode)
        .rceptNo(ref.rceptNo())
        .rceptDt(ref.receivedOn().format(DateTimeFormatter.BASIC_ISO_DATE))
        .reportNm(ref.reportName())
        .build();
  }

  private static String document(
      DartIpoFilingClient client, Map<String, String> cache, String receipt) {
    if (cache.containsKey(receipt)) {
      return cache.get(receipt);
    }
    String xml = client.downloadDocument(receipt).orElse(null);
    cache.put(receipt, xml);
    return xml;
  }

  private DartRightsIssueListingParser.IssueIdentity latestIdentity(
      Ipo ipo,
      List<DartIpoFilingClient.FilingRef> history,
      List<OpenDartDisclosureItem> disclosures,
      Map<String, String> cache,
      LocalDate reportDate,
      DartRightsIssueListingParser.IssueIdentity original) {
    LocalDate payment = original == null ? null : original.paymentDate();
    Long shares = original == null ? null : original.issuedShares();
    for (var filing : history) {
      if (filing.receivedOn().isAfter(reportDate)
          || filing.rceptNo().equals(ipo.getSourceReceiptNo())) {
        continue;
      }
      String xml = document(filingClient, cache, filing.rceptNo());
      if (xml == null) {
        continue;
      }
      String origin =
          DartFilingOrigin.resolveOriginalReceipt(
              disclosure(ipo.getCorpCode(), filing), disclosures, xml);
      if (!ipo.getSourceReceiptNo().equals(origin)) {
        continue;
      }
      var identity = parser.issueIdentity(xml);
      if (identity.paymentDate() != null) {
        payment = identity.paymentDate();
      }
      if (identity.issuedShares() != null) {
        shares = identity.issuedShares();
      }
    }
    return new DartRightsIssueListingParser.IssueIdentity(payment, shares);
  }

  private boolean isSameIssue(
      Ipo ipo,
      Evidence evidence,
      DartRightsIssueListingParser.IssueIdentity latest,
      String reportXml) {
    String source = ipo.getSourceReceiptNo();
    if (source == null || source.isBlank()) {
      return false;
    }
    if (evidence.originalReceipt() != null && !source.equals(evidence.originalReceipt())) {
      return false;
    }
    // 명시적 회차 번호가 없는 실적보고서는 최신 확정 조건의 날짜와 수량을 모두 요구한다.
    if (latest == null
        || latest.paymentDate() == null
        || latest.issuedShares() == null
        || evidence.paymentDate() == null
        || evidence.issuedShares() == null
        || !latest.paymentDate().equals(evidence.paymentDate())) {
      // 원신고번호가 명시된 기존 보고서에는 수량 정보가 없을 수 있다.
      return source.equals(evidence.originalReceipt())
          && (latest == null
              || latest.paymentDate() == null
              || evidence.paymentDate() == null
              || latest.paymentDate().equals(evidence.paymentDate()))
          && (latest == null
              || latest.issuedShares() == null
              || evidence.issuedShares() == null
              || latest.issuedShares().equals(evidence.issuedShares()));
    }
    if (latest.issuedShares().equals(evidence.issuedShares())) {
      return true;
    }
    return parser.hasVerifiedUnissuedShares(
        reportXml, latest.issuedShares(), evidence.issuedShares());
  }
}
