package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.controller.ipo.dto.KrxListingApplyRequest;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.krx.KrxListingAutoSyncService.OutcomeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class KrxListingAutoSyncServiceTest {
  private static final String RECEIPT = "20261120001019";
  private static final String OTHER_RECEIPT = "20261120001020";
  private static final String URL =
      "https://kind.krx.co.kr/external/2026/11/20/001019/20261120002019/70791.htm";
  private static final String OTHER_URL =
      "https://kind.krx.co.kr/external/2026/11/20/001020/20261120002020/70791.htm";

  private KrxListingNoticeDiscoveryClient discovery;
  private KrxListingReconciliationService reconciliation;
  private IpoRepository repository;
  private KrxListingAutoSyncService service;

  @BeforeEach
  void setUp() {
    discovery = mock(KrxListingNoticeDiscoveryClient.class);
    reconciliation = mock(KrxListingReconciliationService.class);
    repository = mock(IpoRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-22T09:00:00Z"), ZoneId.of("Asia/Seoul"));
    service = new KrxListingAutoSyncService(discovery, reconciliation, repository, clock);
    Ipo initial = ipo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(initial));
  }

  @Test
  void monthlyWriteRequestRemainsReadOnlyWhenAutoApplyIsDisabled() {
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var preview = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(preview);

    var report =
        service.reconcileSubscriptionMonths(
            java.time.YearMonth.of(2026, 11), java.time.YearMonth.of(2026, 11), false);

    assertThat(report.dryRun()).isTrue();
    assertThat(report.counts().get(OutcomeType.APPLY_CANDIDATE)).isEqualTo(1);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void multipleUnmatchedNoticesRequireReviewNotRoundConflict() {
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), disclosure(OTHER_RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(discovery.resolveAdditionalListingUrl(OTHER_RECEIPT)).thenReturn(OTHER_URL);
    var first = mock(KrxListingReconciliationPreview.class);
    var second = mock(KrxListingReconciliationPreview.class);
    when(first.reasons()).thenReturn(List.of("발행주식수 불일치"));
    when(second.reasons()).thenReturn(List.of("발행가 불일치"));
    when(reconciliation.preview(7L, URL)).thenReturn(first);
    when(reconciliation.preview(7L, OTHER_URL)).thenReturn(second);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.ROUND_CONFLICT)).isZero();
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void pastPreviewFindsSameOfficialReceiptInMultipleWindowsButNeverWrites() {
    Ipo prior = ipo();
    when(prior.getListingDate()).thenReturn(LocalDate.of(2026, 10, 20));
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(prior));
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var preview = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(preview);

    var report = service.previewPastUnconfirmed();

    assertThat(report.dryRun()).isTrue();
    assertThat(report.counts().get(OutcomeType.APPLY_CANDIDATE)).isEqualTo(1);
    assertThat(report.rows()).hasSize(1);
    assertThat(report.rows().get(0).receiptNo()).isEqualTo(RECEIPT);
    verify(discovery).resolveAdditionalListingUrl(RECEIPT);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void scheduledLookupAppliesExactlyOneMatchingRoundAndCountsDateCorrection() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var preview = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(preview);
    var applied = mock(KrxListingReconciliationService.KrxListingApplyResult.class);
    when(applied.status()).thenReturn("APPLIED");
    when(reconciliation.apply(eq(7L), any(KrxListingApplyRequest.class))).thenReturn(applied);

    var report = service.reconcileRecent();

    assertThat(report.dryRun()).isFalse();
    assertThat(report.counts().get(OutcomeType.DATE_CORRECTED)).isEqualTo(1);
    assertThat(report.rows().get(0).proposedListingDate()).isEqualTo(LocalDate.of(2026, 11, 25));
    verify(reconciliation).apply(eq(7L), any(KrxListingApplyRequest.class));
  }

  @Test
  void ambiguousOfficialNoticesNeverWriteEvenWhenBothPreviewsAreEligible() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), disclosure(OTHER_RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(discovery.resolveAdditionalListingUrl(OTHER_RECEIPT)).thenReturn(OTHER_URL);
    var preview = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(preview);
    var otherPreview = eligiblePreview(false);
    when(reconciliation.preview(7L, OTHER_URL)).thenReturn(otherPreview);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.ROUND_CONFLICT)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(RECEIPT, OTHER_RECEIPT);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void oneEligibleNoticeAndOneFailedDocumentNeverWrite() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), disclosure(OTHER_RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(discovery.resolveAdditionalListingUrl(OTHER_RECEIPT)).thenReturn(OTHER_URL);
    var eligible = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(eligible);
    when(reconciliation.preview(7L, OTHER_URL))
        .thenThrow(new IllegalStateException("KIND 공고 원문 조회·파싱 실패"));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.FETCH_FAILED)).isEqualTo(1);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void dartHttpFailureRemainsFetchFailedAndWritesZero() {
    org.springframework.test.util.ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(reconciliation.preview(7L, URL))
        .thenThrow(
            new com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException(
                "FETCH_FAILED", "DART document HTTP failure"));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.FETCH_FAILED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isZero();
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void invalidDartZipRemainsFetchFailedAndWritesZero() {
    org.springframework.test.util.ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(reconciliation.preview(7L, URL))
        .thenThrow(
            new com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException(
                "INVALID_ZIP", "DART document is not a valid ZIP"));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.FETCH_FAILED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isZero();
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void failedPageInLaterWindowDoesNotBecomeNoNoticeOrPartialConfirmation() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT)))
        .thenThrow(new IllegalStateException("KIND 조회 2페이지 실패"));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.FETCH_FAILED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.NO_NOTICE)).isZero();
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void sameConfirmedNoticeReturnsAlreadyAppliedWithoutMutation() {
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var preview = eligiblePreview(true);
    when(reconciliation.preview(7L, URL)).thenReturn(preview);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.ALREADY_APPLIED)).isEqualTo(1);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void unresolvedCorrectionBlocksOtherwiseEligibleOriginalNotice() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var correction =
        new KrxListingNoticeDiscoveryClient.Disclosure(
            OTHER_RECEIPT, LocalDate.of(2026, 11, 20), "코이즈", "추가상장 정정", "코스닥", "12185");
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), correction));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var eligible = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(eligible);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(OTHER_RECEIPT, "정정");
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
    verify(discovery, never()).resolveAdditionalListingUrl(OTHER_RECEIPT);
  }

  @Test
  void unresolvedCancellationBlocksAlreadyConfirmedNoticeFromBeingReapplied() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    Ipo confirmed = ipo();
    when(confirmed.getListingDateEvidenceType()).thenReturn("CONFIRMED_KRX");
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(confirmed));
    var cancellation =
        new KrxListingNoticeDiscoveryClient.Disclosure(
            OTHER_RECEIPT, LocalDate.of(2026, 11, 20), "코이즈", "추가상장 취소", "코스닥", "12185");
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), cancellation));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var alreadyConfirmed = eligiblePreview(true);
    when(reconciliation.preview(7L, URL)).thenReturn(alreadyConfirmed);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.ALREADY_APPLIED)).isZero();
    assertThat(report.rows().get(0).reason()).contains(OTHER_RECEIPT, "취소");
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
    verify(discovery, never()).resolveAdditionalListingUrl(OTHER_RECEIPT);
  }

  @Test
  void withdrawalNoticeBlocksOtherwiseEligibleOriginalNotice() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var withdrawn =
        new KrxListingNoticeDiscoveryClient.Disclosure(
            OTHER_RECEIPT, LocalDate.of(2026, 11, 20), "코이즈", "추가상장 철회", "코스닥", "12185");
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), withdrawn));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var eligible = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(eligible);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(OTHER_RECEIPT, "철회");
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
    verify(discovery, never()).resolveAdditionalListingUrl(OTHER_RECEIPT);
  }

  @Test
  void changedListingNoticeBlocksOtherwiseEligibleOriginalNotice() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var changed =
        new KrxListingNoticeDiscoveryClient.Disclosure(
            OTHER_RECEIPT, LocalDate.of(2026, 11, 20), "코이즈", "추가상장 변경", "코스닥", "12185");
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), changed));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    var eligible = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(eligible);

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(OTHER_RECEIPT, "변경");
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
    verify(discovery, never()).resolveAdditionalListingUrl(OTHER_RECEIPT);
  }

  @Test
  void newListingNoticeIsNotMisparsedAsAdditionalListing() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var newListing =
        new KrxListingNoticeDiscoveryClient.Disclosure(
            OTHER_RECEIPT, LocalDate.of(2026, 11, 20), "신규회사", "신규상장", "코스닥", "12185");
    when(discovery.find(eq("121850"), any(), any())).thenReturn(result(newListing));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains("신규상장");
    verify(discovery, never()).resolveAdditionalListingUrl(OTHER_RECEIPT);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void syntheticSearchHtmlWithdrawalBlocksOriginalWrite() {
    var report = reconcileSyntheticSearchRows("상장 철회", true);

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(OTHER_RECEIPT, "철회");
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void syntheticSearchHtmlChangeBlocksOriginalWrite() {
    var report = reconcileSyntheticSearchRows("[정정] 상장 변경", true);

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(OTHER_RECEIPT, "변경");
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void syntheticSearchHtmlUnrelatedDisclosureDoesNotBlockOriginalWrite() {
    var report = reconcileSyntheticSearchRows("감사보고서", false);

    assertThat(report.counts().get(OutcomeType.DATE_CORRECTED)).isEqualTo(1);
    assertThat(report.rows().get(0).receiptNo()).isEqualTo(RECEIPT);
    verify(reconciliation).apply(eq(7L), any(KrxListingApplyRequest.class));
  }

  // 합성 KIND 검색 HTML → 실제 목록 파서 → 자동 배치. 외부 공시 실응답이 아니다.
  private KrxListingAutoSyncService.Report reconcileSyntheticSearchRows(
      String secondaryTitle, boolean revision) {
    String html =
        "<html><table class='list type-00 mt10'><tbody>"
            + syntheticSearchRow(RECEIPT, "추가상장")
            + syntheticSearchRow(OTHER_RECEIPT, secondaryTitle)
            + "</tbody></table></html>";
    var discovered =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("/disclosure/details.do".equals(path)) {
                assertThat(params).containsEntry("repIsuSrtCd", "A121850");
                return html;
              }
              assertThat(path).isEqualTo("/common/disclsviewer.do");
              if ("search".equals(params.get("method"))) {
                assertThat(params).containsEntry("acptno", RECEIPT);
                return "<option value='20261120002019|Y'>추가상장</option>";
              }
              assertThat(params).containsEntry("docNo", "20261120002019");
              return URL;
            });
    var discoveryResult =
        discovered.find(
            "121850", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));
    assertThat(discoveryResult.disclosures()).hasSize(revision ? 2 : 1);
    service =
        new KrxListingAutoSyncService(
            discovered,
            reconciliation,
            repository,
            Clock.fixed(Instant.parse("2026-11-22T09:00:00Z"), ZoneId.of("Asia/Seoul")));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var preview = eligiblePreview(false);
    when(reconciliation.preview(7L, URL)).thenReturn(preview);
    var applied = mock(KrxListingReconciliationService.KrxListingApplyResult.class);
    when(applied.status()).thenReturn("APPLIED");
    when(reconciliation.apply(eq(7L), any(KrxListingApplyRequest.class))).thenReturn(applied);

    return service.reconcileRecent();
  }

  private static String syntheticSearchRow(String receipt, String title) {
    return "<tr><td>1</td><td>09:00</td>"
        + "<td><a onclick=\\\"companysummary_open('12185')\\\">코이즈</a></td>"
        + "<td><a onclick=\\\"openDisclsViewer('"
        + receipt
        + "','')\\\">"
        + title
        + "</a></td><td>거래소</td></tr>";
  }

  private static final String NEW_URL =
      "https://kind.krx.co.kr/external/2026/11/20/001019/20261120002019/70760.htm";
  private static final String OTHER_NEW_URL =
      "https://kind.krx.co.kr/external/2026/11/20/001020/20261120002020/70760.htm";

  @Test
  void syntheticNewListingFlowsFromOfficialSearchToAutoApplyWithoutInputUrl() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    String page =
        "<html><table class='list type-00 mt10'><tbody>"
            + syntheticSearchRow(RECEIPT, "신규상장")
            + "</tbody></table></html>";
    var officialDiscovery =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("/disclosure/details.do".equals(path)) {
                assertThat(params).containsEntry("repIsuSrtCd", "A121850");
                return page;
              }
              if ("search".equals(params.get("method"))) {
                assertThat(params).containsEntry("acptno", RECEIPT);
                return "<option value='20261120002019|Y'>신규상장</option>";
              }
              assertThat(params).containsEntry("docNo", "20261120002019");
              return NEW_URL;
            });
    service =
        new KrxListingAutoSyncService(
            officialDiscovery,
            reconciliation,
            repository,
            Clock.fixed(Instant.parse("2026-11-22T09:00:00Z"), ZoneId.of("Asia/Seoul")));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var preview = newListingPreview(false);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(preview);
    when(reconciliation.applyNewListing(
            eq(7L), eq(NEW_URL), eq(preview.listingDate()), eq("hash"), eq("20261101000001")))
        .thenReturn(
            new KrxListingReconciliationService.KrxListingApplyResult(
                7L, true, "APPLIED", List.of()));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.NEW_CONFIRMED)).isEqualTo(1);
    assertThat(report.rows().get(0).receiptNo()).isEqualTo(RECEIPT);
    verify(reconciliation).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void historicalPreviewIncludesAutoIpoWithoutListingDateButNeverWrites() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(false));

    var report = service.previewPastUnconfirmed();

    assertThat(report.dryRun()).isTrue();
    assertThat(report.rows()).hasSize(1);
    assertThat(report.counts().get(OutcomeType.APPLY_CANDIDATE)).isEqualTo(1);
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void importedListingDateIsStillEligibleForOfficialNewListing() {
    Ipo row = newListingIpo();
    when(row.getListingDate()).thenReturn(LocalDate.of(2026, 11, 14));
    when(row.getLastImportedListingDate()).thenReturn(LocalDate.of(2026, 11, 14));
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(false));

    var report = service.reconcileSubscriptionMonths(
        java.time.YearMonth.of(2026, 11), java.time.YearMonth.of(2026, 11), true);

    assertThat(report.counts().get(OutcomeType.APPLY_CANDIDATE)).isEqualTo(1);
    assertThat(report.dryRun()).isTrue();
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void monthlyNewListingReconciliationDoesNotReadOtherSubscriptionMonths() {
    Ipo selected = newListingIpo();
    Ipo other = mock(Ipo.class);
    when(other.getId()).thenReturn(8L);
    when(other.getStatus()).thenReturn(RecordStatus.ACTIVE);
    when(other.getStockCode()).thenReturn("300120");
    when(other.getSyncSource()).thenReturn("OPENDART");
    when(other.getSourceReceiptNo()).thenReturn("20261001000001");
    when(other.getSubscriptionStartDate()).thenReturn(LocalDate.of(2026, 10, 3));
    when(other.getSubscriptionEndDate()).thenReturn(LocalDate.of(2026, 10, 6));
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(selected, other));
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(false));

    var report = service.reconcileSubscriptionMonths(
        java.time.YearMonth.of(2026, 11), java.time.YearMonth.of(2026, 11), true);

    assertThat(report.rows()).hasSize(1);
    assertThat(report.rows().get(0).ipoId()).isEqualTo(7L);
    verify(discovery, never()).find(eq("300120"), any(), any());
  }

  @Test
  void newListingIsReadOnlyWhenFlagDisabled() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    var preview = newListingPreview(false);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(preview);

    var report = service.reconcileRecent();

    assertThat(report.dryRun()).isTrue();
    assertThat(report.counts().get(OutcomeType.APPLY_CANDIDATE)).isEqualTo(1);
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void oneNewListingWithExplicitFlagSavesOnce() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    var preview = newListingPreview(false);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(preview);
    when(reconciliation.applyNewListing(
            eq(7L), eq(NEW_URL), eq(preview.listingDate()), eq("hash"), eq("20261101000001")))
        .thenReturn(
            new KrxListingReconciliationService.KrxListingApplyResult(
                7L, true, "APPLIED", List.of()));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.NEW_CONFIRMED)).isEqualTo(1);
    verify(reconciliation)
        .applyNewListing(
            eq(7L), eq(NEW_URL), eq(preview.listingDate()), eq("hash"), eq("20261101000001"));
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
  }

  @Test
  void newListingCorrectionBlocksWriteWithoutOriginalReference() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    var correction =
        new KrxListingNoticeDiscoveryClient.Disclosure(
            OTHER_RECEIPT,
            LocalDate.of(2026, 11, 20),
            "코이즈",
            "상장 변경",
            "코스닥",
            "12185");
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT), correction));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(false));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains("변경");
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void conflictingNewListingCandidatesCannotApply() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT), newListingDisclosure(OTHER_RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(discovery.resolveNewListingUrl(OTHER_RECEIPT)).thenReturn(OTHER_NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(false));
    when(reconciliation.previewNewListing(7L, OTHER_NEW_URL))
        .thenReturn(newListingPreview(false));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.ROUND_CONFLICT)).isEqualTo(1);
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void oneUnverifiableNewListingBlocksOtherwiseEligibleCandidate() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT), newListingDisclosure(OTHER_RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(discovery.resolveNewListingUrl(OTHER_RECEIPT)).thenReturn(OTHER_NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(false));
    when(reconciliation.previewNewListing(7L, OTHER_NEW_URL))
        .thenReturn(
            new KrxListingReconciliationService.NewListingPreview(
                null, "", false, false, List.of("다른 종목코드")));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.REVIEW_REQUIRED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.NEW_CONFIRMED)).isZero();
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void alreadyConfirmedSameNewListingIsNoOp() {
    Ipo row = newListingIpo();
    when(row.getListingDate()).thenReturn(LocalDate.of(2026, 11, 25));
    when(row.getListingDateEvidenceType()).thenReturn("CONFIRMED_KRX");
    when(row.getKrxListingNoticeUrl()).thenReturn(NEW_URL);
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT)).thenReturn(NEW_URL);
    when(reconciliation.previewNewListing(7L, NEW_URL)).thenReturn(newListingPreview(true));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.ALREADY_APPLIED)).isEqualTo(1);
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  @Test
  void newListingHttpFailureCannotBeReportedAsNoNotice() {
    Ipo row = newListingIpo();
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(row));
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(newListingDisclosure(RECEIPT)));
    when(discovery.resolveNewListingUrl(RECEIPT))
        .thenThrow(new IllegalStateException("KIND official viewer timed out"));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.FETCH_FAILED)).isEqualTo(1);
    assertThat(report.counts().get(OutcomeType.NO_NOTICE)).isZero();
    verify(reconciliation, never()).applyNewListing(any(), any(), any(), any(), any());
  }

  private static Ipo newListingIpo() {
    Ipo row = ipo();
    when(row.getListingDate()).thenReturn(null);
    when(row.getListingDateEvidenceType()).thenReturn(null);
    when(row.getSyncSource()).thenReturn("OPENDART");
    when(row.getSourceReceiptNo()).thenReturn("20261101000001");
    return row;
  }

  private static KrxListingNoticeDiscoveryClient.Disclosure newListingDisclosure(String receipt) {
    return new KrxListingNoticeDiscoveryClient.Disclosure(
        receipt, LocalDate.of(2026, 11, 20), "코이즈", "신규상장", "코스닥", "12185");
  }

  private static KrxListingReconciliationService.NewListingPreview newListingPreview(
      boolean idempotent) {
    return new KrxListingReconciliationService.NewListingPreview(
        LocalDate.of(2026, 11, 25), "hash", true, idempotent, List.of());
  }

  private static Ipo ipo() {
    Ipo ipo = mock(Ipo.class);
    when(ipo.getId()).thenReturn(7L);
    when(ipo.getStockCode()).thenReturn("121850");
    when(ipo.getStatus()).thenReturn(RecordStatus.ACTIVE);
    when(ipo.getListingDate()).thenReturn(LocalDate.of(2026, 11, 24));
    when(ipo.getListingDateEvidenceType()).thenReturn("ESTIMATED_DART");
    when(ipo.getSubscriptionStartDate()).thenReturn(LocalDate.of(2026, 11, 10));
    when(ipo.getSubscriptionEndDate()).thenReturn(LocalDate.of(2026, 11, 18));
    return ipo;
  }

  private static KrxListingNoticeDiscoveryClient.SearchResult result(
      KrxListingNoticeDiscoveryClient.Disclosure... notices) {
    return new KrxListingNoticeDiscoveryClient.SearchResult(List.of(notices), 1);
  }

  private static KrxListingNoticeDiscoveryClient.Disclosure disclosure(String receipt) {
    return new KrxListingNoticeDiscoveryClient.Disclosure(
        receipt, LocalDate.of(2026, 11, 20), "코이즈", "추가상장", "코스닥", "12185");
  }

  private static KrxListingReconciliationPreview eligiblePreview(boolean idempotent) {
    var preview = mock(KrxListingReconciliationPreview.class);
    when(preview.applyAllowed()).thenReturn(true);
    when(preview.idempotent()).thenReturn(idempotent);
    when(preview.expectedKrxListingDate()).thenReturn(LocalDate.of(2026, 11, 25));
    when(preview.expectedDartReceiptNo()).thenReturn("20261119000001");
    when(preview.expectedDartListingDate()).thenReturn(LocalDate.of(2026, 11, 24));
    when(preview.expectedStockCode()).thenReturn("121850");
    when(preview.expectedIssuedShares()).thenReturn(1_995_582L);
    when(preview.expectedIssuePrice()).thenReturn(2_865L);
    when(preview.expectedNoticeHash()).thenReturn("abcdef");
    return preview;
  }
}
