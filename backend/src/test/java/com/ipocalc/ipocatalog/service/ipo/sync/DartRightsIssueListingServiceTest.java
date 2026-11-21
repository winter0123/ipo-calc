package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.ArrayList;
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
            + "<P>금번 유상증자 신주 1주</P>"
            + "<TABLE><TR><TU ACODE=\"STK_CNT\">23,000,000</TU></TR></TABLE>"
            + "<P>납입일 2026.02.06</P></DOCUMENT>";
    String result =
        "<DOCUMENT><P>금번 유상증자 신주 1주</P>"
            + "<TABLE><TR><TU AUNIT=\"INC_CNT_TOT\">23,000,000</TU></TR></TABLE>"
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
    String original =
        "<DOCUMENT><P>유상증자 신주 1주</P>"
            + "<TABLE><TR><TU ACODE=\"STK_CNT\">14,500,000</TU></TR></TABLE>"
            + "<P>납입일 2026.02.19</P></DOCUMENT>";
    String unrelatedIssue =
        "<DOCUMENT><P>유상증자 신주 1주</P>"
            + "<TABLE><TR><TU AUNIT=\"INC_SL_CNT_SUM\">20,860,012</TU></TR></TABLE>"
            + "<P>납입일 2026.02.19</P>"
            + "<P>주권 상장 및 유통 예정일 2026.03.10</P></DOCUMENT>";
    when(filings.downloadDocument(ORIGINAL)).thenReturn(Optional.of(original));
    when(filings.downloadDocument(REPORT)).thenReturn(Optional.of(unrelatedIssue));

    var result = service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

    assertThat(result.saved()).isZero();
    assertThat(result.reviewRequired()).isEqualTo(1);
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
    String original =
        "<DOCUMENT>"
            + paymentTable
            + "<P>금번 유상증자 신주 1주</P>"
            + "<TABLE><TR><TU ACODE=\"STK_CNT\">23,000,000</TU></TR></TABLE>"
            + "</DOCUMENT>";
    String result =
        "<DOCUMENT>"
            + paymentTable
            + "<P>금번 유상증자 신주 1주</P>"
            + "<TABLE><TR><TU AUNIT=\"INC_CNT_TOT\">23,000,000</TU></TR></TABLE>"
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

  // 실제 공시에서 확인한 값과 필드 구조를 재현한 축약 XML. 원본 ZIP 자체는 아니다.
  private record HistoricalIssue(String name, LocalDate payment, long shares, LocalDate listing) {}

  private static String quantityTable(String code, long shares) {
    return "<TABLE><TR><TD>보통주</TD><TU ACODE=\"" + code + "\">" + shares + "</TU></TR></TABLE>";
  }

  private static String scheduleTable(LocalDate payment) {
    return "<P>청약 및 납입일정</P><TABLE>"
        + "<TR><TH>구분</TH><TH>청약일</TH><TH>납입기일</TH></TR>"
        + "<TR><TD>유상증자</TD><TD>"
        + payment.minusDays(2)
        + "</TD>"
        + "<TD>"
        + payment
        + "</TD></TR></TABLE>";
  }

  private static String originalDocument(LocalDate payment, long shares) {
    return "<DOCUMENT><P>기발행 주식수 38,390,259주</P>"
        + "<P>금번 유상증자 신주 1주</P>"
        + quantityTable("STK_CNT", shares)
        + scheduleTable(payment)
        + "</DOCUMENT>";
  }

  private static String resultDocument(
      LocalDate payment, long shares, long secondTotal, LocalDate listing) {
    return "<DOCUMENT>"
        + scheduleTable(payment)
        + quantityTable("INC_CNT_TOT", shares)
        + quantityTable("INC_SL_CNT_SUM", secondTotal)
        + "<P>등기예정일 2026년 2월 12일, 신주 상장 및 유통개시(예정)일 "
        + listing
        + "</P>"
        + "<P>무상증자 신주 상장 예정일 2026년 3월 24일</P></DOCUMENT>";
  }

  @Test
  void restoresSixHistoricalRowsUsingStructuredDisclosureFieldsAndRerunIsIdempotent() {
    List<HistoricalIssue> cases =
        List.of(
            new HistoricalIssue(
                "형지엘리트", LocalDate.of(2026, 1, 30), 23_000_000L, LocalDate.of(2026, 2, 13)),
            new HistoricalIssue(
                "아이씨에이치", LocalDate.of(2026, 2, 10), 8_000_000L, LocalDate.of(2026, 2, 25)),
            new HistoricalIssue(
                "아미코젠", LocalDate.of(2026, 2, 11), 14_925_374L, LocalDate.of(2026, 2, 26)),
            new HistoricalIssue(
                "라온피플", LocalDate.of(2026, 2, 25), 14_500_000L, LocalDate.of(2026, 3, 10)),
            new HistoricalIssue(
                "비보존제약", LocalDate.of(2026, 3, 4), 10_615_000L, LocalDate.of(2026, 3, 19)),
            new HistoricalIssue(
                "대한광통신", LocalDate.of(2026, 3, 6), 23_500_000L, LocalDate.of(2026, 3, 19)));
    List<Ipo> rows = new ArrayList<>();
    List<String> receipts = new ArrayList<>();
    for (int i = 0; i < cases.size(); i++) {
      HistoricalIssue c = cases.get(i);
      long id = 100L + i;
      String corp = "0012345" + i;
      String original = "2026010100000" + i;
      String report = "2026030700000" + i;
      LocalDate end = c.payment().minusDays(2);
      Ipo row =
          Ipo.builder()
              .id(id)
              .corpCode(corp)
              .companyName(c.name())
              .ipoName(c.name())
              .sourceReceiptNo(original)
              .syncSource("OPENDART")
              .subscriptionStartDate(end.minusDays(5))
              .subscriptionEndDate(end)
              .refundDate(c.payment().plusDays(1))
              .fixedPrice(12345L)
              .status(RecordStatus.ACTIVE)
              .build();
      rows.add(row);
      receipts.add(report);
      when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(row));
      when(filings.findIssuanceResults(
              corp,
              end.minusDays(5),
              end.plusDays(120).isAfter(LocalDate.of(2026, 6, 1))
                  ? LocalDate.of(2026, 6, 1)
                  : end.plusDays(120)))
          .thenReturn(List.of(new FilingRef(report, "증권발행실적보고서", c.payment())));
      when(filings.downloadDocument(original))
          .thenReturn(Optional.of(originalDocument(c.payment(), c.shares())));
      when(filings.downloadDocument(report))
          .thenReturn(
              Optional.of(resultDocument(c.payment(), c.shares(), c.shares(), c.listing())));
    }
    when(repo.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(rows);
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 2, 28);
    assertThat(service.backfill(from, to))
        .isEqualTo(new DartRightsIssueListingService.Result(6, 0, 0));
    for (int i = 0; i < cases.size(); i++) {
      HistoricalIssue c = cases.get(i);
      Ipo row = rows.get(i);
      assertThat(row.getId()).isEqualTo(100L + i);
      assertThat(row.getListingDate()).isEqualTo(c.listing());
      assertThat(row.getListingDateEvidenceReceipt()).isEqualTo(receipts.get(i));
      assertThat(row.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
      assertThat(row.getSubscriptionStartDate()).isEqualTo(c.payment().minusDays(7));
      assertThat(row.getSubscriptionEndDate()).isEqualTo(c.payment().minusDays(2));
      assertThat(row.getRefundDate()).isEqualTo(c.payment().plusDays(1));
      assertThat(row.getFixedPrice()).isEqualTo(12345L);
    }
    assertThat(service.backfill(from, to))
        .isEqualTo(new DartRightsIssueListingService.Result(0, 0, 0));
    verify(repo, times(6)).saveAndFlush(any(Ipo.class));
  }

  @Test
  void differentPaymentAndConflictingResultTotalsRequireReview() {
    Ipo row = ipo(ORIGINAL);
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    LocalDate payment = LocalDate.of(2026, 2, 19);
    LocalDate listing = LocalDate.of(2026, 3, 10);
    when(filings.downloadDocument(ORIGINAL))
        .thenReturn(Optional.of(originalDocument(payment, 14_500_000L)));
    when(filings.downloadDocument(REPORT))
        .thenReturn(
            Optional.of(resultDocument(payment.plusDays(1), 14_500_000L, 14_500_000L, listing)),
            Optional.of(resultDocument(payment, 14_500_000L, 14_400_000L, listing)));
    LocalDate from = LocalDate.of(2026, 2, 1);
    LocalDate to = LocalDate.of(2026, 2, 28);
    assertThat(service.backfill(from, to))
        .isEqualTo(new DartRightsIssueListingService.Result(0, 1, 0));
    assertThat(service.backfill(from, to))
        .isEqualTo(new DartRightsIssueListingService.Result(0, 1, 0));
    assertThat(row.getListingDate()).isNull();
    verify(repo, never()).saveAndFlush(any());
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
  @Test
  void correctedOriginalReceiptMatchesLatestPaymentAndShares() {
    Ipo row = ipo("20251121000467");
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 3, 5)));
    String correctionReceipt = "20260220000001";
    when(filings.findEquityFilings(any(), any(), any()))
        .thenReturn(List.of(
            new FilingRef("20251121000467", "증권신고서(지분증권)", LocalDate.of(2025, 11, 21)),
            new FilingRef(correctionReceipt, "[발행조건확정]증권신고서(지분증권)", LocalDate.of(2026, 2, 20))));
    when(filings.downloadDocument("20251121000467"))
        .thenReturn(Optional.of(originalDocument(LocalDate.of(2026, 2, 6), 14_500_000L)));
    when(filings.downloadDocument(correctionReceipt))
        .thenReturn(Optional.of(
            "<DOCUMENT><P>정정 대상 공시서류의 최초 제출일 2025.11.21</P>"
                + originalDocument(LocalDate.of(2026, 2, 25), 14_500_000L)
                + "</DOCUMENT>"));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(resultDocument(
            LocalDate.of(2026, 2, 25), 14_500_000L, 14_500_000L,
            LocalDate.of(2026, 3, 10))));
    assertThat(service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).saved())
        .isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 10));
  }

  @Test
  void acceptsOnlyExplicitlyExplainedUnissuedShares() {
    Ipo row = ipo("20251105000215");
    setupReports(row, new FilingRef(REPORT, "증권발행실적보고서", LocalDate.of(2026, 2, 5)));
    when(filings.downloadDocument("20251105000215"))
        .thenReturn(Optional.of(originalDocument(LocalDate.of(2026, 1, 29), 3_000_000L)));
    String result = resultDocument(
        LocalDate.of(2026, 1, 29), 1_995_582L, 1_995_582L,
        LocalDate.of(2026, 2, 10));
    when(filings.downloadDocument(REPORT))
        .thenReturn(Optional.of(result + "<P>실권주 1,004,418주 미발행</P>"));
    assertThat(service.backfill(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).saved())
        .isEqualTo(1);
    assertThat(row.getListingDate()).isEqualTo(LocalDate.of(2026, 2, 10));
  }

}
