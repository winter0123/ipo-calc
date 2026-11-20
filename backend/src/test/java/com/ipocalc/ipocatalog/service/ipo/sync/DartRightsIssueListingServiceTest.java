package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.FilingRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 같은 회사의 다른 발행회차나 무상 신주 날짜를 붙이지 않는 회귀 테스트. */
class DartRightsIssueListingServiceTest {
  private static final String ORIGINAL = "20260201000001";
  private static final String REPORT = "20260305000001";
  private final IpoRepository repo = mock(IpoRepository.class);
  private final DartIpoFilingClient filings = mock(DartIpoFilingClient.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private DartRightsIssueListingService service;

  @BeforeEach
  void setUp() {
    when(tx.execute(any())).thenAnswer(inv -> {
      TransactionCallback<?> callback = inv.getArgument(0);
      return callback.doInTransaction(null);
    });
    service = new DartRightsIssueListingService(repo, filings, tx,
        Clock.fixed(LocalDate.of(2026, 6, 1).atStartOfDay(ZoneId.of("Asia/Seoul"))
            .toInstant(), ZoneId.of("Asia/Seoul")));
  }

  private Ipo ipo(String originalReceipt) {
    return Ipo.builder()
        .id(125L).companyName("라온피플").ipoName("라온피플")
        .corpCode("00123456").stockCode("300120")
        .sourceReceiptNo(originalReceipt).syncSource("OPENDART")
        .subscriptionStartDate(LocalDate.of(2026, 2, 16))
        .subscriptionEndDate(LocalDate.of(2026, 2, 18))
        .fixedPrice(12345L).status(RecordStatus.ACTIVE).build();
  }

  private String xml(String original, String listing) {
    return "<DOCUMENT><P>원신고서 " + original + "</P><TABLE>"
        + "<TR><TD>납입일</TD><TD>2026.02.19</TD></TR>"
        + "<TR><TD>신주 상장 및 유통개시 예정일</TD><TD>" + listing + "</TD></TR>"
        + "<TR><TD>무상증자 신주 상장 예정일</TD><TD>2026.03.24</TD></TR>"
        + "</TABLE></DOCUMENT>";
  }

  private void setupReports(Ipo row, FilingRef... reports) {
    when(repo.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    when(repo.findByIdAndDeletedAtIsNull(125L)).thenReturn(Optional.of(row));
    when(filings.findIssuanceResults(any(), any(), any()))
        .thenReturn(List.of(reports));
  }

  @Test
  void storesRightsDateOnSameIpoIdWithoutChangingPriceAndRerunIsIdempotent() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));

    var first = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    var rerun = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(first.saved()).isEqualTo(1);
    assertThat(rerun.saved()).isZero();
    assertThat(row.getId()).isEqualTo(125L);
    assertThat(row.getFixedPrice()).isEqualTo(12345L);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 10));
    assertThat(row.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(row.getListingDateEvidenceReceipt()).isEqualTo(REPORT);
    verify(repo).saveAndFlush(row);
  }

  @Test
  void rejectsAnotherIssuanceRoundOfSameCompany() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(xml("20260101000099", "2026.03.10")));
    assertThat(service.backfill(LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 2, 28)).saved()).isZero();
    assertThat(row.getListingDate()).isNull();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void latestCorrectionWinsForSameOriginalReceipt() {
    Ipo row = ipo(ORIGINAL);
    String corrected = "20260306000001";
    setupReports(row,
        new FilingRef(corrected, "[기재정정]증권발행실적보고서",
            LocalDate.of(2026, 3, 6)),
        new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(corrected))
        .thenReturn(Optional.of(xml(ORIGINAL, "2026.03.11")));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));

    assertThat(service.backfill(LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 2, 28)).saved()).isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 11));
    assertThat(row.getListingDateEvidenceReceipt()).isEqualTo(corrected);
    verify(filings, never()).downloadDocument(REPORT);
  }

  @Test
  void avoidsAutomaticMatchWhenSourceReceiptIsUnavailable() {
    Ipo row = ipo(null);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));
    assertThat(service.backfill(LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 2, 28)).saved()).isZero();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void contradictoryDatesInOneReportNeedReview() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10").replace(
            "</TABLE>", "<TR><TD>주권 상장 및 유통 예정일</TD>"
                + "<TD>2026.03.11</TD></TR></TABLE>")));
    var result = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    assertThat(result.saved()).isZero();
    assertThat(result.reviewRequired()).isEqualTo(1);
    verify(repo, never()).saveAndFlush(any());
  }
}
