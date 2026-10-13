package com.vibeaiteam.ipocatalog.service.ipo.detail.dart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link DartFilingParser}의 합성 입력 분기 테스트(실문서 픽스처가 못 타는 경로). */
class DartFilingParserEdgeTest {

  private final DartFilingParser parser = new DartFilingParser();

  private static String table(String... rows) {
    StringBuilder sb = new StringBuilder("<TABLE>");
    for (String r : rows) {
      sb.append("<TR>");
      for (String c : r.split("\\|", -1)) {
        sb.append("<TD>").append(c).append("</TD>");
      }
      sb.append("</TR>");
    }
    return sb.append("</TABLE>").toString();
  }

  private static String summary(String unitText, String... rows) {
    return "<TITLE>요약재무정보</TITLE><P>" + unitText + "</P>" + table(rows);
  }

  @Test
  void 엔티티는_이름_십진_십육진을_풀고_모르는_것은_그대로_둔다() {
    String xml =
        table("경쟁률|1&#44;000.5")
            + table("구분|매각|x", "최대주주|A&amp;B&nbsp;&#x41;&#65;&foo;&#99999999;|10%");

    ParsedFiling f = parser.parse(xml);

    assertThat(f.competitionRate()).isEqualByComparingTo("1000.5");
    assertThat(f.largestShareholderName()).isEqualTo("A&B AA&foo;&#99999999;");
  }

  @Test
  void 천원_단위_원_단위_단위없음을_배수로_환산한다() {
    String hdr = "구분|제2기(2025년)|제1기(2024년)";
    assertThat(
            parser.parse(summary("(단위 : 천원)", hdr, "매출액|5|6", "영업이익|1|2")).financials().get(0))
        .extracting("revenue", "operatingProfit")
        .containsExactly(5_000L, 1_000L);
    assertThat(
            parser.parse(summary("(단위 : 원)", hdr, "매출액|5|6", "영업이익|1|2")).financials().get(0)
                .revenue())
        .isEqualTo(5L);
    assertThat(
            parser.parse(summary("", hdr, "매출액|5|6", "영업이익|1|2")).financials().get(0).revenue())
        .isEqualTo(5L);
    assertThat(
            parser
                .parse(summary("(단위 : 백만원)(단위 : 천원)", hdr, "매출액|5|6", "영업이익|1|2"))
                .financials()
                .get(0)
                .revenue())
        .as("마지막 단위 표기를 쓴다")
        .isEqualTo(5_000L);
  }

  @Test
  void 영업수익_영업손실_연결_접두사와_순이익_제외_행() {
    String xml =
        summary(
            "",
            "구분|제2기(2025년)|제1기(2024년)",
            "연결 영업수익|100|90",
            "영업손실|(20)|-5",
            "법인세비용차감전순이익|7|7",
            "총포괄순이익|8|8",
            "지배기업 소유주지분 순이익|9|9",
            "당기순이익|3|4");

    ParsedFiling f = parser.parse(xml);

    assertThat(f.financials()).hasSize(2);
    assertThat(f.financials().get(0).revenue()).isEqualTo(100L);
    assertThat(f.financials().get(0).operatingProfit()).isEqualTo(-20L);
    assertThat(f.financials().get(1).operatingProfit()).isEqualTo(-5L);
    assertThat(f.financials().get(0).netIncome()).isEqualTo(3L);
  }

  @Test
  void 순이익_행이_없으면_null이고_헤더보다_짧은_행은_null() {
    String xml =
        summary("", "구분|2025년|2024년|2023년", "매출액|1|2", "영업이익|3|4|5");

    ParsedFiling f = parser.parse(xml);

    assertThat(f.financials()).hasSize(3);
    assertThat(f.financials().get(0).netIncome()).isNull();
    assertThat(f.financials().get(2).revenue()).isNull();
    assertThat(f.financials().get(2).operatingProfit()).isEqualTo(5L);
  }

  @Test
  void 기간_헤더나_영업이익_행이_없는_표는_재무로_보지_않는다() {
    assertThat(parser.parse(summary("", "구분|값", "매출액|1", "영업이익|2")).financials()).isEmpty();
    assertThat(
            parser
                .parse(summary("", "구분|2025년|2024년", "매출액|1|2"))
                .financials())
        .isEmpty();
  }

