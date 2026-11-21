package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 유상증자 증권발행실적보고서에서 다른 종류의 상장일을 섞지 않는 파서 테스트. */
class DartRightsIssueListingParserTest {
  private final DartRightsIssueListingParser parser = new DartRightsIssueListingParser();

  private static String report(String rows) {
    return "<DOCUMENT><TITLE>증권발행실적보고서</TITLE>"
        + "<P>원신고서 20260201000001</P><TABLE>"
        + rows
        + "</TABLE></DOCUMENT>";
  }

  private static String row(String label, String value) {
    return "<TR><TD>" + label + "</TD><TD>" + value + "</TD></TR>";
  }

  static Stream<Arguments> sixHistoricalIssueDates() {
    return Stream.of(
        Arguments.of("형지엘리트", "2026.02.13", LocalDate.of(2026, 2, 13)),
        Arguments.of("아이씨에이치", "2026.02.25", LocalDate.of(2026, 2, 25)),
        Arguments.of("아미코젠", "2026.02.26", LocalDate.of(2026, 2, 26)),
        Arguments.of("라온피플", "2026.03.10", LocalDate.of(2026, 3, 10)),
        Arguments.of("비보존제약", "2026.03.19", LocalDate.of(2026, 3, 19)),
        Arguments.of("대한광통신", "2026.03.19", LocalDate.of(2026, 3, 19)));
  }

  @ParameterizedTest(name = "{0}: 유상 신주 상장예정일만 추출")
  @MethodSource("sixHistoricalIssueDates")
  void parsesSixExpectedDatesFromSyntheticReportSamples(
      String company, String date, LocalDate expected) {
    String xml =
        report(
            row("납입일", "2026.02.05")
                + row("신주인수권증서 상장예정일", "2026.02.09")
                + row("주권 상장 및 유통 예정일", date)
                + row("무상증자 신주 상장 예정일", "2026.03.24"));
    var evidence = parser.parse(xml);
    assertThat(evidence).hasSize(1);
    assertThat(evidence.get(0).listingDate()).isEqualTo(expected);
    assertThat(evidence.get(0).originalReceipt()).isEqualTo("20260201000001");
  }

  @ParameterizedTest(name = "{0}: P 문단의 유상 신주 상장일")
  @MethodSource("sixHistoricalIssueDates")
  void readsListingFromParagraphOutsideTables(String company, String date, LocalDate expected) {
    String xml =
        report(row("발행주식총수", "38,390,259주") + row("기발행주식수", "20,860,012주"))
            .replace(
                "</DOCUMENT>",
                "<P>유상증자에 따른 신주 상장 및 유통개시 예정일: "
                    + date
                    + "</P>"
                    + "<P>신주인수권증서 상장예정일: 2026.02.09</P>"
                    + "<P>무상증자 신주 상장 예정일: 2026.03.24</P></DOCUMENT>");
    assertThat(parser.parse(xml))
        .extracting(DartRightsIssueListingParser.Evidence::listingDate)
        .containsExactly(expected);
  }

  @Test
  void extractsCodedNewPaidSharesAndPaymentDateDespiteNarrativeNoise() {
    String hyungji =
        "<DOCUMENT><P>발행주식총수 38,390,259주</P>"
            + "<P>이번 유상증자 신주 1주</P>"
            + "<TABLE><TR><TU ACODE=\"STK_CNT\">23,000,000</TU></TR></TABLE>"
            + "<P>납입일 2026.02.06</P></DOCUMENT>";
    String raon =
        "<DOCUMENT><P>기발행 주식수 20,860,012주</P>"
            + "<P>유상증자 신주 1주</P>"
            + "<TABLE><TR><TU ACODE=\"STK_CNT\">14,500,000</TU></TR></TABLE>"
            + "<P>주금 납입일 2026년 2월 20일</P></DOCUMENT>";
    assertThat(parser.issueIdentity(hyungji).issuedShares()).isEqualTo(23_000_000L);
    assertThat(parser.issueIdentity(raon).issuedShares()).isEqualTo(14_500_000L);
    assertThat(parser.issueIdentity(hyungji).paymentDate()).isEqualTo(LocalDate.of(2026, 2, 6));
    assertThat(parser.issueIdentity(raon).paymentDate()).isEqualTo(LocalDate.of(2026, 2, 20));
  }

