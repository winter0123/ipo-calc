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
  void extractsOnlyNewPaidSharesAndPaymentDateFromParagraphs() {
    String hyungji =
        "<DOCUMENT><P>발행주식총수 38,390,259주</P>"
            + "<P>이번 유상증자 신주 23,000,000주</P>"
            + "<P>납입일 2026.02.06</P></DOCUMENT>";
    String raon =
        "<DOCUMENT><P>기발행 주식수 20,860,012주</P>"
            + "<P>유상증자 신주 14,500,000주</P>"
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
}
