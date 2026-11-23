package com.ipocalc.ipocatalog.service.ipo.krx;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.controller.ipo.dto.KrxListingApplyRequest;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 공고 게시일 기준 KIND 자동 발견과 DART 실제 발행증거의 단일 회차 대조.
 *
 * <p>공고/실적 원문 조회는 DB 트랜잭션 밖에서 실행한다. 저장 직전에는 기존 {@link KrxListingReconciliationService#apply}의 조건부
 * 갱신으로 경합을 차단한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KrxListingAutoSyncService {

  /** 자동 처리 결과 유형. */
  public enum OutcomeType {
    NEW_CONFIRMED,
    DATE_CORRECTED,
    ALREADY_APPLIED,
    NO_NOTICE,
    ROUND_CONFLICT,
    FETCH_FAILED,
    REVIEW_REQUIRED,
    APPLY_CANDIDATE
  }

  /** 공고 식별자, 상장일 후보 및 행별 판정 이유. */
  public record Outcome(
      Long ipoId,
      String stockCode,
      OutcomeType status,
      String receiptNo,
      LocalDate proposedListingDate,
      String reason) {}

  /** 일괄 처리 후 집계와 개별 판정 내역. */
  public record Report(
      LocalDateTime completedAt,
      boolean dryRun,
      Map<OutcomeType, Long> counts,
      List<Outcome> rows) {}

  private final KrxListingNoticeDiscoveryClient discovery;
  private final KrxListingReconciliationService reconciliation;
  private final IpoRepository ipoRepository;
  private final Clock clock;
  private final ReentrantLock activeRun = new ReentrantLock();
  private final AtomicReference<Report> latest = new AtomicReference<>();

  @Value("${app.kind-listing.auto-apply-enabled:false}")
  private boolean autoApplyEnabled;

  /** DART 증권발행실적을 처리한 뒤 호출하는 최근 게시 공고 점검. */
  public Report reconcileRecent() {
    LocalDate today = today();
    List<Ipo> targets =
        candidates().stream()
            .filter(ipo -> ipo.getSubscriptionEndDate() != null)
            .filter(ipo -> !ipo.getSubscriptionEndDate().isBefore(today.minusDays(180)))
            .toList();
    return run(targets, today.minusDays(35), today, !autoApplyEnabled);
  }

  /** 청약월을 선택해도 다음 달 이후 상장 공고까지 탐색하되 다른 청약월 행은 보호한다. */
  public Report reconcileSubscriptionMonths(
      YearMonth fromMonth, YearMonth toMonth, boolean dryRun) {
    if (fromMonth == null || toMonth == null || fromMonth.isAfter(toMonth)) {
      throw new IllegalArgumentException("청약월 조회 범위가 올바르지 않습니다");
    }
    List<Ipo> targets =
        candidates().stream()
            .filter(ipo -> ipo.getSubscriptionStartDate() != null)
            .filter(ipo -> !ipo.getSubscriptionStartDate().isBefore(fromMonth.atDay(1)))
            .filter(ipo -> !ipo.getSubscriptionStartDate().isAfter(toMonth.atEndOfMonth()))
            .toList();
    return run(
        targets,
        fromMonth.atDay(1),
        min(today(), toMonth.atEndOfMonth().plusMonths(3)),
        dryRun || !autoApplyEnabled);
  }

  /** 과거 누락 후보 전체를 저장 없이 원문 조회·판정한다. */
  public Report previewPastUnconfirmed() {
    List<Ipo> targets =
        candidates().stream()
            .filter(
                ipo ->
                    ("ESTIMATED_DART".equals(ipo.getListingDateEvidenceType())
                            && ipo.getListingDate() != null
                            && ipo.getListingDate().isBefore(today()))
                        || (ipo.getSyncSource() != null
                            && ipo.getSubscriptionEndDate() != null
                            && ipo.getSubscriptionEndDate().isBefore(today())
                            && ipo.getListingDateEvidenceType() == null
                            && (ipo.getListingDate() == null
                                || (ipo.getLastImportedListingDate() != null
                                    && ipo.getListingDate()
                                        .equals(ipo.getLastImportedListingDate())))))
            .toList();
    return run(targets, null, today(), true);
  }

  /** 매일 낮에도 공식 신규 공고를 재확인한다. */
  @Scheduled(cron = "0 15 12,18 * * *", zone = "Asia/Seoul")
  public void scanDuringDay() {
    if (!autoApplyEnabled) {
      return;
    }
    try {
      log.info("KIND 낮 자동 점검: {}", reconcileRecent().counts());
    } catch (RuntimeException ex) {
      log.warn("KIND 낮 자동 점검 실패: {}", ex.getClass().getSimpleName());
    }
  }

  /** 자동 DB 반영 활성 여부. */
  public boolean isAutoApplyEnabled() {
    return autoApplyEnabled;
  }

  /** 마지막 자동 판정 결과 스냅샷. */
  public Report latestReport() {
    return latest.get();
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
  }

  private List<Ipo> candidates() {
    return ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
        .filter(ipo -> ipo.getStatus() == RecordStatus.ACTIVE)
        .filter(ipo -> ipo.getStockCode() != null && ipo.getStockCode().matches("[0-9][0-9A-Z]{5}"))
        .filter(
            ipo ->
                "ESTIMATED_DART".equals(ipo.getListingDateEvidenceType())
                    || "CONFIRMED_KRX".equals(ipo.getListingDateEvidenceType())
                    || (ipo.getSyncSource() != null
                        && ipo.getSourceReceiptNo() != null
                        && ipo.getListingDateEvidenceType() == null
                        && (ipo.getListingDate() == null
                            || (ipo.getLastImportedListingDate() != null
                                && ipo.getListingDate().equals(ipo.getLastImportedListingDate())))))
        .toList();
  }

  private Report run(List<Ipo> ipos, LocalDate commonFrom, LocalDate commonTo, boolean dryRun) {
    // 운영 플래그가 꺼져 있으면 어떤 호출 경로에서도 자동 저장을 허용하지 않는다.
    dryRun = dryRun || !autoApplyEnabled;
    if (!activeRun.tryLock()) {
      throw new IllegalStateException("KIND 자동 점검이 이미 실행 중입니다");
    }
    try {
      List<Outcome> outcomes = new ArrayList<>();
      for (Ipo ipo : ipos) {
        LocalDate from = commonFrom;
        LocalDate to = min(today(), commonTo);
        if (from == null) {
          LocalDate anchor =
              ipo.getListingDate() != null ? ipo.getListingDate() : ipo.getSubscriptionEndDate();
          if (anchor == null) {
            outcomes.add(outcome(ipo, OutcomeType.REVIEW_REQUIRED, null, null, "조회 기준일 없음"));
            continue;
          }
          from = anchor.minusMonths(2);
          to = min(today(), anchor.plusMonths(ipo.getListingDate() == null ? 4 : 2));
        }
        try {
          outcomes.add(reconcileOne(ipo, from, to, dryRun));
        } catch (RuntimeException ex) {
          log.warn(
              "KIND 종목별 점검 실패: code={}, kind={}",
              ipo.getStockCode(),
              ex.getClass().getSimpleName());
          outcomes.add(
              outcome(
                  ipo,
                  OutcomeType.FETCH_FAILED,
                  null,
                  null,
                  ex.getClass().getSimpleName() + ": " + safeReason(ex.getMessage())));
        }
      }
      Map<OutcomeType, Long> counts = new LinkedHashMap<>();
      for (OutcomeType type : OutcomeType.values()) {
        counts.put(type, outcomes.stream().filter(o -> o.status() == type).count());
      }
      Report result =
          new Report(LocalDateTime.now(clock), dryRun, Map.copyOf(counts), List.copyOf(outcomes));
      latest.set(result);
      return result;
    } finally {
      activeRun.unlock();
    }
  }

  private Outcome reconcileOne(Ipo ipo, LocalDate from, LocalDate to, boolean dryRun) {
    if (from.isAfter(to)) {
      return outcome(ipo, OutcomeType.NO_NOTICE, null, null, "조회할 공고 게시일 기간 없음");
    }
    Map<String, KrxListingNoticeDiscoveryClient.Disclosure> notices = new LinkedHashMap<>();
    for (LocalDate cursor = from; !cursor.isAfter(to); ) {
      LocalDate end = min(to, cursor.plusDays(27));
      var found = discovery.find(ipo.getStockCode(), cursor, end);
      for (var notice : found.disclosures()) {
        notices.putIfAbsent(notice.receiptNo(), notice);
      }
      cursor = end.plusDays(1);
    }
    if (notices.isEmpty()) {
      return outcome(ipo, OutcomeType.NO_NOTICE, null, null, "공고 게시일 범위의 모든 검색 페이지 정상 조회 후 공고 없음");
    }
    boolean newListing =
        ipo.getListingDate() == null
            || (ipo.getSyncSource() != null
                && ipo.getListingDateEvidenceType() == null
                && ipo.getLastImportedListingDate() != null
                && ipo.getListingDate().equals(ipo.getLastImportedListingDate()))
            || ("CONFIRMED_KRX".equals(ipo.getListingDateEvidenceType())
                && ipo.getKrxListingNoticeUrl() != null
                && ipo.getKrxListingNoticeUrl().endsWith("/70760.htm"));
    if (newListing) {
      return reconcileNewListing(ipo, notices, dryRun);
    }
    List<Match> eligible = new ArrayList<>();
    List<String> reviews = new ArrayList<>();
    boolean unresolvedRevision = false;
    for (var notice : notices.values()) {
      // 서로 다른 유형의 공고를 추가상장 원문 해석기로 전달하지 않는다.
      // 신규상장·정정·취소는 별도의 원문 검증 및 원공고 연결이 필요하다.
      var type = KrxListingNoticeDiscoveryClient.classifyListingTitle(notice.title());
      if (type == KrxListingNoticeDiscoveryClient.ListingType.OTHER) {
        continue;
      }
      if (type != KrxListingNoticeDiscoveryClient.ListingType.ADDITIONAL) {
        reviews.add(notice.receiptNo() + ": 별도 공고 유형 검증 필요: " + notice.title());
        if (type == KrxListingNoticeDiscoveryClient.ListingType.REVISION) {
          unresolvedRevision = true;
        }
        continue;
      }
      // 1개 공고라도 원문 조회 실패하면 해당 행 전체가 실패하며 일부만 확정하지 않는다.
      String url = discovery.resolveAdditionalListingUrl(notice.receiptNo());
      var preview = reconciliation.preview(ipo.getId(), url);
      if (preview.applyAllowed()) {
        eligible.add(new Match(notice.receiptNo(), url, preview));
      } else {
        reviews.add(notice.receiptNo() + ": " + String.join("; ", preview.reasons()));
      }
    }
    if (unresolvedRevision || !reviews.isEmpty()) {
      return outcome(ipo, OutcomeType.REVIEW_REQUIRED, null, null, String.join(" | ", reviews));
    }
    if (eligible.size() > 1) {
      return outcome(
          ipo,
          OutcomeType.ROUND_CONFLICT,
          null,
          null,
          "복수 공고가 발행 회차 후보: " + eligible.stream().map(Match::receiptNo).toList());
    }
    // 다른 추가상장 공고의 발행회차가 확인되지 않으면 후보 하나만 확정하지 않는다.
    // 매칭이 실패한 다른 공고를 무시하면 같은 발행에 대한 중복 공고를 놓칠 수 있다.
    if (!reviews.isEmpty()) {
      return outcome(ipo, OutcomeType.REVIEW_REQUIRED, null, null, String.join(" | ", reviews));
    }
    if (eligible.isEmpty()) {
      return outcome(ipo, OutcomeType.REVIEW_REQUIRED, null, null, "검증 가능한 추가상장 후보 없음");
    }
    Match selected = eligible.get(0);
    var preview = selected.preview();
    if (preview.idempotent()) {
      return outcome(
          ipo,
          OutcomeType.ALREADY_APPLIED,
          selected.receiptNo(),
          preview.expectedKrxListingDate(),
          "동일 공고·날짜가 이미 확정됨");
    }
    if (dryRun) {
      return outcome(
          ipo,
          OutcomeType.APPLY_CANDIDATE,
          selected.receiptNo(),
          preview.expectedKrxListingDate(),
          "공식 근거 대조 성공(읽기 전용)");
    }
    var request =
        new KrxListingApplyRequest(
            selected.url(),
            preview.expectedDartReceiptNo(),
            preview.expectedDartListingDate(),
            preview.expectedStockCode(),
            preview.expectedIssuedShares(),
            preview.expectedIssuePrice(),
            preview.expectedKrxListingDate(),
            preview.expectedNoticeHash());
    var result = reconciliation.apply(ipo.getId(), request);
    if (Objects.equals(result.status(), "NO_OP")) {
      return outcome(
          ipo,
          OutcomeType.ALREADY_APPLIED,
          selected.receiptNo(),
          preview.expectedKrxListingDate(),
          "동시 적용이 먼저 커밋되어 갱신 0건");
    }
    OutcomeType kind =
        ipo.getListingDate() != null
                && !ipo.getListingDate().equals(preview.expectedKrxListingDate())
            ? OutcomeType.DATE_CORRECTED
            : OutcomeType.NEW_CONFIRMED;
    return outcome(
        ipo,
        kind,
        selected.receiptNo(),
        preview.expectedKrxListingDate(),
        "DART 실제 발행과 KIND 동일 회차 확인 후 반영");
  }

  /** 일반 IPO의 신규상장 70760은 추가상장과 별도 판정·저장한다. */
  private Outcome reconcileNewListing(
      Ipo ipo, Map<String, KrxListingNoticeDiscoveryClient.Disclosure> notices, boolean dryRun) {
    List<NewMatch> matches = new ArrayList<>();
    List<String> reviews = new ArrayList<>();
    boolean unresolvedRevision = false;
    for (var notice : notices.values()) {
      var type = KrxListingNoticeDiscoveryClient.classifyListingTitle(notice.title());
      if (type == KrxListingNoticeDiscoveryClient.ListingType.REVISION) {
        unresolvedRevision = true;
        reviews.add(notice.receiptNo() + ": 원공고 미확인 정정·취소·철회·변경: " + notice.title());
        continue;
      }
      if (type != KrxListingNoticeDiscoveryClient.ListingType.NEW_LISTING) {
        continue;
      }
      String url = discovery.resolveNewListingUrl(notice.receiptNo());
      var preview = reconciliation.previewNewListing(ipo.getId(), url);
      if (preview.applyAllowed()) {
        matches.add(new NewMatch(notice.receiptNo(), url, preview));
      } else {
        reviews.add(notice.receiptNo() + ": " + String.join("; ", preview.reasons()));
      }
    }
    // 신규상장 원문 중 하나라도 검증 실패하면 다른 후보로 부분 확정하지 않는다.
    if (unresolvedRevision || !reviews.isEmpty()) {
      return outcome(ipo, OutcomeType.REVIEW_REQUIRED, null, null, String.join(" | ", reviews));
    }
    if (matches.size() > 1) {
      return outcome(ipo, OutcomeType.ROUND_CONFLICT, null, null, "신규상장 공고 후보가 복수입니다");
    }
    if (matches.isEmpty()) {
      return outcome(ipo, OutcomeType.REVIEW_REQUIRED, null, null, String.join(" | ", reviews));
    }
    var chosen = matches.get(0);
    var preview = chosen.preview();
    if (preview.idempotent()) {
      return outcome(
          ipo, OutcomeType.ALREADY_APPLIED, chosen.receipt(), preview.listingDate(), "이미 확정됨");
    }
    if (dryRun) {
      return outcome(
          ipo, OutcomeType.APPLY_CANDIDATE, chosen.receipt(), preview.listingDate(), "읽기 전용");
    }
    var result =
        reconciliation.applyNewListing(
            ipo.getId(),
            chosen.url(),
            preview.listingDate(),
            preview.noticeHash(),
            ipo.getSourceReceiptNo());
    return outcome(
        ipo,
        "NO_OP".equals(result.status()) ? OutcomeType.ALREADY_APPLIED : OutcomeType.NEW_CONFIRMED,
        chosen.receipt(),
        preview.listingDate(),
        "공식 신규상장 보통주 종목코드와 매매개시일 확정");
  }

  private record NewMatch(
      String receipt, String url, KrxListingReconciliationService.NewListingPreview preview) {}

  private static Outcome outcome(
      Ipo ipo, OutcomeType kind, String receipt, LocalDate date, String reason) {
    return new Outcome(ipo.getId(), ipo.getStockCode(), kind, receipt, date, reason);
  }

  private static LocalDate min(LocalDate one, LocalDate two) {
    return one.isBefore(two) ? one : two;
  }

  private static String safeReason(String reason) {
    if (reason == null) {
      return "원인 미상";
    }
    String sanitized =
        reason
            .replaceAll("https?://\\S+", "(URL 비공개)")
            .replaceAll("(?i)(api[_-]?key|service[_-]?key)\\s*[=:]\\s*\\S+", "$1=(비공개)");
    return sanitized.substring(0, Math.min(160, sanitized.length()));
  }

  private record Match(String receiptNo, String url, KrxListingReconciliationPreview preview) {}
}
