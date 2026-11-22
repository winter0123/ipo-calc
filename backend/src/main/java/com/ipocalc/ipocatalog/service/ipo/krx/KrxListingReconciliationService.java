package com.ipocalc.ipocatalog.service.ipo.krx;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.controller.ipo.dto.KrxListingApplyRequest;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartRightsIssueListingParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartRightsIssueListingParser.Evidence;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** KIND 공고 기반 KRX 확정 서비스. */
@Service
@Slf4j
public class KrxListingReconciliationService {

  private final IpoRepository ipoRepository;
  private final DartIpoFilingClient dartIpoFilingClient;
  private final KrxListingNoticeClient noticeClient;
  private final KrxListingNoticeParser noticeParser;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /** 생성자. */
  @Autowired
  public KrxListingReconciliationService(
      IpoRepository ipoRepository,
      DartIpoFilingClient dartIpoFilingClient,
      KrxListingNoticeClient noticeClient,
      KrxListingNoticeParser noticeParser,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.ipoRepository = ipoRepository;
    this.dartIpoFilingClient = dartIpoFilingClient;
    this.noticeClient = noticeClient;
    this.noticeParser = noticeParser;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /** 70760 신규상장 미리보기는 추가상장 발행회차와 별개로 검증한다. */
  public NewListingPreview previewNewListing(Long ipoId, String noticeUrl) {
    KrxListingNoticeClient.validateNewListingUrl(noticeUrl);
    String html = noticeClient.fetchNewListing(noticeUrl);
    var evidence = new KrxNewListingNoticeParser().parse(html);
    String hash = sha256(html);
    List<String> reasons = new ArrayList<>();
    Ipo ipo = ipoRepository.findById(ipoId).orElse(null);
    if (ipo == null || ipo.getDeletedAt() != null || ipo.getStatus() != RecordStatus.ACTIVE) {
      reasons.add("활성 IPO를 찾을 수 없습니다");
      return new NewListingPreview(evidence.listingDate(), hash, false, false, reasons);
    }
    boolean idempotent =
        "CONFIRMED_KRX".equals(ipo.getListingDateEvidenceType())
            && noticeUrl.equals(ipo.getKrxListingNoticeUrl())
            && evidence.listingDate().equals(ipo.getListingDate());
    if (!evidence.stockCode().equals(ipo.getStockCode())) {
      reasons.add("공식 신규상장 종목코드가 다릅니다");
    }
    if (ipo.getSyncSource() == null
        || ipo.getSourceReceiptNo() == null
        || !ipo.getSourceReceiptNo().matches("\\d{14}")
        || ipo.getWithdrawnReceiptNo() != null) {
      reasons.add("원신고가 확인된 자동 수집 IPO가 아닙니다");
    }
    if (ipo.getSubscriptionEndDate() == null
        || !evidence.listingDate().isAfter(ipo.getSubscriptionEndDate())) {
      reasons.add("상장일이 청약 종료일 이후가 아닙니다");
    }
    boolean importedDate =
        ipo.getListingDate() != null
            && ipo.getLastImportedListingDate() != null
            && ipo.getListingDate().equals(ipo.getLastImportedListingDate());
    if (!idempotent
        && (ipo.getListingDateEvidenceType() != null
            || ipo.getListingDateEvidenceReceipt() != null
            || ipo.getListingOpenPrice() != null
            || ipo.getKrxListingNoticeUrl() != null
            || (ipo.getListingDate() != null && !importedDate)
            || (ipo.getListingDate() == null && ipo.getLastImportedListingDate() != null))) {
      reasons.add("기존 수동 상장일 또는 출처를 자동 덮어쓸 수 없습니다");
    }
    Optional<Ipo> same =
        ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull(
            evidence.stockCode(), RecordStatus.ACTIVE);
    if (same.isPresent() && !same.get().getId().equals(ipoId)) {
      reasons.add("같은 종목코드의 다른 활성 IPO가 있습니다");
    }
    return new NewListingPreview(
        evidence.listingDate(), hash, reasons.isEmpty(), idempotent, reasons);
  }

  /** 검증된 신규상장 미리보기만 재조회·조건부 UPDATE로 저장한다. */
  public KrxListingApplyResult applyNewListing(
      Long ipoId,
      String url,
      LocalDate expectedDate,
      String expectedHash,
      String expectedSourceReceipt) {
    if (expectedDate == null || expectedHash == null || expectedHash.isBlank()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "new listing evidence required");
    }
    NewListingPreview preview = previewNewListing(ipoId, url);
    if (!expectedDate.equals(preview.listingDate()) || !expectedHash.equals(preview.noticeHash())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale new listing notice");
    }
    if (!preview.applyAllowed()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "new listing conditions changed");
    }
    String currentHtml = noticeClient.fetchNewListing(url);
    if (!expectedHash.equals(sha256(currentHtml))) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale new listing notice");
    }
    String stockCode = new KrxNewListingNoticeParser().parse(currentHtml).stockCode();
    String status =
        transactionTemplate.execute(
            tx -> {
              Ipo current = ipoRepository.findByIdAndDeletedAtIsNullForUpdate(ipoId).orElse(null);
              if (current == null
                  || expectedSourceReceipt == null
                  || !expectedSourceReceipt.equals(current.getSourceReceiptNo())) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "ipo source changed");
              }
              // 미리보기 이후 청약 종료일 변경을 반드시 잠금 안에서 재검증한다.
              if (current.getSubscriptionEndDate() == null
                  || !current.getSubscriptionEndDate().isBefore(expectedDate)
                  || current.getStatus() != RecordStatus.ACTIVE
                  || current.getWithdrawnReceiptNo() != null
                  || current.getSyncSource() == null
                  || !stockCode.equals(current.getStockCode())) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "ipo conditions changed");
              }
              String receipt = current.getSourceReceiptNo();
              if (ipoRepository.countSameConfirmedNewKrxCandidate(
                      ipoId, stockCode, receipt, expectedDate, url) == 1) {
                return "NO_OP";
              }
              int updated;
              if (current.getListingDate() == null) {
                updated =
                    ipoRepository.confirmNewKrxListingIfEmpty(
                        ipoId, stockCode, receipt, expectedDate, url, LocalDateTime.now(clock));
              } else if (current.getLastImportedListingDate() != null
                  && current.getListingDate().equals(current.getLastImportedListingDate())) {
                updated =
                    ipoRepository.confirmNewKrxListingIfImported(
                        ipoId,
                        stockCode,
                        receipt,
                        current.getListingDate(),
                        expectedDate,
                        url,
                        LocalDateTime.now(clock));
              } else {
                updated = 0;
              }
              if (updated == 1) {
                return "APPLIED";
              }
              throw new org.springframework.web.server.ResponseStatusException(
                  org.springframework.http.HttpStatus.CONFLICT, "concurrent new listing change");
            });
    return new KrxListingApplyResult(ipoId, true, status, preview.reasons());
  }

  /** 공식 신규상장 원문과 읽기 시점의 해시를 보존한다. */
  public record NewListingPreview(
      LocalDate listingDate,
      String noticeHash,
      boolean applyAllowed,
      boolean idempotent,
      List<String> reasons) {}

  /** 미리보기를 수행한다. */
  public KrxListingReconciliationPreview preview(Long ipoId, String noticeUrl) {
    List<String> reasons = new ArrayList<>();
    Ipo ipo = ipoRepository.findById(ipoId).orElse(null);
    if (ipo == null || ipo.getDeletedAt() != null) {
      reasons.add("IPO를 찾을 수 없습니다");
      return emptyPreview(ipoId, reasons);
    }

    String html;
    KrxListingNotice notice;
    String noticeHash;
    try {
      KrxListingNoticeClient.validateNoticeUrl(noticeUrl);
      html = noticeClient.fetch(noticeUrl);
      notice = noticeParser.parse(html, noticeUrl);
      noticeHash = sha256(html);
    } catch (RuntimeException e) {
      reasons.add("KIND 공고를 읽을 수 없습니다: " + e.getMessage());
      throw new IllegalStateException("KIND 공고 원문 조회·파싱 실패", e);
    }

    boolean stockCodeMatches =
        notice.stockCode() != null && notice.stockCode().equals(ipo.getStockCode());
    if (!stockCodeMatches && notice.stockCode() != null) {
      reasons.add("종목코드가 일치하지 않습니다");
    }

    boolean stockTypeCommon = notice.stockType() != null && notice.stockType().contains("보통주");
    if (!stockTypeCommon) {
      reasons.add("보통주가 아닌 주권 종류입니다");
    }

    boolean reasonAllowed = isPaidInCapitalReason(notice.reason());
    if (!reasonAllowed) {
      reasons.add("유상증자 사유가 아니거나 제외 사유입니다");
    }

    int commonPaidInRowCount = noticeParser.countCommonPaidInRows(html);
    boolean singleMatch = commonPaidInRowCount == 1;
    if (!singleMatch) {
      reasons.add("유상 보통주 발행 행이 정확히 1건이 아닙니다");
    }

    DartEvidence dart = loadDart(ipo, reasons);
    boolean issuedSharesMatch = false;
    if (dart != null && notice.issuedShares() != null && dart.issuedShares() != null) {
      issuedSharesMatch = notice.issuedShares().equals(dart.issuedShares());
      if (!issuedSharesMatch) {
        reasons.add("실제 발행주식수가 일치하지 않습니다");
      }
    } else {
      if (notice.issuedShares() != null) {
        reasons.add("DART 실제 발행주식수를 확인할 수 없습니다");
      }
    }

    boolean issuePriceMatch = false;
    if (dart != null && notice.issuePrice() != null && dart.issuePrice() != null) {
      issuePriceMatch = notice.issuePrice().equals(dart.issuePrice());
      if (!issuePriceMatch) {
        reasons.add("발행가가 일치하지 않습니다");
      }
    } else {
      if (notice.issuePrice() != null) {
        reasons.add("DART 확정 발행가를 확인할 수 없습니다");
      }
    }

    boolean datesValid = true;
    if (notice.listingDate() == null) {
      reasons.add("KIND 공고에서 상장일을 확인할 수 없습니다");
      datesValid = false;
    }
    // DART 상장예정일은 KIND 공식 확정일과 달라도 회차 동일성의 근거가 아니다.
    // 실제 발행수량, 발행가, 보통주 종류, 납입일·발행일 관계로만 매칭한다.
    if (dart != null && dart.paymentDate() != null && notice.issueDate() != null) {
      long daysAfterPayment =
          java.time.temporal.ChronoUnit.DAYS.between(dart.paymentDate(), notice.issueDate());
      // 날짜 간격은 다른 회차를 배제하는 보조 조건이지 동일 회차의 증명이 아니다.
      if (daysAfterPayment < 1 || daysAfterPayment > 7) {
        reasons.add("KRX 발행일이 DART 납입일과 같은 발행 회차로 확인되지 않습니다");
        datesValid = false;
      }
    } else if (notice.issueDate() == null) {
      reasons.add("KIND 공고에서 발행일을 확인할 수 없습니다");
      datesValid = false;
    }
    // 단순 수량·가격·납입일 근접성은 같은 발행회차의 증명이 아니다.
    // DART 발행실적 원문과 KIND 발행내역이 독립적으로 명시한 회차+발행일이 정확히 같아야 한다.
    // 실제 공시에 이 연결 근거가 없으면 보수적으로 검토 대상으로 유지한다.
    if (notice.roundLabel() == null || notice.roundLabel().isBlank()) {
      reasons.add("KIND 발행회차 식별자가 없습니다");
    } else if (dart == null
        || dart.roundLabel() == null
        || !normalizeRound(notice.roundLabel()).equals(normalizeRound(dart.roundLabel()))) {
      reasons.add("DART 원신고·발행실적과 KIND 발행회차의 독립적인 연결 근거가 없습니다: " + notice.roundLabel());
    }
    if (ipo.getSubscriptionEndDate() != null
        && notice.listingDate() != null
        && !notice.listingDate().isAfter(ipo.getSubscriptionEndDate())) {
      reasons.add("KRX 상장일이 청약 종료일 이후가 아닙니다");
      datesValid = false;
    }

    // uniqueness
    if (ipo.getStockCode() != null) {
      Optional<Ipo> same =
          ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull(
              ipo.getStockCode(), RecordStatus.ACTIVE);
      if (same.isPresent() && !same.get().getId().equals(ipo.getId())) {
        reasons.add("동일 종목코드의 다른 IPO 행이 있어 고유 매칭할 수 없습니다");
      }
    }

    boolean expired =
        notice.listingDate() != null && !notice.listingDate().isAfter(LocalDate.now(clock));

    boolean idempotent =
        "CONFIRMED_KRX".equals(ipo.getListingDateEvidenceType())
            && ipo.getKrxListingNoticeUrl() != null
            && ipo.getKrxListingNoticeUrl().equals(noticeUrl)
            && ipo.getListingDate() != null
            && ipo.getListingDate().equals(notice.listingDate());

    boolean applyAllowed =
        reasons.isEmpty()
            && stockCodeMatches
            && stockTypeCommon
            && reasonAllowed
            && singleMatch
            && issuedSharesMatch
            && issuePriceMatch
            && datesValid;

    String reviewStatus = applyAllowed ? (idempotent ? "IDEMPOTENT" : "APPLYABLE") : "REVIEW_ONLY";

    return new KrxListingReconciliationPreview(
        ipo.getId(),
        ipo.getStockCode(),
        ipo.getListingDate(),
        ipo.getListingDateEvidenceType(),
        ipo.getListingDateEvidenceReceipt(),
        notice,
        dart == null ? null : dart.receiptNo(),
        dart == null ? null : dart.paymentDate(),
        dart == null ? null : dart.issuedShares(),
        dart == null ? null : dart.issuePrice(),
        dart == null ? null : dart.listingDate(),
        dart == null ? null : dart.originalListingDate(),
        stockCodeMatches,
        stockTypeCommon,
        reasonAllowed,
        singleMatch,
        issuedSharesMatch,
        issuePriceMatch,
        datesValid,
        expired,
        applyAllowed,
        idempotent,
        reasons,
        reviewStatus,
        dart == null ? ipo.getListingDateEvidenceReceipt() : dart.receiptNo(),
        dart == null ? ipo.getListingDate() : dart.originalListingDate(),
        ipo.getStockCode(),
        notice.issuedShares(),
        notice.issuePrice(),
        notice.listingDate(),
        noticeHash);
  }

  /** 미리보기 기대값이 담긴 단일 apply 요청을 받아 적용을 수행한다. */
  public KrxListingApplyResult apply(Long ipoId, KrxListingApplyRequest request) {
    if (request == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "apply request is required");
    }
    String noticeUrl = request.noticeUrl();
    if (noticeUrl == null || noticeUrl.isBlank()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "noticeUrl is required");
    }

    KrxListingReconciliationPreview preview = preview(ipoId, noticeUrl);

    if (!java.util.Objects.equals(
        request.expectedDartReceiptNo(), preview.expectedDartReceiptNo())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale preview: dart receipt mismatch");
    }
    if (!java.util.Objects.equals(
        request.expectedDartListingDate(), preview.expectedDartListingDate())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "stale preview: dart listing date mismatch");
    }
    if (!java.util.Objects.equals(request.expectedStockCode(), preview.expectedStockCode())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale preview: stock code mismatch");
    }
    if (!java.util.Objects.equals(request.expectedIssuedShares(), preview.expectedIssuedShares())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale preview: issued shares mismatch");
    }
    if (!java.util.Objects.equals(request.expectedIssuePrice(), preview.expectedIssuePrice())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale preview: issue price mismatch");
    }
    if (!java.util.Objects.equals(
        request.expectedKrxListingDate(), preview.expectedKrxListingDate())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale preview: krx listing date mismatch");
    }
    if (!java.util.Objects.equals(request.expectedNoticeHash(), preview.expectedNoticeHash())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "stale preview: notice hash mismatch");
    }

    if (!preview.applyAllowed()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "apply conditions are not satisfied: " + preview.reasons());
    }

    final String finalNoticeUrl = noticeUrl;
    final boolean[] idempotent = new boolean[] {preview.idempotent()};
    transactionTemplate.execute(
        status -> {
          Ipo current = ipoRepository.findById(ipoId).orElse(null);
          if (current == null
              || current.getDeletedAt() != null
              || current.getStatus() != RecordStatus.ACTIVE) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "ipo state changed");
          }
          if ("CONFIRMED_KRX".equals(current.getListingDateEvidenceType())
              && finalNoticeUrl.equals(current.getKrxListingNoticeUrl())
              && preview.expectedKrxListingDate().equals(current.getListingDate())) {
            idempotent[0] = true;
            return null;
          }
          if (!"ESTIMATED_DART".equals(current.getListingDateEvidenceType())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "ipo evidence type changed");
          }
          if (!java.util.Objects.equals(current.getStockCode(), preview.expectedStockCode())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "ipo stock code changed");
          }
          if (!java.util.Objects.equals(
              current.getListingDate(), preview.expectedDartListingDate())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "ipo dart listing date changed");
          }
          if (!java.util.Objects.equals(
              current.getListingDateEvidenceReceipt(), preview.expectedDartReceiptNo())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "ipo dart receipt changed");
          }

          int updated =
              ipoRepository.confirmKrxListingIfCurrent(
                  ipoId,
                  preview.expectedStockCode(),
                  preview.expectedDartReceiptNo(),
                  preview.expectedDartListingDate(),
                  preview.expectedKrxListingDate(),
                  finalNoticeUrl,
                  java.time.LocalDateTime.now(clock));
          if (updated == 1) {
            return null;
          }
          // 동시 적용이 먼저 커밋된 경우, 같은 후보라면 충돌이 아니라 no-op으로 보고한다.
          if (ipoRepository.countSameConfirmedKrxCandidate(
                  ipoId,
                  finalNoticeUrl,
                  preview.expectedKrxListingDate(),
                  preview.expectedStockCode(),
                  preview.expectedDartReceiptNo())
              == 1) {
            idempotent[0] = true;
            return null;
          }
          throw new org.springframework.web.server.ResponseStatusException(
              org.springframework.http.HttpStatus.CONFLICT, "concurrent ipo change");
        });

    return new KrxListingApplyResult(
        ipoId, true, idempotent[0] ? "NO_OP" : "APPLIED", preview.reasons());
  }

  private DartEvidence loadDart(Ipo ipo, List<String> reasons) {
    if (!"ESTIMATED_DART".equals(ipo.getListingDateEvidenceType())
        && !"CONFIRMED_KRX".equals(ipo.getListingDateEvidenceType())) {
      return null;
    }
    String receipt = ipo.getListingDateEvidenceReceipt();
    if (receipt == null || !receipt.matches("\\d{14}")) {
      reasons.add("DART receipt가 없습니다");
      return null;
    }
    String xml = dartIpoFilingClient.downloadDocumentStrict(receipt);
    DartRightsIssueListingParser parser = new DartRightsIssueListingParser();
    java.util.List<Evidence> evidences = parser.parse(xml);
    // 서로 다른 유효 후보가 둘 이상이면 임의로 하나를 고를 수 없다. review-only로 남긴다.
    // 파서가 같은 날짜/증거를 이미 중복 제거하므로 여기서는 남은 후보의 고유성만 확인한다.
    if (evidences.stream().distinct().count() > 1) {
      reasons.add("DART 상장예정일 후보가 여러 건입니다");
      return null;
    }
    Long shares = evidences.isEmpty() ? null : evidences.get(0).issuedShares();
    java.time.LocalDate payment = evidences.isEmpty() ? null : evidences.get(0).paymentDate();
    java.time.LocalDate listing = evidences.isEmpty() ? null : evidences.get(0).listingDate();
    if (shares == null) {
      reasons.add("DART 실제 발행주식수를 확인할 수 없습니다");
    }
    if (payment == null) {
      reasons.add("DART 납입일을 확인할 수 없습니다");
    }
    if (listing == null) {
      reasons.add("DART 예정일을 확인할 수 없습니다");
    }
    Long price = parseDartIssuePrice(xml);
    if (price == null) {
      reasons.add("DART 확정 발행가를 확인할 수 없습니다");
    }
    java.time.LocalDate original =
        ipo.getLastImportedListingDate() != null
            ? ipo.getLastImportedListingDate()
            : ipo.getListingDate();
    return new DartEvidence(
        receipt, payment, shares, price, listing, original, extractDartRound(xml));
  }

  // 발행실적 문서 전체에 등장하는 과거 회차/인용 문구는 연결 근거가 아니다.
  // 현재 발행임을 밝히는 전용 섹션의 구조화된 발행회수 행에 한해서만 인정한다.
  // 실제 DART 자료에 이 구역이 없으면 회차 연결은 검토 대상으로 남긴다.
  private String extractDartRound(String xml) {
    java.util.regex.Matcher sections =
        java.util.regex.Pattern.compile(
                "<SECTION-2\\b[^>]*>\\s*<TITLE[^>]*>금번\\s*유상증자\\s*발행내역</TITLE>(.*?)</SECTION-2>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
            .matcher(xml);
    java.util.regex.Pattern roundRow =
        java.util.regex.Pattern.compile(
            "<TR\\b[^>]*>\\s*<TH\\b[^>]*>발행회수\\s*\\(발행일\\)</TH>\\s*"
                + "<TD\\b[^>]*>(제\\s*\\d+\\s*회\\s*\\('\\d{2}\\.\\d{2}\\.\\d{2}\\))</TD>\\s*</TR>",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
    String round = null;
    while (sections.find()) {
      java.util.regex.Matcher rows = roundRow.matcher(sections.group(1));
      while (rows.find()) {
        String candidate = normalizeRound(rows.group(1));
        if (round != null && !round.equals(candidate)) {
          return null;
        }
        round = candidate;
      }
    }
    return round;
  }

  private String normalizeRound(String round) {
    return round == null ? "" : round.replaceAll("\\s+", "").trim();
  }

  private Long parseDartIssuePrice(String xml) {
    java.util.regex.Matcher tr =
        java.util.regex.Pattern.compile(
                "<TR\\b[^>]*>(.*?)</TR>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
            .matcher(xml);
    while (tr.find()) {
      String row = tr.group(1);
      if (!row.contains("ACODE=\"INC_STK_KND\"") || !row.contains("보통주")) {
        continue;
      }
      if (!row.contains("ACODE=\"INC_STK_AMT\"")) {
        continue;
      }
      java.util.regex.Matcher priceMatcher =
          java.util.regex.Pattern.compile("ACODE=\"INC_STK_AMT\"[^>]*>([0-9,]+)").matcher(row);
      if (priceMatcher.find()) {
        try {
          return Long.parseLong(priceMatcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
          // ignore
        }
      }
    }
    java.util.regex.Matcher total =
        java.util.regex.Pattern.compile("ACODE=\"INC_AMT_TOT\"[^>]*>([0-9,]+)").matcher(xml);
    if (total.find()) {
      try {
        return Long.parseLong(total.group(1).replace(",", ""));
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    java.util.regex.Matcher sum =
        java.util.regex.Pattern.compile("ACODE=\"INC_SL_AMT_SUM\"[^>]*>([0-9,]+)").matcher(xml);
    if (sum.find()) {
      try {
        return Long.parseLong(sum.group(1).replace(",", ""));
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    return null;
  }

  private boolean isPaidInCapitalReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return false;
    }
    String r = reason.replace(" ", "");
    if (r.contains("무상") || r.contains("전환") || r.contains("채권") || r.contains("행사")) {
      return false;
    }
    return r.contains("유상");
  }

  private String sha256(String value) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private KrxListingReconciliationPreview emptyPreview(Long ipoId, List<String> reasons) {
    return new KrxListingReconciliationPreview(
        ipoId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        reasons,
        "REVIEW_ONLY",
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private record DartEvidence(
      String receiptNo,
      LocalDate paymentDate,
      Long issuedShares,
      Long issuePrice,
      LocalDate listingDate,
      LocalDate originalListingDate,
      String roundLabel) {}

  /** 적용 결과. */
  public record KrxListingApplyResult(
      Long ipoId, boolean applied, String status, List<String> reasons) {}
}
