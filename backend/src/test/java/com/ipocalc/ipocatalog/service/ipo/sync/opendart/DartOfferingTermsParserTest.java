package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** {@link DartOfferingTermsParser} 단위 테스트. SF-2 fixture 파일 직접 읽기. */
class DartOfferingTermsParserTest {

  private final DartOfferingTermsParser parser = new DartOfferingTermsParser();

  private String readFixture(String filename) throws IOException {
    Path path = Path.of("src/test/resources/dart", filename);
    return Files.readString(path);
  }

  /** 원신고(20261008000649): 희망공모가 주석에 9400~11000, 모집가액 단일값 9400, fixedPrice=null. */
  @Test
  void parse_ykmcOriginalReport() throws IOException {
    String xml = readFixture("ykmc-20261008000649-offering-summary.html");

    DartOfferingTerms terms = parser.parse(xml, "20261008000649", false);

    // 밴드는 주석(주2)의 "9,400원 ~ 11,000원"에서 추출
    assertThat(terms.lowerBandPrice()).isEqualTo(9_400L);
    assertThat(terms.upperBandPrice()).isEqualTo(11_000L);
    // 원신고는 확정가 없음
    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasValidBand()).isTrue();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
    // 청약일정 파싱
    assertThat(terms.subscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    assertThat(terms.subscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
  }

  /** 기재정정(20261030000616): 원신고와 동일, fixedPrice=null. */
  @Test
  void parse_ykmcCorrectionReport() throws IOException {
    String xml = readFixture("ykmc-20261030000616-offering-summary.html");

    DartOfferingTerms terms = parser.parse(xml, "20261030000616", false);

    // 밴드 동일하게 추출
    assertThat(terms.lowerBandPrice()).isEqualTo(9_400L);
    assertThat(terms.upperBandPrice()).isEqualTo(11_000L);
    // 정정 공시라도 발행조건확정이 아니면 fixedPrice=null
    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasValidBand()).isTrue();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
    assertThat(terms.subscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    assertThat(terms.subscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
  }

  /** 발행조건확정(20261103000312): fixedPrice=11000, 과거 밴드 미반환. */
  @Test
  void parse_ykmcFinalizedReport() throws IOException {
    String xml = readFixture("ykmc-20261103000312-offering-summary.html");

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    // 과거 밴드의 출처는 원신고 또는 기재정정이다.
    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
    // 확정가는 정정 후 셀(모집(매출)가액 11,000원)에서만 추출
    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms.hasValidBand()).isFalse();
    assertThat(terms.hasConfirmedFixedPrice()).isTrue();
    assertThat(terms.subscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    assertThat(terms.subscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
  }

  /** 일반 증권신고서: 희망가 범위만 존재. */
  @Test
  void parse_regularReportWithBand() {
    String xml =
        """
        <TABLE>
          <TR><TH>희망공모가</TH></TR>
          <TR><TD>15,000원 ~ 18,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261001000001", false);

    assertThat(terms.lowerBandPrice()).isEqualTo(15_000L);
    assertThat(terms.upperBandPrice()).isEqualTo(18_000L);
    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasValidBand()).isTrue();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
  }

  /** 단일 가격만 있는 일반 문서: 밴드와 확정가 모두 null. */
  @Test
  void parse_singlePriceOnly() {
    String xml =
        """
        <TABLE>
          <TR><TH>모집가액</TH></TR>
          <TR><TD>10,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261001000002", false);

    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasValidBand()).isFalse();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
  }

  /** 발행조건확정이 아닌 문서의 단일 가격: fixedPrice == null. */
  @Test
  void parse_nonFinalizedSinglePrice() {
    String xml =
        """
        <TABLE>
          <TR><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>모집가액</TD><TD>9,500원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261001000004", false);

    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
  }

  /** 발행조건확정 공시에서 정정 후 단일 가격 추출 (HTML 색상 없는 입력 검증). 실제 fixture 구조와 유사하게 "공통정정사항" 행과 정정 후 가격 포함. */
  @Test
  void parse_finalizedReportWithSingleFixedPrice_noColor() {
    String xml =
        """
        <TABLE>
          <TR><TH>정 정 전</TH><TH>정 정 후</TH></TR>
          <TR><TD>공통정정사항</TD>
              <TD>- 모집(매출)가액: 11,000원 - 모집(매출)총액: 25,300,000,000원</TD></TR>
        </TABLE>
        <TABLE>
          <TR><TH>증권의종류</TH><TH>증권수량</TH><TH>액면가액</TH>
              <TH>모집(매출)가액</TH><TH>모집(매출)총액</TH><TH>모집(매출)방법</TH></TR>
          <TR><TD>기명식 보통주</TD><TD>2,300,000</TD><TD>500</TD>
              <TD>11,000</TD><TD>25,300,000,000</TD><TD>일반공모</TD></TR>
        </TABLE>
        <TABLE>
          <TR><TH>청약기일</TH><TH>납입기일</TH><TH>청약공고일</TH>
              <TH>배정공고일</TH><TH>배정기준일</TH></TR>
          <TR><TD>2026년 11월 04일 ~ 2026년 11월 05일</TD><TD>2026년 11월 09일</TD>
              <TD>2026년 11월 04일</TD><TD>2026년 11월 09일</TD><TD>-</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms.hasConfirmedFixedPrice()).isTrue();
    assertThat(terms.subscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    assertThat(terms.subscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
  }

  /**
   * 총액(모집총액)과 액면가, 주식수, 단위 숫자는 가격으로 인식하지 않음. isFinalizedReport=true지만 정정표(정정전/정정후 헤더)가 없으므로
   * fixedPrice=null.
   */
  @Test
  void parse_ignoresTotalAmountParValueShareCount() {
    String xml =
        """
        <TABLE>
          <TR><TH>모집(매출)가액</TH><TH>모집(매출)총액</TH><TH>액면가액</TH><TH>증권 수량</TH></TR>
          <TR><TD>11,000원</TD><TD>25,300,000,000원</TD><TD>500원</TD><TD>2,300,000</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    // 정정표(정정전/정정후 헤더)가 없으므로 fixedPrice=null
    assertThat(terms.fixedPrice()).isNull();
    // 단일 가격만 있으므로 밴드도 없음
    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
  }

  /** 정정 전 단일값(9400)은 fixedPrice가 아님. */
  @Test
  void parse_correctionBeforeSingleValueNotFixedPrice() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>모집(매출)가액</TD><TD>9,400원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    // 정정 전 값은 fixedPrice가 되지 않음 (정정 후만 확정가)
    assertThat(terms.fixedPrice()).isNull();
  }

  /** 정정 전 9400, 정정 후 11000이 같이 있을 때 정정 후만 확정가로. */
  @Test
  void parse_correctionBeforeAndAfter_onlyAfterIsFixed() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>모집(매출)가액</TD><TD>9,400원</TD><TD>11,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
  }

  /** 문서 다른 곳의 근거 없는 단일값이나 숫자 2개 임의 결합 금지. */
  @Test
  void parse_noArbitraryCombinationOfUnrelatedNumbers() {
    String xml =
        """
        <TABLE>
          <TR><TH>일반정보</TH></TR>
          <TR><TD>기관투자자 11,000주, 일반청약자 9,400주</TD></TR>
        </TABLE>
        <TABLE>
          <TR><TH>청약기일</TH></TR>
          <TR><TD>2026년 11월 04일 ~ 2026년 11월 05일</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", false);

    // 관련 없는 표의 숫자는 가격으로 인식하지 않음
    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
    assertThat(terms.fixedPrice()).isNull();
  }

  /** 청약기일 파싱. */
  @Test
  void parse_subscriptionDates() {
    String xml =
        """
        <TABLE>
          <TR><TH>청약기일</TH></TR>
          <TR><TD>2026년 11월 04일 ~ 2026년 11월 05일</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", false);

    assertThat(terms.subscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    assertThat(terms.subscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
  }

  /** 주식수량 파싱. */
  @Test
  void parse_offeringShareCount() {
    String xml =
        """
        <TABLE>
          <TR><TH>증권수량</TH></TR>
          <TR><TD>2,200,000</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", false);

    assertThat(terms.offeringShareCount()).isEqualTo(2_200_000L);
  }

  /** PARSER_VERSION이 올라갔는지 확인. */
  @Test
  void parserVersion_increased() {
    assertThat(DartOfferingTerms.PARSER_VERSION).isGreaterThan(1);
  }

  /* ======================= Issue (1) ======================= */
  /**
   * 단일 헤더 행 [항목 | 정 정 전 | 정 정 후], 값 행 [확정공모가 | 9,400원 | 11,000원] → fixedPrice=11000. 정정 후 명시 헤더가
   * 있으면 같은 가격 라벨의 두 번째 행을 요구하지 말 것.
   */
  @Test
  void parse_correctionHeaderSingleRow_fixedPriceFromAfterColumn() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정 정 전</TH><TH>정 정 후</TH></TR>
          <TR><TD>확정공모가</TD><TD>9,400원</TD><TD>11,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms.hasConfirmedFixedPrice()).isTrue();
  }

  /* ======================= Issue (2) ======================= */
  /**
   * [모집가액 | 모집총액], [9,400원 | 21,620,000,000원 ~ 25,300,000,000원] → 밴드 양쪽 null. 희망가 라벨과 범위가 같은 문장/셀에
   * 결합되거나 명시적 희망가 행/열에 있는 경우만 읽고 일반 표 전체 스캔에서 총액이 밴드로 들어가지 않게 함.
   */
  @Test
  void parse_priceAndTotalAmountSameRow_noBandFromTotalAmount() {
    String xml =
        """
        <TABLE>
          <TR><TH>모집가액</TH><TH>모집총액</TH></TR>
          <TR><TD>9,400원</TD><TD>21,620,000,000원 ~ 25,300,000,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261008000649", false);

    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
    assertThat(terms.hasValidBand()).isFalse();
  }

  /* ======================= Issue (3) ======================= */
  /** 단일 모집가액 컬럼 행에 9,400원과 11,000원이 따로 있어도 밴드가 아님. min/max fallback 제거. 역전된 범위도 거부. */
  @Test
  void parse_singlePriceColumnMultipleRows_noBandFromMinMax() {
    String xml =
        """
        <TABLE>
          <TR><TH>모집가액</TH></TR>
          <TR><TD>9,400원</TD></TR>
          <TR><TD>11,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261001000001", false);

    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
    assertThat(terms.hasValidBand()).isFalse();
  }

  /** 역전된 범위(20,000원 ~ 15,000원)는 거부. */
  @Test
  void parse_invalidPriceRange_rejected() {
    String xml =
        """
        <TABLE>
          <TR><TH>희망공모가</TH></TR>
          <TR><TD>20,000원 ~ 15,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261001000003", false);

    assertThat(terms.lowerBandPrice()).isNull();
    assertThat(terms.upperBandPrice()).isNull();
    assertThat(terms.hasValidBand()).isFalse();
  }

  /* ======================= Issue (4) ======================= */
  /**
   * 첫 표 [정정전 | 정정후]에 가격 없고, 다음 표 [항목 | 정정전 | 정정후]에 [공통정정사항 | 모집가액: 9,400원 | 모집가액: 11,000원] → 11000.
   * 정정 후 인덱스는 표마다 검출. 확정가 정규식이 11000을 뒤 000으로 읽거나 범위의 첫 값을 확정가로 읽지 않도록, 11,000/11000 모두 지원하고 전체
   * 토큰·단일 값 검사. 하드코딩 가격 상한/하한으로 의미 판단 금지.
   */
  @Test
  void parse_multiTableCorrection_afterIndexPerTable_fixedPrice11000() {
    String xml =
        """
        <TABLE>
          <TR><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>공통사항</TD><TD>일부내용</TD></TR>
        </TABLE>
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>공통정정사항</TD><TD>모집가액: 9,400원</TD><TD>모집가액: 11,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms.hasConfirmedFixedPrice()).isTrue();
  }

