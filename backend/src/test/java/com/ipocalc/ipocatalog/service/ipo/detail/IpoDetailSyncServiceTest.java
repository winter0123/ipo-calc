package com.ipocalc.ipocatalog.service.ipo.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.domain.model.ipo.IpoDetail;
import com.ipocalc.ipocatalog.repository.ipo.IpoDetailRepository;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.detail.dart.DartFilingParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.EquityOffering;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.FilingRef;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.Underwriter;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartCompanyOverview;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link IpoDetailSyncService} 단위 테스트. 외부 호출은 목, 원문은 파서 테스트 픽스처를 쓴다. */
class IpoDetailSyncServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
  private static final String RCEPT = "20261007000328";

  private final IpoRepository ipoRepository = mock(IpoRepository.class);
  private final IpoDetailRepository detailRepository = mock(IpoDetailRepository.class);
  private final IpoDetailService detailService = mock(IpoDetailService.class);
  private final DartIpoFilingClient filingClient = mock(DartIpoFilingClient.class);
  private final OpenDartCorpDisclosureClient disclosureClient =
      mock(OpenDartCorpDisclosureClient.class);
  private final IpoDetailSyncService service =
      new IpoDetailSyncService(
          ipoRepository,
          detailRepository,
          detailService,
          filingClient,
          disclosureClient,
          Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), ZoneId.of("Asia/Seoul")));

  private static Ipo ipo(Long id, String corpCode, LocalDate listing) {
    return Ipo.builder().id(id).corpCode(corpCode).listingDate(listing).build();
  }

  private static String fixture() throws IOException {
    try (InputStream in =
        IpoDetailSyncServiceTest.class.getResourceAsStream("/dart/ms-prospectus.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void combinesFilingCompanyAndOfferingIntoSnapshot() throws IOException {
    Ipo ms = ipo(1L, "01749559", LocalDate.of(2026, 10, 22));
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(ms));
    when(detailRepository.findById(1L)).thenReturn(Optional.empty());
    when(filingClient.findLatestOfferingDocument("01749559", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "[기재정정]투자설명서", TODAY)));
    when(filingClient.downloadDocument(RCEPT)).thenReturn(Optional.of(fixture()));
    when(disclosureClient.getCompanyOverview("01749559"))
        .thenReturn(Optional.of(new OpenDartCompanyOverview("000", "김성포", "20151027", "경기도 성남시")));
    when(filingClient.fetchEquityOffering("01749559", TODAY))
        .thenReturn(
            Optional.of(
                new EquityOffering(100L, List.of(new Underwriter("케이비증권", "대표", 4_000_000L)))));

    assertThat(service.syncRecent()).isEqualTo(1);

    ArgumentCaptor<IpoDetailSnapshot> captor = ArgumentCaptor.forClass(IpoDetailSnapshot.class);
    verify(detailService).save(eq(1L), captor.capture());
    IpoDetailSnapshot s = captor.getValue();
    assertThat(s.sourceRceptNo()).isEqualTo(RCEPT);
    assertThat(s.ceoName()).isEqualTo("김성포");
    assertThat(s.establishedOn()).isEqualTo(LocalDate.of(2015, 10, 27));
    assertThat(s.parValue()).isEqualTo(100L);
    assertThat(s.underwriters())
        .extracting(IpoDetailSnapshot.Underwriter::shares)
        .containsExactly(4_000_000L);
    assertThat(s.financials()).hasSize(4);
    assertThat(s.floatSchedule()).hasSize(5);
    assertThat(s.institutionalCompetitionRate()).isEqualByComparingTo("1096.56");
    assertThat(s.largestShareholderName()).isEqualTo("김성포");
  }

  @Test
  void skipsDownloadWhenSameFilingAlreadySaved() {
    Ipo ms = ipo(1L, "01749559", null);
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(ms));
    when(filingClient.findLatestOfferingDocument("01749559", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "투자설명서", TODAY)));
    when(detailRepository.findById(1L))
        .thenReturn(
            Optional.of(
                IpoDetail.builder()
                    .ipoId(1L)
                    .sourceRceptNo(RCEPT)
                    .parserVersion(DartFilingParser.VERSION)
                    .build()));

    assertThat(service.syncRecent()).isZero();

    verify(filingClient, never()).downloadDocument(anyString());
    verify(detailService, never()).save(any(), any());
  }

  @Test
  void reparsesSameFilingWhenSavedByOlderParser() {
    Ipo ms = ipo(1L, "01749559", null);
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(ms));
    when(filingClient.findLatestOfferingDocument("01749559", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "투자설명서", TODAY)));
    when(detailRepository.findById(1L))
        .thenReturn(
            Optional.of(
                IpoDetail.builder()
                    .ipoId(1L)
                    .sourceRceptNo(RCEPT)
                    .parserVersion(DartFilingParser.VERSION - 1)
                    .build()));

    service.syncRecent();

    verify(filingClient).downloadDocument(RCEPT);
  }

  @Test
  void targetsOnlyIposWithCorpCodeNotLongListed() {
    assertThat(IpoDetailSyncService.isTarget(ipo(1L, null, null), TODAY)).isFalse();
    assertThat(IpoDetailSyncService.isTarget(ipo(1L, "01749559", null), TODAY)).isTrue();
    assertThat(IpoDetailSyncService.isTarget(ipo(1L, "01749559", TODAY.minusDays(30)), TODAY))
        .isTrue();
    assertThat(IpoDetailSyncService.isTarget(ipo(1L, "01749559", TODAY.minusDays(31)), TODAY))
        .isFalse();
  }

  @Test
  void oneFailureDoesNotStopOthers() throws IOException {
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(ipo(1L, "00000001", null), ipo(2L, "00000002", null)));
    when(filingClient.findLatestOfferingDocument("00000001", TODAY))
        .thenThrow(new IllegalStateException("boom"));
    when(filingClient.findLatestOfferingDocument("00000002", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "투자설명서", TODAY)));
    when(detailRepository.findById(2L)).thenReturn(Optional.empty());
    when(filingClient.downloadDocument(RCEPT)).thenReturn(Optional.of(fixture()));
    when(disclosureClient.getCompanyOverview("00000002")).thenReturn(Optional.empty());
    when(filingClient.fetchEquityOffering("00000002", TODAY)).thenReturn(Optional.empty());

    assertThat(service.syncRecent()).isEqualTo(1);
    verify(detailService).save(eq(2L), any());
  }

  @Test
  void skipsNonTargetsAndIposWithoutFiling() {
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(
            List.of(
                ipo(1L, null, null),
                ipo(2L, "00000002", TODAY.minusDays(31)),
                ipo(3L, "00000003", null)));
    when(filingClient.findLatestOfferingDocument("00000003", TODAY)).thenReturn(Optional.empty());

    assertThat(service.syncRecent()).isZero();

    verify(filingClient, never()).findLatestOfferingDocument(eq("00000002"), any());
    verify(filingClient, never()).downloadDocument(anyString());
    verify(detailService, never()).save(any(), any());
  }

  @Test
  void savesWhenStoredDetailHasDifferentFiling() throws IOException {
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(ipo(1L, "00000001", null)));
    when(filingClient.findLatestOfferingDocument("00000001", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "투자설명서", TODAY)));
    when(detailRepository.findById(1L))
        .thenReturn(
            Optional.of(IpoDetail.builder().ipoId(1L).sourceRceptNo("20260101000001").build()));
    when(filingClient.downloadDocument(RCEPT)).thenReturn(Optional.of(fixture()));
    when(disclosureClient.getCompanyOverview("00000001")).thenReturn(Optional.empty());
    when(filingClient.fetchEquityOffering("00000001", TODAY)).thenReturn(Optional.empty());

    assertThat(service.syncRecent()).isEqualTo(1);

    verify(filingClient).downloadDocument(RCEPT);
  }

  @Test
  void doesNotSaveWhenDocumentDownloadFails() {
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(ipo(1L, "00000001", null)));
    when(filingClient.findLatestOfferingDocument("00000001", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "투자설명서", TODAY)));
    when(detailRepository.findById(1L)).thenReturn(Optional.empty());
    when(filingClient.downloadDocument(RCEPT)).thenReturn(Optional.empty());

    assertThat(service.syncRecent()).isZero();

    verify(detailService, never()).save(any(), any());
  }

  @Test
  void keepsStoredCompanyAndOfferingWhenLookupsFail() throws IOException {
    when(ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc())
        .thenReturn(List.of(ipo(1L, "00000001", null)));
    when(filingClient.findLatestOfferingDocument("00000001", TODAY))
        .thenReturn(Optional.of(new FilingRef(RCEPT, "투자설명서", TODAY)));
    IpoDetail stored =
        IpoDetail.builder()
            .ipoId(1L)
            .sourceRceptNo("20260101000001")
            .ceoName("김대표")
            .establishedOn(LocalDate.of(2015, 10, 27))
            .address("서울")
            .parValue(500L)
            .underwriters(List.of(new IpoDetailSnapshot.Underwriter("케이비증권", "대표", 1L)))
            .build();
    when(detailRepository.findById(1L)).thenReturn(Optional.of(stored));
    when(filingClient.downloadDocument(RCEPT)).thenReturn(Optional.of(fixture()));
    when(disclosureClient.getCompanyOverview("00000001")).thenReturn(Optional.empty());
    when(filingClient.fetchEquityOffering("00000001", TODAY)).thenReturn(Optional.empty());

    assertThat(service.syncRecent()).isEqualTo(1);

    ArgumentCaptor<IpoDetailSnapshot> captor = ArgumentCaptor.forClass(IpoDetailSnapshot.class);
    verify(detailService).save(eq(1L), captor.capture());
    IpoDetailSnapshot s = captor.getValue();
    assertThat(s.sourceRceptNo()).isEqualTo(RCEPT);
    assertThat(s.ceoName()).isEqualTo("김대표");
    assertThat(s.establishedOn()).isEqualTo(LocalDate.of(2015, 10, 27));
    assertThat(s.address()).isEqualTo("서울");
    assertThat(s.parValue()).isEqualTo(500L);
    assertThat(s.underwriters()).hasSize(1);
  }

  @Test
  void blankOrInvalidCompanyFieldsBecomeNull() {
    FilingRef filing = new FilingRef(RCEPT, "투자설명서", TODAY);
    com.ipocalc.ipocatalog.service.ipo.detail.dart.ParsedFiling parsed =
        new com.ipocalc.ipocatalog.service.ipo.detail.dart.DartFilingParser().parse(null);

    IpoDetailSnapshot blank =
        IpoDetailSyncService.toSnapshot(
            filing, parsed, new OpenDartCompanyOverview("000", "  ", " ", ""), null);
    IpoDetailSnapshot badDate =
        IpoDetailSyncService.toSnapshot(
            filing, parsed, new OpenDartCompanyOverview("000", " 홍길동 ", "2015-10", " 서울 "), null);
    IpoDetailSnapshot noCompany = IpoDetailSyncService.toSnapshot(filing, parsed, null, null);

    assertThat(blank.ceoName()).isNull();
    assertThat(blank.establishedOn()).isNull();
    assertThat(blank.address()).isNull();
    assertThat(badDate.ceoName()).isEqualTo("홍길동");
    assertThat(badDate.establishedOn()).isNull();
    assertThat(badDate.address()).isEqualTo("서울");
    assertThat(noCompany.ceoName()).isNull();
    assertThat(noCompany.parValue()).isNull();
    assertThat(noCompany.underwriters()).isEmpty();
  }
}
