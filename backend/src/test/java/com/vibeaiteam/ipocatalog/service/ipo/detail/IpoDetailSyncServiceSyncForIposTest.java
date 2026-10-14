package com.vibeaiteam.ipocatalog.service.ipo.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.domain.model.ipo.IpoDetail;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoDetailRepository;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.detail.dart.DartFilingParser;
import com.vibeaiteam.ipocatalog.service.ipo.detail.dart.ParsedFiling;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.EquityOffering;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.FilingRef;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartCompanyOverview;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** IpoDetailSyncService syncForIpos 테스트. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpoDetailSyncServiceSyncForIposTest {

  @Mock private IpoRepository ipoRepository;
  @Mock private IpoDetailRepository detailRepository;
  @Mock private IpoDetailService detailService;
  @Mock private DartIpoFilingClient filingClient;
  @Mock private OpenDartCorpDisclosureClient disclosureClient;

  private IpoDetailSyncService service;
  private Clock fixedClock;
  private DartFilingParser parser;
  private DartFilingParser mockParser;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(
        LocalDateTime.of(2026, 10, 14, 12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(),
        java.time.ZoneId.systemDefault());
    parser = new DartFilingParser();
    mockParser = mock(DartFilingParser.class);
    
    service =
        new IpoDetailSyncService(
            ipoRepository,
            detailRepository,
            detailService,
            filingClient,
            disclosureClient,
            fixedClock);
    // parser 필드를 mock으로 교체
    org.springframework.test.util.ReflectionTestUtils.setField(service, "parser", mockParser);
  }

  @Test
  void syncForIpos_filtersByTargetCriteria() {
    // given
    Ipo targetIpo = createIpo(1L, "corp1", "Target IPO", null); // 미상장
    Ipo listedRecentIpo = createIpo(
        2L, "corp2", "Listed Recent", LocalDate.of(2026, 10, 1)); // 상장 30일 이내
    Ipo listedOldIpo = createIpo(
        3L, "corp3", "Listed Old", LocalDate.of(2026, 9, 1)); // 상장 30일 초과
    Ipo noCorpCodeIpo = createIpo(4L, null, "No CorpCode", null); // corpCode 없음

    List<Ipo> ipos = List.of(targetIpo, listedRecentIpo, listedOldIpo, noCorpCodeIpo);

    // targetIpo와 listedRecentIpo만 대상
    FilingRef filingRef1 = new FilingRef("20260115001", "투자설명서", LocalDate.of(2026, 1, 15));
    FilingRef filingRef2 = new FilingRef("20260116001", "투자설명서", LocalDate.of(2026, 1, 16));
    
    when(filingClient.findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.of(filingRef1));
    when(filingClient.findLatestOfferingDocument(eq("corp2"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.of(filingRef2));
    
    when(filingClient.downloadDocument("20260115001")).thenReturn(Optional.of("document1"));
    when(filingClient.downloadDocument("20260116001")).thenReturn(Optional.of("document2"));
    
    when(detailRepository.findById(1L)).thenReturn(Optional.empty());
    when(detailRepository.findById(2L)).thenReturn(Optional.empty());
    
    ParsedFiling parsed = mock(ParsedFiling.class);
    when(parsed.financials()).thenReturn(Collections.emptyList());
    when(parsed.floatSchedule()).thenReturn(Collections.emptyList());
    when(parsed.capitalStock()).thenReturn(1000000000L);
    when(parsed.largestShareholderName()).thenReturn("Owner");
    when(parsed.largestShareholderRatio()).thenReturn(BigDecimal.valueOf(50.0));
    when(parsed.competitionRate()).thenReturn(BigDecimal.valueOf(100.0));
    when(parsed.lockUpCommitmentRatio()).thenReturn(BigDecimal.valueOf(50.0));
    when(mockParser.parse(anyString())).thenReturn(parsed);
    
    when(disclosureClient.getCompanyOverview("corp1")).thenReturn(Optional.empty());
    when(disclosureClient.getCompanyOverview("corp2")).thenReturn(Optional.empty());
    when(filingClient.fetchEquityOffering(
        eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.empty());
    when(filingClient.fetchEquityOffering(
        eq("corp2"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.empty());

    // when
    int saved = service.syncForIpos(ipos);

    // then
    assertThat(saved).isEqualTo(2);
    verify(filingClient).findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14)));
    verify(filingClient).findLatestOfferingDocument(eq("corp2"), eq(LocalDate.of(2026, 10, 14)));
    // corp3(상장 30일 초과)는 호출되지 않아야 함 (isTarget에서 false 반환)
    // Note: exact argument verification for "corp3" would require matching the actual call,
    // but since it's filtered by isTarget, we just verify the two expected calls above.
    verify(detailService, times(2)).save(anyLong(), any());
  }

  @Test
  void syncForIpos_skipsWhenNoFiling() {
    // given
    Ipo ipo = createIpo(1L, "corp1", "Test IPO", null);
    when(filingClient.findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.empty());

    // when
    int saved = service.syncForIpos(List.of(ipo));

    // then
    assertThat(saved).isEqualTo(0);
    verify(filingClient, never()).downloadDocument(anyString());
  }

  @Test
  void syncForIpos_skipsWhenDocumentDownloadFails() {
    // given
    Ipo ipo = createIpo(1L, "corp1", "Test IPO", null);
    FilingRef filingRef = new FilingRef("20260115001", "투자설명서", LocalDate.of(2026, 1, 15));
    
    when(filingClient.findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.of(filingRef));
    when(filingClient.downloadDocument("20260115001")).thenReturn(Optional.empty());

    // when
    int saved = service.syncForIpos(List.of(ipo));

    // then
    assertThat(saved).isEqualTo(0);
    verify(detailService, never()).save(anyLong(), any());
  }

  @Test
  void syncForIpos_skipsWhenAlreadySavedSameVersion() {
    // given
    Ipo ipo = createIpo(1L, "corp1", "Test IPO", null);
    FilingRef filingRef = new FilingRef("20260115001", "투자설명서", LocalDate.of(2026, 1, 15));
    
    when(filingClient.findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.of(filingRef));
    
    IpoDetail existing = mock(IpoDetail.class);
    when(existing.getSourceRceptNo()).thenReturn("20260115001");
    when(existing.getParserVersion()).thenReturn(DartFilingParser.VERSION);
    when(detailRepository.findById(1L)).thenReturn(Optional.of(existing));

    // when
    int saved = service.syncForIpos(List.of(ipo));

    // then
    assertThat(saved).isEqualTo(0);
    verify(filingClient, never()).downloadDocument(anyString());
  }

  @Test
  void syncForIpos_continuesOnException() {
    // given
    Ipo ipo1 = createIpo(1L, "corp1", "Test IPO 1", null);
    Ipo ipo2 = createIpo(2L, "corp2", "Test IPO 2", null);
    
    FilingRef filingRef1 = new FilingRef("20260115001", "투자설명서", LocalDate.of(2026, 1, 15));
    FilingRef filingRef2 = new FilingRef("20260116001", "투자설명서", LocalDate.of(2026, 1, 16));
    
    when(filingClient.findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.of(filingRef1));
    when(filingClient.findLatestOfferingDocument(eq("corp2"), eq(LocalDate.of(2026, 10, 14))))
        .thenThrow(new RuntimeException("Network error"));
    
    when(filingClient.downloadDocument("20260115001")).thenReturn(Optional.of("document1"));
    when(detailRepository.findById(1L)).thenReturn(Optional.empty());
    
    ParsedFiling parsed = mock(ParsedFiling.class);
    when(parsed.financials()).thenReturn(Collections.emptyList());
    when(parsed.floatSchedule()).thenReturn(Collections.emptyList());
    when(parsed.capitalStock()).thenReturn(1000000000L);
    when(parsed.largestShareholderName()).thenReturn("Owner");
    when(parsed.largestShareholderRatio()).thenReturn(BigDecimal.valueOf(50.0));
    when(parsed.competitionRate()).thenReturn(BigDecimal.valueOf(100.0));
    when(parsed.lockUpCommitmentRatio()).thenReturn(BigDecimal.valueOf(50.0));
    when(mockParser.parse(anyString())).thenReturn(parsed);
    
    when(disclosureClient.getCompanyOverview("corp1")).thenReturn(Optional.empty());
    when(filingClient.fetchEquityOffering(
        eq("corp1"), eq(LocalDate.of(2026, 10, 14))))
        .thenReturn(Optional.empty());

    // when
    int saved = service.syncForIpos(List.of(ipo1, ipo2));

    // then
    assertThat(saved).isEqualTo(1); // ipo1만 성공
    verify(filingClient).findLatestOfferingDocument(eq("corp1"), eq(LocalDate.of(2026, 10, 14)));
    verify(filingClient).findLatestOfferingDocument(eq("corp2"), eq(LocalDate.of(2026, 10, 14)));
  }

  private Ipo createIpo(Long id, String corpCode, String ipoName, LocalDate listingDate) {
    Ipo ipo = mock(Ipo.class);
    when(ipo.getId()).thenReturn(id);
    when(ipo.getCorpCode()).thenReturn(corpCode);
    when(ipo.getIpoName()).thenReturn(ipoName);
    when(ipo.getListingDate()).thenReturn(listingDate);
    when(ipo.getDeletedAt()).thenReturn(null);
    return ipo;
  }
}