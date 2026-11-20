package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 원신고 접수번호를 검증된 최초제출일에서만 연결한다. */
class DartFilingOriginTest {

  private static final String CORP = "01658035";

  private OpenDartDisclosureItem filing(String receipt, String date, String name) {
    return OpenDartDisclosureItem.builder()
        .corpCode(CORP)
        .rceptNo(receipt)
        .rceptDt(date)
        .reportNm(name)
        .build();
  }

  @Test
  void finalizedFilingResolvesOriginalByExplicitFirstSubmissionDate() {
    var original = filing("20261008000649", "20261008", "증권신고서(지분증권)");
    var correction = filing("20261030000616", "20261030", "[기재정정]증권신고서(지분증권)");
    var finalized = filing("20261103000312", "20261103", "[발행조건확정]증권신고서(지분증권)");
    String xml =
        "<DOCUMENT><TABLE><TR><TD>정정대상 공시서류의 최초 제출일</TD>"
            + "<TD>2026년 10월 08일</TD></TR></TABLE></DOCUMENT>";
    assertThat(DartFilingOrigin.originalSubmissionDate(xml)).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(
            DartFilingOrigin.resolveOriginalReceipt(
                finalized, List.of(finalized, correction, original), xml))
        .isEqualTo("20261008000649");
  }

  @Test
  void missingOrAmbiguousOriginNeverGuessesOfferingRound() {
    var old = filing("20261008000649", "20261008", "증권신고서(지분증권)");
    var other = filing("20261008000888", "20261008", "증권신고서(지분증권)");
    var correction = filing("20261103000312", "20261103", "[기재정정]증권신고서(지분증권)");
    String xml = "<DOCUMENT>정정대상 공시서류의 최초 제출일: 2026.10.08</DOCUMENT>";
    assertThat(DartFilingOrigin.resolveOriginalReceipt(correction, List.of(old, other), xml))
        .isNull();
    assertThat(
            DartFilingOrigin.resolveOriginalReceipt(
                correction, List.of(old), "<DOCUMENT>no origin</DOCUMENT>"))
        .isNull();
  }
}
