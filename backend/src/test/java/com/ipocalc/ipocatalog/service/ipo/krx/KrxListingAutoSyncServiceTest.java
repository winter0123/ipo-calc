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
    Clock clock = Clock.fixed(
        Instant.parse("2026-11-22T09:00:00Z"), ZoneId.of("Asia/Seoul"));
    service = new KrxListingAutoSyncService(discovery, reconciliation, repository, clock);
    when(repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(ipo()));
  }

  @Test
  void pastPreviewFindsSameOfficialReceiptInMultipleWindowsButNeverWrites() {
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(reconciliation.preview(7L, URL)).thenReturn(eligiblePreview(false));

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
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(reconciliation.preview(7L, URL)).thenReturn(eligiblePreview(false));
    var applied = mock(KrxListingReconciliationService.KrxListingApplyResult.class);
    when(applied.status()).thenReturn("APPLIED");
    when(reconciliation.apply(eq(7L), any(KrxListingApplyRequest.class)))
        .thenReturn(applied);

    var report = service.reconcileRecent();

    assertThat(report.dryRun()).isFalse();
    assertThat(report.counts().get(OutcomeType.DATE_CORRECTED)).isEqualTo(1);
    assertThat(report.rows().get(0).proposedListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 25));
    verify(reconciliation).apply(eq(7L), any(KrxListingApplyRequest.class));
  }

  @Test
  void ambiguousOfficialNoticesNeverWriteEvenWhenBothPreviewsAreEligible() {
    ReflectionTestUtils.setField(service, "autoApplyEnabled", true);
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT), disclosure(OTHER_RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(discovery.resolveAdditionalListingUrl(OTHER_RECEIPT)).thenReturn(OTHER_URL);
    when(reconciliation.preview(7L, URL)).thenReturn(eligiblePreview(false));
    when(reconciliation.preview(7L, OTHER_URL)).thenReturn(eligiblePreview(false));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.ROUND_CONFLICT)).isEqualTo(1);
    assertThat(report.rows().get(0).reason()).contains(RECEIPT, OTHER_RECEIPT);
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
    when(discovery.find(eq("121850"), any(), any()))
        .thenReturn(result(disclosure(RECEIPT)));
    when(discovery.resolveAdditionalListingUrl(RECEIPT)).thenReturn(URL);
    when(reconciliation.preview(7L, URL)).thenReturn(eligiblePreview(true));

    var report = service.reconcileRecent();

    assertThat(report.counts().get(OutcomeType.ALREADY_APPLIED)).isEqualTo(1);
    verify(reconciliation, never()).apply(any(), any(KrxListingApplyRequest.class));
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
