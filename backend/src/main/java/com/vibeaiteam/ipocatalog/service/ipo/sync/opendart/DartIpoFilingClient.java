package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport;
import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenDART 공모 공시 조회 클라이언트.
 *
 * <p>공시 목록(list.json)에서 최신 투자설명서·증권신고서를 찾고, 원문(document.xml)과 증권신고서 주요정보(estkRs.json)를
 * 가져온다. 키 없음·형식 오류·HTTP 오류·status≠000은 모두 {@link Optional#empty()}이며, 로그·예외에 crtfc_key가 든 URL을
 * 남기지 않는다.
 */
public class DartIpoFilingClient {

  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int PAGE_COUNT = 100;
  private static final int MAX_PAGES = 5; // 1년치 한 회사 공시는 이를 넘지 않는다
  private static final long MAX_RESPONSE_BYTES = 50L * 1024 * 1024; // 응답 50MB
  private static final long MAX_UNZIPPED_BYTES = 80L * 1024 * 1024; // 압축 해제 합계 80MB
  private static final String PROSPECTUS = "투자설명서";
  private static final String EQUITY_REPORT = "증권신고서(지분증권)";

  private final RestClient restClient;
  private final OpenDartApiProperties properties;
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final long maxUnzippedBytes;
  private final RequestThrottle throttle;

  /** 접수된 공시 한 건. */
  public record FilingRef(String rceptNo, String reportName, LocalDate receivedOn) {}

  /** 인수인 한 곳. */
  public record Underwriter(String name, String role, Long shares) {}

  /** 증권신고서 주요정보 중 액면가와 인수인. */
  public record EquityOffering(Long parValue, List<Underwriter> underwriters) {}

  /** 생성자 (테스트용 - RestClient 직접 주입). */
  public DartIpoFilingClient(
      RestClient restClient, OpenDartApiProperties properties, RequestThrottle throttle) {
    this(restClient, properties, throttle, MAX_UNZIPPED_BYTES);
  }

  /** 압축 해제 한도를 바꾸는 생성자 (테스트용 - 큰 메모리 없이 zip bomb 경로를 확인한다). */
  DartIpoFilingClient(
      RestClient restClient,
      OpenDartApiProperties properties,
      RequestThrottle throttle,
      long maxUnzippedBytes) {
    this.restClient = restClient;
    this.properties = properties;
    this.throttle = throttle;
    this.maxUnzippedBytes = maxUnzippedBytes;
  }

  /** 생성자 (운영용 - 문서가 수 MB라 읽기 타임아웃을 길게 잡는다). */
  public DartIpoFilingClient(
      RestClient.Builder restClientBuilder,
      OpenDartApiProperties properties,
      RequestThrottle throttle,
      int connectTimeoutMs,
      int readTimeoutMs) {
    this(
        restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .requestFactory(HttpClientSupport.requestFactory(connectTimeoutMs, readTimeoutMs))
            .build(),
        properties,
        throttle);
  }

  /** 최근 1년 공시 중 최신 투자설명서, 없으면 최신 증권신고서(지분증권)를 찾는다. */
  public synchronized Optional<FilingRef> findLatestOfferingDocument(
      String corpCode, LocalDate today) {
    if (!usable(corpCode)) {
      return Optional.empty();
    }
    List<FilingRef> all = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      int pageNo = page;
      Optional<JsonNode> json =
          getJson(
              b ->
                  b.path("/api/list.json")
                      .queryParam("corp_code", corpCode)
                      .queryParam("bgn_de", today.minusDays(365).format(DATE))
                      .queryParam("end_de", today.format(DATE))
                      .queryParam("page_no", pageNo)
                      .queryParam("page_count", PAGE_COUNT));
      if (json.isEmpty()) {
        break;
      }
      for (JsonNode item : json.get().path("list")) {
        toFiling(item).ifPresent(all::add);
      }
      if (pageNo >= json.get().path("total_page").asInt(1)) {
        break;
      }
    }
    Comparator<FilingRef> latest =
        Comparator.comparing(FilingRef::receivedOn).thenComparing(FilingRef::rceptNo);
    return pickLatest(all, PROSPECTUS, latest).or(() -> pickLatest(all, EQUITY_REPORT, latest));
  }

  private static Optional<FilingRef> pickLatest(
      List<FilingRef> all, String keyword, Comparator<FilingRef> latest) {
    return all.stream().filter(f -> f.reportName().contains(keyword)).max(latest);
  }

  private static Optional<FilingRef> toFiling(JsonNode item) {
    try {
      return Optional.of(
          new FilingRef(
              item.path("rcept_no").asString(),
              item.path("report_nm").asString(),
              LocalDate.parse(item.path("rcept_dt").asString(), DATE)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /** 공시 원문 ZIP에서 가장 큰 항목을 UTF-8 문자열로 돌려준다. 크기 한도를 넘으면 포기한다. */
  public synchronized Optional<String> downloadDocument(String rceptNo) {
    if (properties.isApiKeyMissing() || rceptNo == null || !rceptNo.matches("\\d{14}")) {
      return Optional.empty();
    }
    byte[] body;
    try {
      throttle.acquire();
      body =
          restClient
              .get()
              .uri(
                  b ->
                      b.path("/api/document.xml")
                          .queryParam("crtfc_key", properties.getApiKey())
                          .queryParam("rcept_no", rceptNo)
                          .build())
              .retrieve()
              .body(byte[].class);
    } catch (RestClientException e) {
      return Optional.empty(); // 메시지에 crtfc_key가 든 URL이 있어 남기지 않는다
    }
    // ZIP 시그니처("PK")가 아니면 JSON 오류 응답이다
    if (body == null || body.length > MAX_RESPONSE_BYTES || body.length < 2 || body[0] != 'P'
        || body[1] != 'K') {
      return Optional.empty();
    }
    return largestEntry(body).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
  }

  private Optional<byte[]> largestEntry(byte[] zip) {
    byte[] largest = null;
    long total = 0;
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        // 항목 이름은 쓰지 않는다(디스크에 저장하지 않으므로 경로 조작이 무의미)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
          total += n;
          if (total > maxUnzippedBytes) {
            return Optional.empty(); // zip bomb
          }
          out.write(buf, 0, n);
        }
        if (largest == null || out.size() > largest.length) {
          largest = out.toByteArray();
        }
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    return Optional.ofNullable(largest);
  }

  /** 증권신고서 주요정보(estkRs)에서 액면가와 인수인을 읽는다. */
  public synchronized Optional<EquityOffering> fetchEquityOffering(
      String corpCode, LocalDate today) {
    if (!usable(corpCode)) {
      return Optional.empty();
    }
    return getJson(
            b ->
                b.path("/api/estkRs.json")
                    .queryParam("corp_code", corpCode)
                    .queryParam("bgn_de", today.minusDays(365).format(DATE))
                    .queryParam("end_de", today.format(DATE)))
        .map(
            json -> {
              Long parValue = null;
              List<Underwriter> underwriters = new ArrayList<>();
              for (JsonNode group : json.path("group")) {
                String title = group.path("title").asString();
                JsonNode first = group.path("list").path(0);
                if ("증권의종류".equals(title) && parValue == null) {
                  parValue = toLong(first.path("fv"));
                } else if ("인수인정보".equals(title)) {
                  for (JsonNode u : group.path("list")) {
                    underwriters.add(
                        new Underwriter(
                            u.path("actnmn").asString(null),
                            u.path("actsen").asString(null),
                            toLong(u.path("udtcnt"))));
                  }
                }
              }
              return new EquityOffering(parValue, underwriters);
            });
  }

  /** 쉼표를 지우고 숫자로 바꾼다. "-"·빈 값·숫자 아님은 null. */
  private static Long toLong(JsonNode node) {
    String text = node.asString("").replace(",", "").trim();
    return text.matches("-?\\d+") ? Long.valueOf(text) : null;
  }

  private boolean usable(String corpCode) {
    return !properties.isApiKeyMissing() && corpCode != null && corpCode.matches("\\d{8}");
  }

  /** JSON API를 호출해 status 000인 응답만 돌려준다. */
  private Optional<JsonNode> getJson(
      Function<org.springframework.web.util.UriBuilder, org.springframework.web.util.UriBuilder>
          params) {
    try {
      throttle.acquire();
      String body =
          restClient
              .get()
              .uri(b -> params.apply(b).queryParam("crtfc_key", properties.getApiKey()).build())
              .retrieve()
              .body(String.class);
      if (body == null) {
        return Optional.empty();
      }
      JsonNode json = objectMapper.readTree(body);
      return "000".equals(json.path("status").asString()) ? Optional.of(json) : Optional.empty();
    } catch (RestClientException | JacksonException e) {
      return Optional.empty(); // 메시지에 crtfc_key가 든 URL이 있어 남기지 않는다
    }
  }
}
