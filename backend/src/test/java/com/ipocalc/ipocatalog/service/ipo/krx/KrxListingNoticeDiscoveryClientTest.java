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
  void findsOfficialReceiptWithoutProvidingNoticeUrl() {
    var client = new KrxListingNoticeDiscoveryClient(
        (path, params, post) -> {
          assertThat(path).isEqualTo("/disclosure/details.do");
          assertThat(post).isTrue();
          assertThat(params).containsEntry("repIsuSrtCd", "A121850")
              .containsEntry("lastReport", "")
              .containsEntry("fromDate", "2026-02-05")
              .containsEntry("toDate", "2026-02-05")
              .containsEntry("pageIndex", "1");
          return page(List.of(RECEIPT));
        });

    var result = client.find("121850", DAY, DAY);

    assertThat(result.pagesRead()).isEqualTo(1);
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
    var client = new KrxListingNoticeDiscoveryClient(
        (path, params, post) -> {
          calls.incrementAndGet();
          return "1".equals(params.get("pageIndex")) ? page(first) : page(second);
        });

    var result = client.find("121850", DAY, DAY);

    assertThat(result.pagesRead()).isEqualTo(2);
    assertThat(calls).hasValue(2);
    assertThat(result.disclosures()).hasSize(101);
    assertThat(result.disclosures().stream().map(
        KrxListingNoticeDiscoveryClient.Disclosure::receiptNo).distinct().count())
        .isEqualTo(101);
  }

  @Test
  void failedSecondPageDoesNotBecomeNoNoticeResult() {
    List<String> first = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      first.add("20260205" + String.format("%06d", i));
    }
    var client = new KrxListingNoticeDiscoveryClient(
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
    var client = new KrxListingNoticeDiscoveryClient(
        (path, params, post) -> page(first));

    assertThatThrownBy(() -> client.find("121850", DAY, DAY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("중복 페이지");
  }

  @Test
  void missingTableIsFailureButCompleteEmptyPageIsNoNotice() {
    var broken = new KrxListingNoticeDiscoveryClient(
        (path, params, post) -> "<html><body>접속 제한</body></html>");
    assertThatThrownBy(() -> broken.find("121850", DAY, DAY))
        .hasMessageContaining("테이블 구조");

    var empty = new KrxListingNoticeDiscoveryClient(
        (path, params, post) -> page(List.of()));
    assertThat(empty.find("121850", DAY, DAY).disclosures()).isEmpty();
  }

  @Test
  void resolvesDiscoveredReceiptThroughOfficialViewer() {
    List<String> requests = new ArrayList<>();
    var client = new KrxListingNoticeDiscoveryClient(
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
    var client = new KrxListingNoticeDiscoveryClient(
        (path, params, post) -> "search".equals(params.get("method"))
            ? "<option value='20260205002638|Y'>추가상장</option>"
            : "https://kind.krx.co.kr/external/2026/02/05/000999/"
                + "20260205002638/70791.htm");
    assertThatThrownBy(() -> client.resolveAdditionalListingUrl(RECEIPT))
        .hasMessageContaining("접수번호와 원문 경로");
  }

  @Test
  void refusesInvalidStockCodeAndInvalidDateRangeBeforeNetwork() {
    var client = new KrxListingNoticeDiscoveryClient(
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

  private static String page(List<String> receipts) {
    StringBuilder html = new StringBuilder(
        "<html><table class='list type-00 mt10'><tbody>");
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
