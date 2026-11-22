package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KrxNewListingNoticeParserTest {
  private final KrxNewListingNoticeParser parser = new KrxNewListingNoticeParser();

  // 공식 KIND 넥스아이 20261110001806(70760.htm)에 공개된 필드의 재구성 HTML.
  // 공식 원본 파일이 아닌 합성 HTML이므로 라이브 문서 재생 성공으로 간주하지 않는다.
  private static final String NOTICE =
      "<html><body>신규상장"
          + "<table><tr><td>1.회사명</td><td>주식회사 넥스아이</td></tr>"
          + "<tr><td>2.주식의 종류와 수</td><td>주권 종류</td>"
          + "<td>표준코드</td><td>단축코드</td><td>주식수(주)</td></tr>"
          + "<tr><td>보통주</td><td>KR7493410005</td>"
          + "<td>A493410</td><td>45,845,239</td></tr>"
          + "<tr><td>3.액면가(원)</td><td>500</td></tr>"
          + "<tr><td>9.승인일</td><td>2026년 11월 10일</td></tr>"
          + "<tr><td>10.상장일(매매개시일)</td>"
          + "<td>2026년 11월 12일</td></tr></table></body></html>";

  @Test
  void parsesVerifiedNewListingFieldsWithSeparateApprovalDay() {
    var evidence = parser.parse(NOTICE);
    assertThat(evidence.stockCode()).isEqualTo("493410");
    assertThat(evidence.listingDate()).isEqualTo(LocalDate.of(2026, 11, 12));
  }

  @Test
  void refusesWrongDocumentCodeAndAmbiguousCommonShares() {
    assertThatThrownBy(() -> parser.parse(NOTICE.replace("신규상장", "추가상장")))
        .isInstanceOf(IllegalArgumentException.class);
    String duplicate =
        NOTICE.replace(
            "<tr><td>3.액면가(원)",
            "<tr><td>보통주</td><td>KR7493410005</td>"
                + "<td>A493410</td><td>45,845,239</td></tr><tr><td>3.액면가(원)");
    assertThatThrownBy(() -> parser.parse(duplicate))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesMissingOrRepeatedListingDate() {
    assertThatThrownBy(
            () -> parser.parse(NOTICE.replace("10.상장일(매매개시일)", "10.참고일")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parser.parse(NOTICE.replace("</table>", 
        "<tr><td>10.상장일(매매개시일)</td><td>2026년 11월 13일</td></tr></table>")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
