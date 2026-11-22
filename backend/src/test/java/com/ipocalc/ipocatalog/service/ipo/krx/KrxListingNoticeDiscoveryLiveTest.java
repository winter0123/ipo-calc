package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/** 실제 KIND 서버를 읽기 전용으로 호출하며 URL 없이 네 건을 자동 발견하는 검증이다. */
@EnabledIfEnvironmentVariable(named = "KIND_DISCOVERY_LIVE", matches = "true")
class KrxListingNoticeDiscoveryLiveTest {

  private record Case(
      String name,
      String stockCode,
      LocalDate publishedAt,
      String receiptNo,
      LocalDate listingDate,
      String expectedContentId) {}

  @Test
  void findsAndParsesFourKnownNoticesUsingOnlyStockCodeAndPublicationRange() {
    List<Case> cases = List.of(
        new Case("코이즈", "121850", LocalDate.of(2026, 2, 5),
            "20260205001019", LocalDate.of(2026, 2, 10), "20260205002638"),
        new Case("아미코젠", "092040", LocalDate.of(2026, 2, 23),
            "20260223000883", LocalDate.of(2026, 2, 26), "20260223002329"),
        new Case("라온피플", "300120", LocalDate.of(2026, 3, 5),
            "20260305001391", LocalDate.of(2026, 3, 10), "20260305003200"),
        new Case("비보존제약", "082800", LocalDate.of(2026, 3, 16),
            "20260316001505", LocalDate.of(2026, 3, 19), "20260316004580"));

    var discovery = new KrxListingNoticeDiscoveryClient();
    var officialNoticeClient = new KrxListingNoticeClient(RestClient.builder());
    var parser = new KrxListingNoticeParser();
    for (Case c : cases) {
      var publicationSearch = discovery.find(
          c.stockCode(), c.publishedAt().minusDays(1), c.publishedAt().plusDays(1));
      var search = publicationSearch.disclosures().stream()
              .anyMatch(item -> c.receiptNo().equals(item.receiptNo()))
          ? publicationSearch
          : discovery.findViaStockIssue(
              c.stockCode(), c.publishedAt().minusDays(1), c.publishedAt().plusDays(1));
      System.out.printf("KIND_DISCOVERY_PATH stock=%s detail=%s fallback=%s%n",
          c.stockCode(),
          publicationSearch.disclosures().stream()
              .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo).toList(),
          search == publicationSearch ? "NOT_NEEDED"
              : search.disclosures().stream()
                  .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo).toList());
      var found = search.disclosures().stream()
          .filter(item -> c.receiptNo().equals(item.receiptNo()))
          .findFirst().orElseThrow(() -> new AssertionError(
              "KIND 자동 발견 실패: " + c.name() + " (" + c.stockCode()
                  + "), pages=" + search.pagesRead()
                  + ", receipts=" + search.disclosures().stream()
                      .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo).toList()));
      assertThat(found.publishedAt()).isEqualTo(c.publishedAt());
      String url = discovery.resolveAdditionalListingUrl(found.receiptNo());
      assertThat(url).contains("/" + c.expectedContentId() + "/70791.htm");
      var notice = parser.parse(officialNoticeClient.fetch(url), url);
      assertThat(notice.stockCode()).isEqualTo(c.stockCode());
      assertThat(notice.listingDate()).isEqualTo(c.listingDate());
      System.out.printf(
          "KIND_DISCOVERY_PROOF stock=%s from=%s to=%s receipt=%s content=%s "
              + "listing=%s pages=%d%n",
          c.stockCode(), c.publishedAt().minusDays(1), c.publishedAt().plusDays(1),
          found.receiptNo(), url, notice.listingDate(), search.pagesRead());
    }
  }
}
