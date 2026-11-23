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
  void discoversAndParsesThreeRealNewListingNoticesWithoutInputUrlsOrDatabaseWrites() {
    var discovery = new KrxListingNoticeDiscoveryClient();
    var official = new KrxListingNoticeClient(RestClient.builder());
    var parser = new KrxNewListingNoticeParser();
    List<NewCase> cases =
        List.of(
            new NewCase(
                "넥스아이",
                "493410",
                LocalDate.of(2026, 11, 10),
                "20261110001806",
                LocalDate.of(2026, 11, 12)),
            new NewCase(
                "이에스티",
                "495910",
                LocalDate.of(2026, 11, 2),
                "20261102001162",
                LocalDate.of(2026, 11, 4)),
            new NewCase(
                "크리에이츠",
                "439270",
                LocalDate.of(2026, 10, 28),
                "20261028001803",
                LocalDate.of(2026, 10, 30)));

    for (NewCase c : cases) {
      LocalDate from = c.publishedAt().minusDays(1);
      LocalDate to = c.publishedAt().plusDays(1);
      var search = discovery.find(c.stockCode(), from, to);
      var candidates =
          search.disclosures().stream()
              .filter(
                  notice ->
                      KrxListingNoticeDiscoveryClient.classifyListingTitle(notice.title())
                          == KrxListingNoticeDiscoveryClient.ListingType.NEW_LISTING)
              .toList();
      var receipts =
          search.disclosures().stream()
              .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
              .toList();
      assertThat(candidates)
          .as(
              "KIND 신규상장 후보 수 불일치: stock=%s, from=%s, to=%s, candidates=%s, receipts=%s",
              c.stockCode(),
              from,
              to,
              candidates.stream()
                  .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
                  .toList(),
              receipts)
          .hasSize(1);

      var found = candidates.get(0);
      // Compare expected receipt only AFTER candidate selection; never use a known URL.
      assertThat(found.receiptNo())
          .as("KIND 신규상장 접수번호: stock=%s", c.stockCode())
          .isEqualTo(c.expectedReceiptNo());
      assertThat(found.publishedAt())
          .as("KIND 게시일: stock=%s", c.stockCode())
          .isEqualTo(c.publishedAt());
      String discoveredUrl = discovery.resolveNewListingUrl(found.receiptNo());
      var evidence = parser.parse(official.fetchNewListing(discoveredUrl));
      assertThat(evidence.stockCode()).as(c.name()).isEqualTo(c.stockCode());
      assertThat(evidence.listingDate()).as(c.name()).isEqualTo(c.listingDate());
      System.out.printf(
          "KIND_NEW_LISTING_DISCOVERY_PROOF stock=%s receipt=%s listing=%s pages=%d%n",
          evidence.stockCode(), found.receiptNo(), evidence.listingDate(), search.pagesRead());
    }
  }

  private record NewCase(
      String name,
      String stockCode,
      LocalDate publishedAt,
      String expectedReceiptNo,
      LocalDate listingDate) {}

  @Test
  void findsAndParsesFourKnownNoticesUsingOnlyStockCodeAndPublicationRange() {
    List<Case> cases =
        List.of(
            new Case(
                "코이즈",
                "121850",
                LocalDate.of(2026, 2, 5),
                "20260205001019",
                LocalDate.of(2026, 2, 10),
                "20260205002638"),
            new Case(
                "아미코젠",
                "092040",
                LocalDate.of(2026, 2, 23),
                "20260223000883",
                LocalDate.of(2026, 2, 26),
                "20260223002329"),
            new Case(
                "라온피플",
                "300120",
                LocalDate.of(2026, 3, 5),
                "20260305001391",
                LocalDate.of(2026, 3, 10),
                "20260305003200"),
            new Case(
                "비보존제약",
                "082800",
                LocalDate.of(2026, 3, 16),
                "20260316001505",
                LocalDate.of(2026, 3, 19),
                "20260316004580"));

    var discovery = new KrxListingNoticeDiscoveryClient();
    var officialNoticeClient = new KrxListingNoticeClient(RestClient.builder());
    var parser = new KrxListingNoticeParser();
    for (Case c : cases) {
      var publicationSearch =
          discovery.find(c.stockCode(), c.publishedAt().minusDays(1), c.publishedAt().plusDays(1));
      var search =
          publicationSearch.disclosures().stream()
                  .anyMatch(item -> c.receiptNo().equals(item.receiptNo()))
              ? publicationSearch
              : discovery.findViaStockIssue(
                  c.stockCode(), c.publishedAt().minusDays(1), c.publishedAt().plusDays(1));
      System.out.printf(
          "KIND_DISCOVERY_PATH stock=%s detail=%s fallback=%s%n",
          c.stockCode(),
          publicationSearch.disclosures().stream()
              .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
              .toList(),
          search == publicationSearch
              ? "NOT_NEEDED"
              : search.disclosures().stream()
                  .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
                  .toList());
      var found =
          search.disclosures().stream()
              .filter(item -> c.receiptNo().equals(item.receiptNo()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "KIND 자동 발견 실패: "
                              + c.name()
                              + " ("
                              + c.stockCode()
                              + "), pages="
                              + search.pagesRead()
                              + ", receipts="
                              + search.disclosures().stream()
                                  .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
                                  .toList()));
      assertThat(found.publishedAt()).isEqualTo(c.publishedAt());
      String url = discovery.resolveAdditionalListingUrl(found.receiptNo());
      assertThat(url).contains("/" + c.expectedContentId() + "/70791.htm");
      var notice = parser.parse(officialNoticeClient.fetch(url), url);
      assertThat(notice.stockCode()).isEqualTo(c.stockCode());
      assertThat(notice.listingDate()).isEqualTo(c.listingDate());
      System.out.printf(
          "KIND_DISCOVERY_PROOF stock=%s from=%s to=%s receipt=%s content=%s "
              + "listing=%s pages=%d%n",
          c.stockCode(),
          c.publishedAt().minusDays(1),
          c.publishedAt().plusDays(1),
          found.receiptNo(),
          url,
          notice.listingDate(),
          search.pagesRead());
    }
  }
}
