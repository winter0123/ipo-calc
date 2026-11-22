package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KrxListingNoticeParserTest {

  @Test
  void parsesFourRealNotices() throws Exception {
    String[][] expected = {
      {"121850", "1995582", "2865", "2026-02-10", "2026-01-30", "제16회('26.01.30)"},
      {"092040", "14925374", "1164", "2026-02-26", "2026-02-12", "제20회('26.02.12)"},
      {"300120", "14500000", "1360", "2026-03-10", "2026-02-26", "제19회('26.02.26)"},
      {"082800", "10615000", "3065", "2026-03-19", "2026-03-05", "제47회('26.03.05)"}
    };
    String[] files = {"koiz.html", "amico.html", "raon.html", "vivo.html"};
    for (int i = 0; i < files.length; i++) {
      String html = readResource("/krx/notices/" + files[i]);
      KrxListingNotice notice = new KrxListingNoticeParser().parse(html);
      assertThat(notice.stockCode()).isEqualTo(expected[i][0]);
      assertThat(notice.issuedShares()).isEqualTo(Long.parseLong(expected[i][1]));
      assertThat(notice.issuePrice()).isEqualTo(Long.parseLong(expected[i][2]));
      assertThat(notice.listingDate()).isEqualTo(LocalDate.parse(expected[i][3]));
      assertThat(notice.issueDate()).isEqualTo(LocalDate.parse(expected[i][4]));
      assertThat(notice.roundLabel()).isEqualTo(expected[i][5]);
    }
  }

  @Test
  void parsesDifferentRoundNoticeWithLaterRoundAndListingDate() throws Exception {
    String html = readResource("/krx/notices/raon-other-round.html");

    KrxListingNotice notice = new KrxListingNoticeParser().parse(html);

    assertThat(notice.stockCode()).isEqualTo("300120");
    assertThat(notice.roundLabel()).isEqualTo("제20회('26.03.02)");
    assertThat(notice.listingDate()).isEqualTo(LocalDate.of(2026, 3, 24));
    assertThat(notice.issueDate()).isEqualTo(LocalDate.of(2026, 3, 2));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream in = KrxListingNoticeParserTest.class.getResourceAsStream(path)) {
      assertThat(in).as("fixture %s", path).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
