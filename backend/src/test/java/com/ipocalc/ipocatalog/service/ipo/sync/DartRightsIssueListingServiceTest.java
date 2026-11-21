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
    when(tx.execute(any()))
        .thenAnswer(
            inv -> {
              TransactionCallback<?> callback = inv.getArgument(0);
              return callback.doInTransaction(null);
            });
    service =
        new DartRightsIssueListingService(
            repo,
            filings,
            tx,
            Clock.fixed(
                LocalDate.of(2026, 6, 1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")));
  }

  private Ipo ipo(String originalReceipt) {
    return Ipo.builder()
        .id(125L)
        .companyName("라온피플")
        .ipoName("라온피플")
        .corpCode("00123456")
        .stockCode("300120")
        .sourceReceiptNo(originalReceipt)
        .syncSource("OPENDART")
        .subscriptionStartDate(LocalDate.of(2026, 2, 16))
        .subscriptionEndDate(LocalDate.of(2026, 2, 18))
        .fixedPrice(12345L)
        .status(RecordStatus.ACTIVE)
        .build();
  }

  private String xml(String original, String listing) {
    return "<DOCUMENT><P>원신고서 "
        + original
        + "</P><TABLE>"
        + "<TR><TD>납입일</TD><TD>2026.02.19</TD></TR>"
        + "<TR><TD>신주 상장 및 유통개시 예정일</TD><TD>"
        + listing
        + "</TD></TR>"
        + "<TR><TD>무상증자 신주 상장 예정일</TD><TD>2026.03.24</TD></TR>"
        + "</TABLE></DOCUMENT>";
  }

  private void setupReports(Ipo row, FilingRef... reports) {
    when(repo.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    when(repo.findByIdAndDeletedAtIsNull(125L)).thenReturn(Optional.of(row));
    when(filings.findIssuanceResults(any(), any(), any())).thenReturn(List.of(reports));
  }

  @Test
  void storesRightsDateOnSameIpoIdWithoutChangingPriceAndRerunIsIdempotent() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));

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
    assertThat(service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).saved())
        .isZero();
    assertThat(row.getListingDate()).isNull();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void latestCorrectionWinsForSameOriginalReceipt() {
    Ipo row = ipo(ORIGINAL);
    String corrected = "20260306000001";
    setupReports(
        row,
        new FilingRef(corrected, "[기재정정]증권발행실적보고서", LocalDate.of(2026, 3, 6)),
        new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(corrected)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.11")));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));

    assertThat(service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).saved())
        .isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 11));
    assertThat(row.getListingDateEvidenceReceipt()).isEqualTo(corrected);
    verify(filings, never()).downloadDocument(REPORT);
  }

  @Test
  void avoidsAutomaticMatchWhenSourceReceiptIsUnavailable() {
    Ipo row = ipo(null);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));
    assertThat(service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).saved())
        .isZero();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void rechecksExistingDartEstimateAndAppliesNewerCorrection() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));
    var first = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    assertThat(first.saved()).isEqualTo(1);

    String correction = "20260306000001";
    setupReports(
        row,
        new FilingRef(correction, "[기재정정]증권발행실적보고서", LocalDate.of(2026, 3, 6)),
        new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(correction)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.11")));
    var retry = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(retry.saved()).isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 11));
    assertThat(row.getListingDateEvidenceReceipt()).isEqualTo(correction);
    assertThat(row.getFixedPrice()).isEqualTo(12345L);
    assertThat(service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).saved())
        .isZero();
  }

  @Test
  void matchesIssueUsingNewSharesAndPaymentInsteadOfStoredOutstandingShareCount() {
    Ipo row = ipo(ORIGINAL);
    // 기존 DB에 38,390,259주처럼 총 발행주식수가 기록돼 있더라도 매칭 근거로 쓰지 않는다.
    row =
        Ipo.builder()
            .id(125L)
            .corpCode("00123456")
            .stockCode("300120")
            .companyName("형지엘리트")
            .ipoName("형지엘리트")
            .sourceReceiptNo(ORIGINAL)
            .syncSource("OPENDART")
            .offeringShareCount(38_390_259L)
            .subscriptionStartDate(LocalDate.of(2026, 2, 2))
            .subscriptionEndDate(LocalDate.of(2026, 2, 4))
            .status(RecordStatus.ACTIVE)
            .build();
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 2, 10)));
    String original =
        "<DOCUMENT><P>기발행 주식수 38,390,259주</P>"
            + "<P>금번 유상증자 신주 23,000,000주</P>"
            + "<P>납입일 2026.02.06</P></DOCUMENT>";
    String result =
        "<DOCUMENT><P>금번 유상증자 신주 23,000,000주</P>"
            + "<P>납입일 2026.02.06</P>"
            + "<P>주권 상장 및 유통 예정일 2026.02.13</P></DOCUMENT>";
    when(filings.downloadDocument(ORIGINAL)).thenReturn(Optional.of(original));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(result));

    var backfill = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(backfill.saved()).isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 2, 13));
    assertThat(row.getOfferingShareCount()).isEqualTo(38_390_259L);
    verify(repo).saveAndFlush(row);
  }

  @Test
  void rejectsPaymentOrNewShareMismatchEvenWhenCompanyMatches() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    String original = "<DOCUMENT><P>유상증자 신주 14,500,000주</P>" + "<P>납입일 2026.02.19</P></DOCUMENT>";
    String unrelatedIssue =
        "<DOCUMENT><P>유상증자 신주 20,860,012주</P>"
            + "<P>납입일 2026.02.19</P>"
            + "<P>주권 상장 및 유통 예정일 2026.03.10</P></DOCUMENT>";
    when(filings.downloadDocument(ORIGINAL)).thenReturn(Optional.of(original));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(unrelatedIssue));

    var result = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result.saved()).isZero();
    assertThat(row.getListingDate()).isNull();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void hyungjiUsesPaymentDueTableAndIgnoresOld2019Date() {
    Ipo row =
        Ipo.builder()
            .id(125L)
            .corpCode("00123456")
            .companyName("형지엘리트")
            .ipoName("형지엘리트")
            .sourceReceiptNo(ORIGINAL)
            .syncSource("OPENDART")
            .offeringShareCount(38_390_259L)
            .subscriptionStartDate(LocalDate.of(2026, 1, 26))
            .subscriptionEndDate(LocalDate.of(2026, 1, 28))
            .status(RecordStatus.ACTIVE)
            .build();
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 2, 10)));
    String paymentTable =
        "<P>과거 납입일 2019년 9월 16일</P>"
            + "<P>청약 및 납입일정</P><TABLE>"
            + "<TR><TH>구분</TH><TH>청약일</TH><TH>납입기일</TH></TR>"
            + "<TR><TD>유상증자</TD><TD>2026년 1월 28일</TD>"
            + "<TD>2026년 1월 30일</TD></TR></TABLE>";
    String original = "<DOCUMENT>" + paymentTable + "<P>금번 유상증자 신주 23,000,000주</P></DOCUMENT>";
    String result =
        "<DOCUMENT>"
            + paymentTable
            + "<P>금번 유상증자 신주 23,000,000주</P>"
            + "<P>주권 상장 및 유통 예정일 2026년 2월 13일</P></DOCUMENT>";
    when(filings.downloadDocument(ORIGINAL)).thenReturn(Optional.of(original));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(result));

    var backfill = service.backfill(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertThat(backfill.saved()).isEqualTo(1);
    assertThat(backfill.reviewRequired()).isZero();
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 2, 13));
    assertThat(row.getOfferingShareCount()).isEqualTo(38_390_259L);
    verify(repo).saveAndFlush(row);
  }

  @Test
  void unresolvedFilingRoundIsReportedForManualReviewRatherThanSilentZero() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(ORIGINAL))
        .thenReturn(Optional.of("<DOCUMENT><P>2019년 9월 16일 납입일</P></DOCUMENT>"));
    when(filings.downloadDocument(REPORT))
        .thenReturn(
            Optional.of(
                "<DOCUMENT><P>주권 상장 및 유통 예정일 2026.03.10</P>" + "<P>납입일 2026.02.19</P></DOCUMENT>"));

    var result = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result.saved()).isZero();
    assertThat(result.reviewRequired()).isEqualTo(1);
    assertThat(result.failures()).isZero();
    assertThat(row.getListingDate()).isNull();
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void correctionNeverOverwritesManuallyProtectedListingDate() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(xml(ORIGINAL, "2026.03.10")));
    service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    row.protectListingDate();

    var retry = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(retry.saved()).isZero();
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 10));
    assertThat(row.getListingDateEvidenceType()).isNull();
  }

  @Test
  void contradictoryDatesInOneReportNeedReview() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    when(filings.downloadDocument(REPORT))
        .thenReturn(
            Optional.of(
                xml(ORIGINAL, "2026.03.10")
                    .replace(
                        "</TABLE>",
                        "<TR><TD>주권 상장 및 유통 예정일</TD>" + "<TD>2026.03.11</TD></TR></TABLE>")));
    var result = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    assertThat(result.saved()).isZero();
    assertThat(result.reviewRequired()).isEqualTo(1);
    verify(repo, never()).saveAndFlush(any());
  }
}
