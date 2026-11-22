package com.ipocalc.ipocatalog.service.ipo.krx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 공식 KIND 상세검색에서 종목코드와 공고 게시일만으로 상장 공고를 발견한다. */
@Component
public class KrxListingNoticeDiscoveryClient {

  private static final String HOST = "https://kind.krx.co.kr";
  private static final int PAGE_SIZE = 100;
  private static final int MAX_PAGES = 50;
  private static final int MAX_BYTES = 1_000_000;
  private static final Pattern TABLE =
      Pattern.compile(
          "<table\\b[^>]*class\\s*=\\s*['\"][^'\"]*\\blist\\s+type-00\\b[^'\"]*['\"][^>]*>(.*?)</table>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern BODY =
      Pattern.compile("<tbody\\b[^>]*>(.*?)</tbody>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern ROW =
      Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern CELL =
      Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern RECEIPT =
      Pattern.compile("openDisclsViewer\\(\\s*['\"](\\d{14})['\"]");
  private static final Pattern CONTENT_OPTION =
      Pattern.compile("<option\\b[^>]*value\\s*=\\s*['\"](\\d{14})\\|([YN])['\"]",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DOCUMENT_URL =
      Pattern.compile("https://kind\\.krx\\.co\\.kr/external/\\d{4}/\\d{2}/\\d{2}/\\d{6}/\\d{14}/70791\\.htm");
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern SPACE = Pattern.compile("\\s+");
  private static final Pattern SHORT_CODE = Pattern.compile("[0-9][0-9A-Z]{5}");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  private final Transport transport;

  /** 운영 환경에서는 외부 리다이렉트를 허용하지 않는 고정 호스트 HTTP 클라이언트를 사용한다. */
  public KrxListingNoticeDiscoveryClient() {
    this(new KindHttpTransport());
  }

  KrxListingNoticeDiscoveryClient(Transport transport) {
    this.transport = transport;
  }

  /** 단일 KIND 요청을 교체할 수 있게 하여 페이지네이션·실패를 네트워크 없이 검증한다. */
  interface Transport {
    String request(String path, Map<String, String> parameters, boolean post);
  }

  /** KIND 공고의 게시일과 접수번호를 식별하며 상장일은 원문 파싱 후에만 확정한다. */
  public record Disclosure(
      String receiptNo,
      LocalDate publishedAt,
      String companyName,
      String title,
      String market,
      String issuerCode) {}

  /** 모든 검색 페이지의 파싱을 완료한 결과. */
  public record SearchResult(List<Disclosure> disclosures, int pagesRead) {}

  /**
   * KIND 공시 상세검색을 게시일 기준으로 전 페이지 조회한다.
   *
   * <p>한 페이지라도 실패하면 공고 없음으로 반환하지 않고 예외를 던진다.
   */
  public SearchResult find(String stockCode, LocalDate from, LocalDate to) {
    if (stockCode == null || !SHORT_CODE.matcher(stockCode).matches()) {
      throw new IllegalArgumentException("KIND 조회에는 정확한 6자리 종목코드가 필요합니다");
    }
    if (from == null || to == null || from.isAfter(to) || from.plusMonths(1).isBefore(to)) {
      throw new IllegalArgumentException("KIND 공고 게시일 범위는 최대 한 달입니다");
    }
    Map<String, Disclosure> unique = new LinkedHashMap<>();
    Set<String> pageFingerprints = new LinkedHashSet<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      Map<String, String> query = new LinkedHashMap<>();
      query.put("method", "searchDetailsSub");
      query.put("forward", "details_sub");
      query.put("currentPageSize", Integer.toString(PAGE_SIZE));
      query.put("pageIndex", Integer.toString(page));
      query.put("orderMode", "1");
      query.put("orderStat", "D");
      query.put("repIsuSrtCd", "A" + stockCode);
      query.put("reportNm", "상장");
      query.put("lastReport", "");
      query.put("bfrDsclsType", "on");
      query.put("fromDate", from.format(DATE));
      query.put("toDate", to.format(DATE));
      String html = transport.request("/disclosure/details.do", query, true);
      List<Disclosure> current = parsePage(html);
      String fingerprint = current.stream().map(Disclosure::receiptNo).toList().toString();
      if (!current.isEmpty() && !pageFingerprints.add(fingerprint)) {
        throw new IllegalStateException("KIND 페이지 번호가 적용되지 않아 중복 페이지가 반환됐습니다");
      }
      for (Disclosure item : current) {
        if (!item.publishedAt().isBefore(from)
            && !item.publishedAt().isAfter(to)
            && (item.title().contains("추가상장") || item.title().contains("신규상장")
                || item.title().contains("상장취소") || item.title().contains("상장정정"))) {
          unique.putIfAbsent(item.receiptNo(), item);
        }
      }
      if (current.size() < PAGE_SIZE) {
        return new SearchResult(List.copyOf(unique.values()), page);
      }
    }
    throw new IllegalStateException("KIND 공고가 최대 검색 페이지를 초과했습니다");
  }

  /** 검색으로 발견한 접수번호로 공식 70791 추가상장 원문 URL을 조회한다. */
  public String resolveAdditionalListingUrl(String receiptNo) {
    if (receiptNo == null || !receiptNo.matches("\\d{14}")) {
      throw new IllegalArgumentException("KIND 공고 접수번호가 유효하지 않습니다");
    }
    Map<String, String> shell = Map.of(
        "method", "search", "acptno", receiptNo, "docno", "",
        "viewerhost", "", "viewerport", "");
    String html = transport.request("/common/disclsviewer.do", shell, false);
    Matcher options = CONTENT_OPTION.matcher(html);
    List<String> current = new ArrayList<>();
    List<String> old = new ArrayList<>();
    while (options.find()) {
      if ("Y".equalsIgnoreCase(options.group(2))) {
        current.add(options.group(1));
      } else {
        old.add(options.group(1));
      }
    }
    current.addAll(old);
    if (current.isEmpty()) {
      throw new IllegalStateException("KIND 공고 본문 선택 옵션이 없습니다");
    }
    for (String contentId : current) {
      String resolved = transport.request(
          "/common/disclsviewer.do",
          Map.of("method", "searchContents", "docNo", contentId), true);
      Matcher match = DOCUMENT_URL.matcher(resolved);
      if (match.find()) {
        String url = match.group();
        KrxListingNoticeClient.validateNoticeUrl(url);
        if (!url.contains("/" + receiptNo.substring(0, 4) + "/"
            + receiptNo.substring(4, 6) + "/" + receiptNo.substring(6, 8) + "/"
            + receiptNo.substring(8) + "/")) {
          throw new IllegalStateException("KIND 공고 접수번호와 원문 경로가 다릅니다");
        }
        return url;
      }
    }
    throw new IllegalStateException("KIND 추가상장 원문 URL을 찾을 수 없습니다");
  }

  private static List<Disclosure> parsePage(String html) {
    if (html == null || html.isBlank()) {
      throw new IllegalStateException("KIND 공시 검색 응답이 비어 있습니다");
    }
    Matcher table = TABLE.matcher(html);
    if (!table.find()) {
      throw new IllegalStateException("KIND 공시 검색 테이블 구조가 변경됐습니다");
    }
    Matcher body = BODY.matcher(table.group(1));
    if (!body.find()) {
      throw new IllegalStateException("KIND 공시 검색 tbody 구조가 변경됐습니다");
    }
    List<Disclosure> results = new ArrayList<>();
    Matcher rows = ROW.matcher(body.group(1));
    while (rows.find()) {
      Matcher cells = CELL.matcher(rows.group(1));
      List<String> data = new ArrayList<>();
      while (cells.find()) {
        data.add(cells.group(1));
      }
      if (data.size() < 5) {
        continue;
      }
      String title = plain(data.get(3));
      Matcher receipt = RECEIPT.matcher(data.get(3));
      if (!receipt.find()) {
        throw new IllegalStateException("KIND 공시 검색 결과에서 접수번호를 읽을 수 없습니다");
      }
      String no = receipt.group(1);
      String issuer = "";
      Matcher company = Pattern.compile("companysummary_open\\(\\s*['\"]([0-9A-Za-z]+)")
          .matcher(data.get(2));
      if (company.find()) {
        issuer = company.group(1);
      }
      String market = "";
      Matcher alt = Pattern.compile("<img\\b[^>]*alt=['\"]([^'\"]+)").matcher(data.get(2));
      if (alt.find()) {
        market = alt.group(1);
      }
      results.add(new Disclosure(
          no, LocalDate.parse(no.substring(0, 4) + "-" + no.substring(4, 6) + "-"
              + no.substring(6, 8)),
          plain(data.get(2)), title, market, issuer));
    }
    return results;
  }

  private static String plain(String text) {
    return SPACE.matcher(TAG.matcher(text).replaceAll(" ")).replaceAll(" ").trim();
  }

  private static final class KindHttpTransport implements Transport {
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Override
    public String request(String path, Map<String, String> parameters, boolean post) {
      if (!path.equals("/disclosure/details.do")
          && !path.equals("/common/disclsviewer.do")) {
        throw new IllegalArgumentException("허용되지 않은 KIND 경로입니다");
      }
      String form = parameters.entrySet().stream()
          .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
              + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
          .reduce((left, right) -> left + "&" + right)
          .orElse("");
      String url = HOST + path + (post ? "" : "?" + form);
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(15))
          .header("User-Agent", "Mozilla/5.0")
          .header("Referer", HOST + "/");
      HttpRequest request = post
          ? builder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
              .POST(HttpRequest.BodyPublishers.ofString(form)).build()
          : builder.GET().build();
      try {
        HttpResponse<InputStream> response =
            client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
          response.body().close();
          throw new IllegalStateException("KIND 조회 HTTP " + response.statusCode());
        }
        try (InputStream input = response.body();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) {
              throw new IllegalStateException("KIND 조회 응답이 크기 제한을 초과했습니다");
            }
            bytes.write(buffer, 0, count);
          }
          return bytes.toString(StandardCharsets.UTF_8);
        }
      } catch (IOException e) {
        throw new IllegalStateException("KIND 조회 네트워크 실패: " + e.getClass().getSimpleName(), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("KIND 조회가 중단됐습니다", e);
      }
    }
  }
}