  @Test
  void 자본금은_헤더의_첫_기간_열에서_읽고_대괄호는_무시한다() {
    String xml =
        summary(
            "(단위 : 천원)",
            "구분|제2기(2025년)|제1기(2024년)",
            "[자본금]|2,000|1,000",
            "매출액|1|2",
            "영업이익|3|4");

    assertThat(parser.parse(xml).capitalStock()).isEqualTo(2_000_000L);
  }

  @Test
  void 자본금_행만_있고_기간_헤더가_없으면_자본금은_null() {
    assertThat(parser.parse(summary("", "자본금|5")).capitalStock()).isNull();
  }

  @Test
  void 유통가능_일정은_두_단계_미만이면_빈_목록이고_비율_열이_없으면_null() {
    String one = table("상장일 유통가능|100|10%");
    assertThat(parser.parse(one).floatSchedule()).isEmpty();

    String two =
        table(
            "상장일 유통가능|100",
            "상장후 1개월 유통가능|abc",
            "상장후 3개월 유통가능|300|30");
    ParsedFiling f = parser.parse(two);
    assertThat(f.floatSchedule()).hasSize(2);
    assertThat(f.floatSchedule().get(0).ratio()).isNull();
    assertThat(f.floatSchedule().get(1).ratio()).isEqualByComparingTo("30");
  }

  @Test
  void 경쟁률은_행의_마지막_숫자_셀이고_숫자가_없으면_다음_표를_본다() {
    String xml =
        table("경쟁률|-|해당없음") + table("경 쟁 률|1,234.50|x|2,000.5:1|3,000");

    assertThat(parser.parse(xml).competitionRate()).isEqualByComparingTo("3000");
    assertThat(parser.parse(table("경쟁률|-")).competitionRate()).isNull();
  }

  @Test
  void 확약비율은_미확약_합계로_계산하고_미확약_수량이_숫자가_아니면_전부_확약() {
    // 합계 열 수량은 행의 뒤에서 두 번째 셀이다.
    String xml = table("구분|합계|비고", "미확약|25|b", "합계|100|d");
    assertThat(parser.parse(xml).lockUpCommitmentRatio()).isEqualByComparingTo("75.00");

    String nonNumeric = table("구분|합계|비고", "미확약|n/a|b", "합계|100|d");
    assertThat(parser.parse(nonNumeric).lockUpCommitmentRatio()).isEqualByComparingTo("100.00");
  }

  @Test
  void 확약비율_계산_불가_표는_건너뛴다() {
    // 합계가 0, 숫자가 아님, 행 누락, 헤더에 합계 없음
    for (String t :
        new String[] {
          table("구분|합계|비고", "미확약|1|a|b", "합계|0|c|d"),
          table("구분|합계|비고", "미확약|1|a|b", "합계|n/a|c|d"),
          table("구분|합계|비고", "합계|100|c|d"),
          table("구분|합계|비고", "미확약|1|a|b"),
          table("구분|합계|비고", "미확약|1", "합계|1"),
          table("구분|수량|비고", "미확약|1|a|b", "합계|100|c|d"),
          "<TABLE></TABLE>"
        }) {
      assertThat(parser.parse(t).lockUpCommitmentRatio()).as(t).isNull();
    }
  }

  @Test
  void 최대주주는_매각_의무보유_유통_표에서만_읽고_이름_후보는_걸러낸다() {
    String notSale = table("구분|보유", "최대주주|홍길동|30%");
    assertThat(parser.parse(notSale).largestShareholderName()).isNull();

    String sale = table("구분|의무보유", "최대주주|보통주|-|1,000|홍길동|30%|2%");
    ParsedFiling f = parser.parse(sale);
    assertThat(f.largestShareholderName()).isEqualTo("홍길동");
    assertThat(f.largestShareholderRatio()).isEqualByComparingTo("30");

    // 이름 후보가 전혀 없고 비율도 없으면 둘 다 null
    ParsedFiling none = parser.parse(table("구분|유통", "최대주주|보통주|-|1,000"));
    assertThat(none.largestShareholderName()).isNull();
    assertThat(none.largestShareholderRatio()).isNull();
  }

  @Test
  void 중첩_또는_닫히지_않은_표여도_예외_없이_끝난다() {
    String xml = "<TITLE>요약재무정보</TITLE><TABLE><TR><TD>2025년</TD><TD>2024년</TD></TR>";
    ParsedFiling f = parser.parse(xml);
    assertThat(f.financials()).isEmpty();
    assertThat(f.floatSchedule()).isEmpty();
  }
}
