package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KrxListingNoticeDiscoveryClientTest {

  private static final LocalDate DAY = LocalDate.of(2026, 2, 5);
  private static final String RECEIPT = "20260205001019";

  @Test
  void classifiesSyntheticAmendmentsAndIgnoresUnrelatedCompanyDisclosures() {
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("추가상장"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.ADDITIONAL);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("[기재정정] 추가상장"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.REVISION);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("상장 철회"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.REVISION);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("상장 변경"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.REVISION);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("상장 일정 변경"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.REVISION);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("신규 상장"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.NEW_LISTING);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("사업보고서 상장 철회 언급"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.OTHER);
    assertThat(KrxListingNoticeDiscoveryClient.classifyListingTitle("감사보고서"))
        .isEqualTo(KrxListingNoticeDiscoveryClient.ListingType.OTHER);
  }

  @Test
  void findsOfficialReceiptWithoutProvidingNoticeUrl() {
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              assertThat(path).isEqualTo("/disclosure/details.do");
              assertThat(post).isTrue();
              assertThat(params)
                  .containsEntry("repIsuSrtCd", "A121850")
                  .containsEntry("lastReport", "")
                  .containsEntry("reportNm", "")
                  .containsEntry("fromDate", "2026-02-05")
                  .containsEntry("toDate", "2026-02-05");
              return page("1".equals(params.get("pageIndex")) ? List.of(RECEIPT) : List.of());
            });

    var result = client.find("121850", DAY, DAY);

    assertThat(result.pagesRead()).isEqualTo(2);
    assertThat(result.disclosures()).hasSize(1);
    assertThat(result.disclosures().get(0).receiptNo()).isEqualTo(RECEIPT);
    assertThat(result.disclosures().get(0).publishedAt()).isEqualTo(DAY);
    assertThat(result.disclosures().get(0).title()).isEqualTo("추가상장");
  }

  @Test
  void readsSecondPageAndDeduplicatesByOfficialReceipt() {
    AtomicInteger calls = new AtomicInteger();
    List<String> first = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      first.add("20260205" + String.format("%06d", i));
    }
    List<String> second = List.of(first.get(99), RECEIPT);
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              calls.incrementAndGet();
              return page(
                  "1".equals(params.get("pageIndex"))
                      ? first
                      : "2".equals(params.get("pageIndex")) ? second : List.of());
            });

    var result = client.find("121850", DAY, DAY);

    assertThat(result.pagesRead()).isEqualTo(3);
    assertThat(calls).hasValue(3);
    assertThat(result.disclosures()).hasSize(101);
    assertThat(
            result.disclosures().stream()
                .map(KrxListingNoticeDiscoveryClient.Disclosure::receiptNo)
                .distinct()
                .count())
        .isEqualTo(101);
  }

  @Test
  void failedSecondPageDoesNotBecomeNoNoticeResult() {
    List<String> first = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      first.add("20260205" + String.format("%06d", i));
    }
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("2".equals(params.get("pageIndex"))) {
                throw new IllegalStateException("KIND 조회 HTTP 429");
              }
              return page(first);
            });

    assertThatThrownBy(() -> client.find("121850", DAY, DAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("429");
  }

  @Test
  void repeatedFirstPageMustNotBeMisreadAsAllPages() {
    List<String> first = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      first.add("20260205" + String.format("%06d", i));
    }
    var client = new KrxListingNoticeDiscoveryClient((path, params, post) -> page(first));

    assertThatThrownBy(() -> client.find("121850", DAY, DAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("중복 페이지");
  }

  @Test
  void missingTableIsFailureButCompleteEmptyPageIsNoNotice() {
    var broken =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> "<html><body>접속 제한</body></html>");
    assertThatThrownBy(() -> broken.find("121850", DAY, DAY)).hasMessageContaining("테이블 구조");

    var empty = new KrxListingNoticeDiscoveryClient((path, params, post) -> page(List.of()));
    assertThat(empty.find("121850", DAY, DAY).disclosures()).isEmpty();
  }

  @Test
  void malformedDataRowCannotTurnHundredRowsIntoFalseLastPage() {
    List<String> receipts = new ArrayList<>();
    for (int i = 1; i <= 99; i++) {
      receipts.add("20260205" + String.format("%06d", i));
    }
    String incomplete =
        page(receipts).replace("</tbody>", "<tr><td>09:00</td><td>누락된 데이터 행</td></tr></tbody>");
    var client = new KrxListingNoticeDiscoveryClient((path, params, post) -> incomplete);
    assertThatThrownBy(() -> client.find("121850", DAY, DAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("열이 누락");
  }

  @Test
  void shortPageWithMoreResultsIsNotTrustedAsTerminal() {
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) ->
                page(
                    "1".equals(params.get("pageIndex"))
                        ? List.of(RECEIPT)
                        : List.of("20260205001020")));
    assertThatThrownBy(() -> client.find("121850", DAY, DAY)).hasMessageContaining("페이지 완결성");
  }

  @Test
  void shortPageMustObserveNextPageAndRejectHttpFailure() {
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("2".equals(params.get("pageIndex"))) {
                throw new IllegalStateException("HTTP 403 on page 2");
              }
              return page(List.of(RECEIPT));
            });
    assertThatThrownBy(() -> client.find("121850", DAY, DAY)).hasMessageContaining("HTTP 403");
  }

  @Test
  void resolvesNewListingViewerAndRejectsMismatchedContentId() {
    // 검색 결과의 접수번호 00840과 뷰어 원문 ID 01806은 서로 다른 값이다.
    String receipt = "20261110000840";
    String official = "https://kind.krx.co.kr/external/2026/11/10/000840/20261110001806/70760.htm";
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) ->
                "search".equals(params.get("method"))
                    ? "<option value='20261110001806|Y'>신규상장</option>"
                    : official);
    assertThat(client.resolveNewListingUrl(receipt)).isEqualTo(official);
    assertThatThrownBy(() -> client.resolveAdditionalListingUrl(receipt))
        .isInstanceOf(IllegalStateException.class);

    var mismatch =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) ->
                "search".equals(params.get("method"))
                    ? "<option value='20261110001806|Y'>신규상장</option>"
                    : official.replace("20261110001806/70760.htm", "20261110001999/70760.htm"));
    assertThatThrownBy(() -> mismatch.resolveNewListingUrl(receipt)).hasMessageContaining("원문 경로");

    var wrongReceipt =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) ->
                "search".equals(params.get("method"))
                    ? "<option value='20261110001806|Y'>신규상장</option>"
                    : official);
    assertThatThrownBy(() -> wrongReceipt.resolveNewListingUrl("20261110001806"))
        .hasMessageContaining("접수번호와 원문 경로");
  }

  @Test
  void resolvesDiscoveredReceiptThroughOfficialViewer() {
    List<String> requests = new ArrayList<>();
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              requests.add(params.get("method"));
              if ("search".equals(params.get("method"))) {
                assertThat(params).containsEntry("acptno", RECEIPT);
                return "<select><option value='20260205002638|Y'>추가상장</option></select>";
              }
              assertThat(params).containsEntry("docNo", "20260205002638");
              return "https://kind.krx.co.kr/external/2026/02/05/001019/"
                  + "20260205002638/70791.htm";
            });

    String url = client.resolveAdditionalListingUrl(RECEIPT);

    assertThat(url).endsWith("/001019/20260205002638/70791.htm");
    assertThat(requests).containsExactly("search", "searchContents");
  }

  @Test
  void refusesReceiptAndDocumentPathMismatch() {
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) ->
                "search".equals(params.get("method"))
                    ? "<option value='20260205002638|Y'>추가상장</option>"
                    : "https://kind.krx.co.kr/external/2026/02/05/000999/"
                        + "20260205002638/70791.htm");
    assertThatThrownBy(() -> client.resolveAdditionalListingUrl(RECEIPT))
        .hasMessageContaining("접수번호와 원문 경로");
  }

  @Test
  void refusesInvalidStockCodeAndInvalidDateRangeBeforeNetwork() {
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              throw new AssertionError("request must not be sent");
            });
    assertThatThrownBy(() -> client.find("코이즈", DAY, DAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.find("121850", DAY.plusDays(1), DAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.find("121850", DAY, DAY.plusMonths(2)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stockIssuanceFallbackSearchesByListingWindowButFiltersByPublicationDay() {
    AtomicInteger queriedMonths = new AtomicInteger();
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("/disclosure/details.do".equals(path)) {
                assertThat(params)
                    .containsEntry("repIsuSrtCd", "A121850")
                    .containsEntry("reportNm", "");
                return page(List.of(RECEIPT)).replace("12185", "27957");
              }
              assertThat(path).isEqualTo("/corpgeneral/stockissuelist.do");
              assertThat(params)
                  .containsEntry("isurCd", "27957")
                  .containsEntry("listingType", "2")
                  .containsEntry("marketType", "all");
              queriedMonths.incrementAndGet();
              return "2026-02-01".equals(params.get("fromDate"))
                  ? stockIssuePage(List.of(RECEIPT, "20260101000001"))
                  : stockIssuePage(List.of());
            });

    var result = client.findViaStockIssue("121850", DAY, DAY);

    assertThat(result.disclosures()).hasSize(1);
    assertThat(result.disclosures().get(0).receiptNo()).isEqualTo(RECEIPT);
    assertThat(result.pagesRead()).isEqualTo(queriedMonths.get());
    assertThat(queriedMonths.get()).isGreaterThan(1);
  }

  @Test
  void stockIssuanceFallbackPageTwoFailureIsNotSilentlyIgnored() {
    List<String> first = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      first.add("20260205" + String.format("%06d", i));
    }
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("/disclosure/details.do".equals(path)) {
                return page(List.of(RECEIPT)).replace("12185", "27957");
              }
              assertThat(params).containsEntry("isurCd", "27957");
              if ("2026-02-01".equals(params.get("fromDate"))) {
                if ("2".equals(params.get("pageIndex"))) {
                  throw new IllegalStateException("KIND page 2 timed out");
                }
                return stockIssuePage(first);
              }
              return stockIssuePage(List.of());
            });
    assertThatThrownBy(() -> client.findViaStockIssue("121850", DAY, DAY))
        .hasMessageContaining("page 2 timed out");
  }

  @Test
  void unknownKindIssuerCodeIsFetchFailureNotNoNotices() {
    var client =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              assertThat(path).isEqualTo("/disclosure/details.do");
              return page(List.of());
            });

    assertThatThrownBy(() -> client.findViaStockIssue("121850", DAY, DAY))
        .hasMessageContaining("회사 식별자를 찾을 수 없습니다");
  }

  private static String stockIssuePage(List<String> receipts) {
    StringBuilder html = new StringBuilder("<table class='list type-00'><tbody>");
    for (String receipt : receipts) {
      html.append("<tr onclick=\"fnDetailView('")
          .append(receipt)
          .append("')\">")
          .append("<td><a onclick=\"companysummary_open('12185')\">코이즈</a></td>")
          .append("<td>2026-02-10</td><td>추가상장</td>")
          .append("<td>1,995,582</td><td>500</td><td>유상증자</td></tr>");
    }
    return html.append("</tbody></table>").toString();
  }

  private static String page(List<String> receipts) {
    StringBuilder html = new StringBuilder("<html><table class='list type-00 mt10'><tbody>");
    for (String receipt : receipts) {
      html.append("<tr><td>1</td><td>09:00</td>")
          .append("<td><a onclick=\"companysummary_open('12185')\">코이즈</a></td>")
          .append("<td><a onclick=\"openDisclsViewer('")
          .append(receipt)
          .append("','')\">추가상장</a></td><td>거래소</td></tr>");
    }
    return html.append("</tbody></table></html>").toString();
  }
}
