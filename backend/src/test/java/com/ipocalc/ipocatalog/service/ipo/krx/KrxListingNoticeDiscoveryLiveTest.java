package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
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

  /** 검색 접수번호와 원문 ID는 별도 식별자다. 각 사례를 독립 DynamicTest로 실행한다. */
  @TestFactory
  Stream<DynamicTest> discoversThreeNewListingsIndependentlyFromStockAndDate() {
    List<NewCase> cases =
        List.of(
            new NewCase(
                "넥스아이",
                "493410",
                LocalDate.of(2026, 11, 10),
                "20261110000840",
                "20261110001806",
                LocalDate.of(2026, 11, 12)),
            new NewCase(
                "이에스티",
                "495910",
                LocalDate.of(2026, 11, 2),
                null,
                "20261102001162",
                LocalDate.of(2026, 11, 4)),
            new NewCase(
                "크리에이츠",
                "439270",
                LocalDate.of(2026, 10, 28),
                null,
                "20261028001803",
                LocalDate.of(2026, 10, 30)));
    return cases.stream()
        .map(
            c ->
                DynamicTest.dynamicTest(
                    c.name(),
                    () -> {
                      var discovery = new KrxListingNoticeDiscoveryClient();
                      var official = new KrxListingNoticeClient(RestClient.builder());
                      var parser = new KrxNewListingNoticeParser();
                      LocalDate from = c.publishedAt().minusDays(1);
                      LocalDate to = c.publishedAt().plusDays(1);
                      var search = discovery.find(c.stockCode(), from, to);
                      var candidates =
                          search.disclosures().stream()
                              .filter(
                                  notice ->
                                      KrxListingNoticeDiscoveryClient.classifyListingTitle(
                                              notice.title())
                                          == KrxListingNoticeDiscoveryClient.ListingType
                                              .NEW_LISTING)
                              .toList();
                      assertThat(candidates)
                          .as(
                              "stock=%s from=%s to=%s receipts=%s",
                              c.stockCode(),
                              from,
                              to,
                              search.disclosures().stream()
                                  .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
                                  .toList())
                          .hasSize(1);
                      var found = candidates.get(0);
                      String url = discovery.resolveNewListingUrl(found.receiptNo());
                      var evidence = parser.parse(official.fetchNewListing(url));
                      if (c.knownSearchReceipt() != null) {
                        assertThat(found.receiptNo()).isEqualTo(c.knownSearchReceipt());
                      }
                      // 실제 외부 URL의 중간 경로에는 검색 접수번호의 뒤 6자리가,
                      // 원문 폴더에는 뷰어가 선택한 별도 content ID가 있어야 한다.
                      assertThat(url).contains("/" + found.receiptNo().substring(8) + "/");
                      assertThat(url).contains("/" + c.knownContentId() + "/70760.htm");
                      assertThat(found.publishedAt()).isEqualTo(c.publishedAt());
                      assertThat(evidence.stockCode()).isEqualTo(c.stockCode());
                      assertThat(evidence.listingDate()).isEqualTo(c.listingDate());
                      System.out.printf(
                          "KIND_NEW_LISTING_DISCOVERY_PROOF stock=%s receipt=%s content=%s listing=%s pages=%d%n",
                          c.stockCode(),
                          found.receiptNo(),
                          c.knownContentId(),
                          evidence.listingDate(),
                          search.pagesRead());
                    }));
  }

  private record NewCase(
      String name,
      String stockCode,
      LocalDate publishedAt,
      String knownSearchReceipt,
      String knownContentId,
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