  @Test
  void listingLabelAndDateInAdjacentParagraphsArePaired() {
    String xml =
        "<DOCUMENT><P>유상증자 신주 상장 및 유통 예정일</P>"
            + "<P>2026.02.13</P>"
            + "<P>무상증자 신주 상장 예정일</P><P>2026.03.24</P>"
            + "</DOCUMENT>";
    assertThat(parser.parse(xml))
        .extracting(DartRightsIssueListingParser.Evidence::listingDate)
        .containsExactly(LocalDate.of(2026, 2, 13));
  }

  @Test
  void amicozenUsesDateAfterListingLabelInsteadOfEarlierRegistrationDate() {
    String xml =
        "<DOCUMENT><P>등기예정일 2026년 2월 12일, "
            + "신주 상장 및 유통개시(예정)일 2026년 2월 26일</P>"
            + "<P>신주인수권증서 상장예정일 2026년 2월 9일</P>"
            + "</DOCUMENT>";
    assertThat(parser.parse(xml))
        .extracting(DartRightsIssueListingParser.Evidence::listingDate)
        .containsExactly(LocalDate.of(2026, 2, 26));
  }

  @Test
  void paymentDueColumnOverridesHistoricalDatesOutsideCurrentSchedule() {
    String xml =
        "<DOCUMENT><P>과거 납입일 2019년 9월 16일</P>"
            + "<P>청약 및 납입일정</P>"
            + "<TABLE><TR><TH>구분</TH><TH>청약일</TH><TH>납입기일</TH></TR>"
            + "<TR><TD>유상증자</TD><TD>2026년 1월 28일</TD>"
            + "<TD>2026년 1월 30일</TD></TR></TABLE>"
            + "<P>이번 유상증자 신주 1주</P>"
            + "<TABLE><TR><TU ACODE=\"STK_CNT\">23,000,000</TU></TR></TABLE>"
            + "<P>발행주식총수 38,390,259주</P></DOCUMENT>";
    var identity = parser.issueIdentity(xml);
    assertThat(identity.paymentDate()).isEqualTo(LocalDate.of(2026, 1, 30));
    assertThat(identity.issuedShares()).isEqualTo(23_000_000L);
  }

  @Test
  void missingCurrentPaymentEvidenceDoesNotUseUnrelatedPastPaymentDate() {
    String xml = "<DOCUMENT><P>2019년 9월 16일 납입일</P>" + "<P>발행주식총수 38,390,259주</P></DOCUMENT>";
    assertThat(parser.issueIdentity(xml).paymentDate()).isNull();
    assertThat(parser.issueIdentity(xml).issuedShares()).isNull();
  }

  @Test
  void structuredQuantityDisagreementRequiresReview() {
    String xml =
        "<DOCUMENT><TABLE><TR>"
            + "<TU ACODE=\"INC_CNT_TOT\">23,000,000</TU>"
            + "<TU ACODE=\"INC_SL_CNT_SUM\">22,000,000</TU>"
            + "</TR></TABLE></DOCUMENT>";
    assertThat(
            parser.parse(xml.replace("</DOCUMENT>", "<P>신주 상장 및 유통 예정일 2026.02.13</P></DOCUMENT>")))
        .extracting(DartRightsIssueListingParser.Evidence::issuedShares)
        .containsExactly((Long) null);
  }

  @Test
  void duplicatedResultTotalsAgreeWithoutDoubleCounting() {
    String xml =
        "<DOCUMENT><TABLE><TR>"
            + "<TU ACODE=\"INC_CNT_TOT\">23,000,000</TU>"
            + "<TU ACODE=\"INC_SL_CNT_SUM\">23,000,000</TU>"
            + "</TR></TABLE></DOCUMENT>";
    assertThat(
            parser.parse(xml.replace("</DOCUMENT>", "<P>신주 상장 및 유통 예정일 2026.02.13</P></DOCUMENT>")))
        .extracting(DartRightsIssueListingParser.Evidence::issuedShares)
        .containsExactly(23_000_000L);
  }