  /** 11,000원과 11000원 모두 지원. */
  @Test
  void parse_fixedPrice_both11000And11000Supported() {
    String xml1 =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정 정 전</TH><TH>정 정 후</TH></TR>
          <TR><TD>확정공모가</TD><TD>9,400원</TD><TD>11,000원</TD></TR>
        </TABLE>
        """;
    String xml2 =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정 정 전</TH><TH>정 정 후</TH></TR>
          <TR><TD>확정공모가</TD><TD>9,400원</TD><TD>11000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms1 = parser.parse(xml1, "20261103000312", true);
    DartOfferingTerms terms2 = parser.parse(xml2, "20261103000312", true);

    assertThat(terms1.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms2.fixedPrice()).isEqualTo(11_000L);
  }

  /** 서로 다른 유효 값의 모호성은 null로. */
  @Test
  void parse_ambiguousDifferentValidValues_returnsNull() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정 정 전</TH><TH>정 정 후</TH></TR>
          <TR><TD>모집가액</TD><TD>10,000원</TD><TD>12,000원</TD></TR>
          <TR><TD>매출가액</TD><TD>11,000원</TD><TD>13,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    // 모집가액 10000→12000, 매출가액 11000→13000: 서로 다른 라벨에서 다른 값 → 모호함 → null
    assertThat(terms.fixedPrice()).isNull();
  }

  /** 비고 열이 있어도 정정 후 열의 확정가를 읽는다. */
  @Test
  void parse_correctionHeaderWithNoteColumn_fixedPriceFromAfterColumn() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH><TH>비고</TH></TR>
          <TR><TD>확정공모가</TD><TD>9,400원</TD><TD>11,000원</TD><TD>참고</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms.hasConfirmedFixedPrice()).isTrue();
  }

  /**
   * B: [공통정정사항 | - | 모집가액: 9,400원 ~ 11,000원] → fixed=null (현재 9400). 공통정정사항의 정정 후 셀에 범위(~ 포함)가 있으면
   * 확정가로 쓰지 않음.
   */
  @Test
  void parse_commonCorrectionWithRangeInAfterCell_noFixedPrice() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>공통정정사항</TD><TD>-</TD><TD>모집가액: 9,400원 ~ 11,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
  }

  /**
   * C: [공통정정사항 | - | 확정공모가: 11,000원<br>
   * 확정공모가: 12,000원] → fixed=null (현재 12000). 같은 셀에 여러 확정공모가가 있으면 모호함으로 null.
   */
  @Test
  void parse_commonCorrectionWithMultipleFixedPrices_noFixedPrice() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>공통정정사항</TD><TD>-</TD><TD>확정공모가: 11,000원<BR/>확정공모가: 12,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
  }

  /**
   * 표 사이에 서로 다른 유효 확정가가 있으면 모호함으로 null. 첫 표: [확정공모가 | 9400 | 11000], 두 번째 표: [확정공모가 | 10000 |
   * 13000] → fixed=null.
   */
  @Test
  void parse_multiTableDifferentFixedPrices_noFixedPrice() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>확정공모가</TD><TD>9,400원</TD><TD>11,000원</TD></TR>
        </TABLE>
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>확정공모가</TD><TD>10,000원</TD><TD>13,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isNull();
    assertThat(terms.hasConfirmedFixedPrice()).isFalse();
  }

  /**
   * 정정 후 헤더가 없는 표에서 이전 표의 인덱스 재사용 방지. 첫 표: [항목|정정전|정정후] [확정공모가|9400|11000] (정정후 인덱스=2) 두 번째 표: 헤더
   * 없음, [확정공모가|-|13000] → 첫 표의 인덱스(2) 재사용하면 13000으로 잘못 인식할 수 있음.
   */
  @Test
  void parse_multiTableNoHeaderInSecondTable_noIndexReuse() {
    String xml =
        """
        <TABLE>
          <TR><TH>항목</TH><TH>정정전</TH><TH>정정후</TH></TR>
          <TR><TD>확정공모가</TD><TD>9,400원</TD><TD>11,000원</TD></TR>
        </TABLE>
        <TABLE>
          <TR><TD>확정공모가</TD><TD>-</TD><TD>13,000원</TD></TR>
        </TABLE>
        """;

    DartOfferingTerms terms = parser.parse(xml, "20261103000312", true);

    assertThat(terms.fixedPrice()).isEqualTo(11_000L);
    assertThat(terms.hasConfirmedFixedPrice()).isTrue();
  }
}
