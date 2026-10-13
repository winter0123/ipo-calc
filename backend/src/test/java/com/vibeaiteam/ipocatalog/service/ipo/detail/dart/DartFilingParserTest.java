package com.vibeaiteam.ipocatalog.service.ipo.detail.dart;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.ipocatalog.service.ipo.detail.dart.ParsedFiling.Financial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * {@link DartFilingParser} 단위 테스트.
 *
 * <p>픽스처({@code src/test/resources/dart/*.xml})는 실제 DART document.xml에서 "요약재무정보" 절 전체와
 * 유통가능·경쟁률·확약·최대주주 표를 첫 번째 것만 잘라 {@code <TITLE>발췌</TITLE>} 뒤에 이어 붙인 것이다.
 * ms-terms-confirmed는 발행조건확정 문서에서 경쟁률·확약 표만 잘랐다.
 */
class DartFilingParserTest {

  private final DartFilingParser parser = new DartFilingParser();

  private ParsedFiling parseFile(String name) throws IOException {
    String xml =
        new String(
            new ClassPathResource("dart/" + name).getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    return parser.parse(xml);
  }

  @Test
  void 엠에스바이오_투자설명서를_파싱한다() throws IOException {
    ParsedFiling f = parseFile("ms-prospectus.xml");

    assertThat(f.financials()).hasSize(4);
    assertThat(f.financials().get(1))
        .isEqualTo(
            new Financial(
                "제11기(2025년12월말)", 23_786_846_687L, 8_557_587_279L, 7_287_887_715L));
    assertThat(f.capitalStock()).isEqualTo(2_308_265_000L);
    assertThat(f.floatSchedule()).hasSize(5);
    assertThat(f.floatSchedule().get(0).label()).isEqualTo("상장일 유통가능");
    assertThat(f.floatSchedule().get(0).shares()).isEqualTo(6_347_310L);
    assertThat(f.floatSchedule().get(0).ratio()).isEqualByComparingTo("23.33");
    assertThat(f.floatSchedule().get(4).ratio()).isEqualByComparingTo("100");
    assertThat(f.competitionRate()).isEqualByComparingTo("1096.56");
    assertThat(f.lockUpCommitmentRatio()).isEqualByComparingTo("30.62");
    assertThat(f.largestShareholderName()).isEqualTo("김성포");
    assertThat(f.largestShareholderRatio()).isEqualByComparingTo("30.10");
  }

  @Test
  void 디티에스는_백만원_단위를_원으로_환산한다() throws IOException {
    ParsedFiling f = parseFile("dts-prospectus.xml");

    assertThat(f.financials().get(1).revenue()).isEqualTo(142_718_000_000L);
    assertThat(f.competitionRate()).isEqualByComparingTo("983.98");
    assertThat(f.lockUpCommitmentRatio()).isEqualByComparingTo("13.31");
    assertThat(f.largestShareholderName()).isEqualTo("㈜다산네트웍스");
    assertThat(f.largestShareholderRatio()).isEqualByComparingTo("37.69");
  }

  @Test
  void 크리에이츠는_괄호_음수를_읽는다() throws IOException {
    ParsedFiling f = parseFile("creates-prospectus.xml");

    assertThat(f.financials().get(1).revenue()).isEqualTo(104_181_888_150L);
    assertThat(f.financials().get(2).netIncome()).isEqualTo(-3_222_876_744L);
  }

  @Test
  void 엘리스그룹은_연결당기순이익_행도_순이익으로_읽는다() throws IOException {
    ParsedFiling f = parseFile("elice-prospectus.xml");

    assertThat(f.largestShareholderName()).isEqualTo("김재원");
    assertThat(f.largestShareholderRatio()).isEqualByComparingTo("27.64");
    assertThat(f.competitionRate()).isEqualByComparingTo("388.84");
    assertThat(f.floatSchedule()).hasSize(6);
    assertThat(f.financials()).hasSize(4);
    assertThat(f.financials().get(1).netIncome()).isEqualTo(15_665_669_657L);
    assertThat(f.capitalStock()).isEqualTo(4_436_797_500L);
  }

  @Test
  void 발행조건확정_문서는_재무_없이도_경쟁률과_확약을_읽는다() throws IOException {
    ParsedFiling f = parseFile("ms-terms-confirmed.xml");

    assertThat(f.financials()).isEmpty();
    assertThat(f.floatSchedule()).isEmpty();
    assertThat(f.competitionRate()).isEqualByComparingTo("1096.56");
    assertThat(f.lockUpCommitmentRatio()).isEqualByComparingTo("30.62");
    assertThat(f.capitalStock()).isNull();
  }

  @Test
  void 요약재무정보_절_밖의_재무표는_무시한다() {
    String comparable =
        "<TABLE><TR><TD>구분</TD><TD>2025년</TD><TD>2024년</TD></TR>"
            + "<TR><TD>매출액</TD><TD>999</TD><TD>888</TD></TR>"
            + "<TR><TD>영업이익</TD><TD>99</TD><TD>88</TD></TR></TABLE>";
    String summary =
        "<TITLE>1. 요약재무정보</TITLE><TABLE><TR><TD>(단위 : 천원)</TD></TR></TABLE>"
            + "<TABLE><TR><TD>구분</TD><TD>제2기(2025년)</TD><TD>제1기(2024년)</TD></TR>"
            + "<TR><TD>매출액</TD><TD>1,000</TD><TD>-</TD></TR>"
            + "<TR><TD>영업이익</TD><TD>(10)</TD><TD>5</TD></TR></TABLE>";
    String xml =
        "<TITLE>공모가격 산정</TITLE>" + comparable + summary + "<TITLE>다음 절</TITLE>" + comparable;

    ParsedFiling f = parser.parse(xml);

    assertThat(f.financials()).hasSize(2);
    assertThat(f.financials().get(0).revenue()).isEqualTo(1_000_000L);
    assertThat(f.financials().get(0).operatingProfit()).isEqualTo(-10_000L);
    assertThat(f.financials().get(1).revenue()).isNull();
    assertThat(f.financials().get(1).netIncome()).isNull();
  }

  @Test
  void 이상한_입력도_예외_없이_빈_결과를_돌려준다() {
    for (String xml : new String[] {null, "", "<TABLE><TR><TD>경쟁률", "<TITLE>요약재무정보</TITLE>"}) {
      ParsedFiling f = parser.parse(xml);
      assertThat(f.financials()).isEmpty();
      assertThat(f.competitionRate()).isNull();
    }
    assertThat(parser.parse("<TABLE><TR><TD>경쟁률</TD><TD>1,0.5.</TD></TR></TABLE>")
        .competitionRate()).isNull();
  }
}