  @Test
  void narrativeAloneIsNotQuantityEvidence() {
    assertThat(parser.issueIdentity("<P>이번 유상증자 신주 1주</P>").issuedShares()).isNull();
  }

  @Test
  void ignoresExistingOutstandingSharesWithoutIssueSpecificLabel() {
    String xml =
        "<DOCUMENT><TABLE>"
            + row("발행주식총수", "38,390,259주")
            + row("기발행주식수", "20,860,012주")
            + "</TABLE></DOCUMENT>";
    assertThat(parser.issueIdentity(xml).issuedShares()).isNull();
  }

  @Test
  void raonPeopleBonusDateIsNotPaidRightsListingDate() {
    String xml =
        report(row("신주 상장 및 유통개시 예정일", "2026년 3월 10일") + row("무상증자 신주 상장 예정일", "2026년 3월 24일"));
    assertThat(parser.parse(xml))
        .extracting(DartRightsIssueListingParser.Evidence::listingDate)
        .containsExactly(LocalDate.of(2026, 3, 10));
  }

  @Test
  void multiplePaidRightsListingDatesRemainAmbiguous() {
    String xml =
        report(row("주권 상장 및 유통 예정일", "2026.03.10") + row("신주 상장 및 유통개시 예정일", "2026.03.11"));
    assertThat(parser.parse(xml))
        .extracting(DartRightsIssueListingParser.Evidence::listingDate)
        .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11));
  }

  @Test
  void ignoresWarrantAndBonusDatesWhenNoRightsListingDateExists() {
    String xml = report(row("신주인수권증서 상장예정일", "2026.03.10") + row("무상증자 신주 상장 예정일", "2026.03.24"));
    assertThat(parser.parse(xml)).isEmpty();
  }
  @Test
  void rejectsUnsubscribedSharesThatWereFullyIssuedThroughPublicOffering() {
    String xml =
        "<DOCUMENT><P>실권주 1,004,418주는 일반공모로 전량 발행하였습니다</P></DOCUMENT>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isFalse();
  }

  @Test
  void acceptsExplicitUnissuedSharesOnlyWhenDifferenceMatches() {
    String xml = "<DOCUMENT><P>실권주 1,004,418주 미발행</P></DOCUMENT>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isTrue();
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_583L)).isFalse();
  }

  @Test
  void doesNotCombineUnrelatedUnissuedReasonAndShareQuantity() {
    String xml =
        "<DOCUMENT><P>다른 발행 회차의 주식은 미발행</P>"
            + "<P>실권주 1,004,418주는 일반공모로 전량 발행하였습니다</P></DOCUMENT>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isFalse();
  }

  @Test
  void rejectsUnissuedClaimAboutDifferentSharesInSameParagraph() {
    String xml =
        "<P>실권주 1,004,418주는 인수단에 배정하였습니다. 단주는 미발행입니다.</P>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isFalse();
  }

  @Test
  void rejectsCancelledUnissuedPlan() {
    String xml =
        "<P>실권주 1,004,418주는 미발행할 예정이었으나 인수단에 배정하였습니다.</P>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isFalse();
  }

  @Test
  void acceptsConfirmedUnissuedQuantityInSingleCell() {
    String xml =
        "<TABLE><TR><TD>초과청약 배정 후 실권주 1,004,418주는 미발행 처리</TD></TR></TABLE>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isTrue();
  }

  @Test
  void rejectsQuantityAndUnissuedClaimInDifferentCells() {
    String xml =
        "<TABLE><TR><TD>실권주 1,004,418주</TD><TD>단주는 미발행</TD></TR></TABLE>";
    assertThat(parser.hasVerifiedUnissuedShares(xml, 3_000_000L, 1_995_582L)).isFalse();
  }

}
