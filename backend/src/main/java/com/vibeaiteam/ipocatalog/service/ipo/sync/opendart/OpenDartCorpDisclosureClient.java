package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartCompanyOverview;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport;
import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenDART DS001 공시검색 클라이언트.
 *
 * <p>최근 N일 내 "증권신고서(지분증권)" 제출 회사 목록을 조회한다. 같은 {@code corp_code}의 정정·발행조건확정 공시는 접두어([기재정정],
 * [발행조건확정]) 제거 후 하나로 합치고, {@code rcept_dt}가 가장 최신인 항목을 채택한다.
 */
public class OpenDartCorpDisclosureClient {

  private static final String ENDPOINT_PATH = "/api/list.json";
  private static final String REPORT_TYPE_FILTER = "C001"; // 증권신고서-지분증권
  private static final int PAGE_COUNT = 100; // 페이지당 최대 건수

  private final RestClient restClient;
  private final OpenDartApiProperties properties;
  private final Clock clock;
  private final RequestThrottle throttle;

  /** 생성자 (테스트용 - RestClient 직접 주입). */
  public OpenDartCorpDisclosureClient(
      RestClient restClient,
      OpenDartApiProperties properties,
      Clock clock,
      RequestThrottle throttle) {
    this.restClient = restClient;
    this.properties = properties;
    this.clock = clock;
    this.throttle = throttle;
  }

  /** 생성자 (운영용 - RestClient.Builder로 생성, 타임아웃 설정 포함). */
  public OpenDartCorpDisclosureClient(
      RestClient.Builder restClientBuilder,
      OpenDartApiProperties properties,
      Clock clock,
      RequestThrottle throttle) {
    this(
        restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(HttpClientSupport.requestFactory())
            .build(),
        properties,
        clock,
        throttle);
  }

  /**
   * 최근 지정된 일수 내 지분증권 증권신고서 제출 회사 목록을 조회한다.
   *
   * @param daysBack 조회할 과거 일수 (예: 7 → 최근 7일)
   * @return 필터링 및 디듀프된 {@link OpenDartDisclosureItem} 리스트
   * @throws IllegalStateException API 키가 설정되지 않은 경우
   * @throws OpenDartAuthenticationException 인증키 오류(status 011)인 경우
   * @throws OpenDartApiException 기타 API 오류인 경우
   */
  public List<OpenDartDisclosureItem> searchRecentEquitySecuritiesCompanies(int daysBack) {
    validateApiKey();
    LocalDate endDate = LocalDate.now(clock);
    LocalDate startDate = endDate.minusDays(daysBack);
    return searchByDateRange(startDate, endDate);
  }

  /**
   * 지정된 기간 내 지분증권 증권신고서 제출 회사 목록을 조회한다.
   *
   * @param startDate 시작일 (포함)
   * @param endDate 종료일 (포함)
   * @return 필터링 및 디듀프된 {@link OpenDartDisclosureItem} 리스트
   */
  public List<OpenDartDisclosureItem> searchByDateRange(LocalDate startDate, LocalDate endDate) {
    validateApiKey();

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    String bgnDe = startDate.format(formatter);
    String endDe = endDate.format(formatter);

    List<OpenDartDisclosureItem> allItems = new ArrayList<>();
    int pageNo = 1;
    int totalPages = 1;

    do {
      throttle.acquire();
      int currentPage = pageNo;
      OpenDartListResponse response;
      try {
        response =
            restClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path(ENDPOINT_PATH)
                            .queryParam("crtfc_key", properties.getApiKey())
                            .queryParam("bgn_de", bgnDe)
                            .queryParam("end_de", endDe)
                            .queryParam("pblntf_detail_ty", REPORT_TYPE_FILTER)
                            .queryParam("page_no", currentPage)
                            .queryParam("page_count", PAGE_COUNT)
                            .build())
                .retrieve()
                .body(OpenDartListResponse.class);
      } catch (RestClientException e) {
        // 예외 메시지에는 crtfc_key가 담긴 요청 URL이 들어가므로 원인 예외를 잇지 않고 클래스명만 남긴다.
        throw new OpenDartApiException(
            "http_error", "OpenDART API 호출 실패: " + e.getClass().getSimpleName());
      }

      if (response == null) {
        throw new OpenDartApiException("null", "OpenDART API 응답이 null입니다.");
      }

      handleStatus(response.status(), response.message());

      if ("013".equals(response.status())) {
        // 조회된 데이터 없음 - 정상 처리
        break;
      }

      if (response.list() == null) {
        break;
      }

      allItems.addAll(response.list());
      totalPages = response.total_page();
      pageNo++;

    } while (pageNo <= totalPages);

    // 지분증권 증권신고서만 필터링 + 정정/발행조건확정 디듀프
    return filterAndDeduplicate(allItems);
  }

  /**
   * 기업개황(대표이사·설립일·주소)을 조회한다. 키가 없거나 호출이 실패하면 빈 값이다(상세 화면 보조 정보라 예외를 올리지 않는다).
   *
   * @param corpCode DART 고유번호(8자리)
   * @return 기업개황
   */
  public Optional<OpenDartCompanyOverview> getCompanyOverview(String corpCode) {
    if (properties.isApiKeyMissing() || corpCode == null || !corpCode.matches("\\d{8}")) {
      return Optional.empty();
    }
    try {
      throttle.acquire();
      OpenDartCompanyOverview overview =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/company.json")
                          .queryParam("crtfc_key", properties.getApiKey())
                          .queryParam("corp_code", corpCode)
                          .build())
              .retrieve()
              .body(OpenDartCompanyOverview.class);
      return Optional.ofNullable(overview).filter(o -> "000".equals(o.status()));
    } catch (RestClientException e) {
      // 예외 메시지에 crtfc_key가 담긴 URL이 들어가므로 남기지 않는다.
      return Optional.empty();
    }
  }

  private void validateApiKey() {
    if (properties.isApiKeyMissing()) {
      throw new IllegalStateException("OpenDART API 키가 설정되지 않았습니다. OPENDART_API_KEY 환경변수를 확인하세요.");
    }
  }

  private void handleStatus(String status, String message) {
    if ("000".equals(status)) {
      return; // 정상
    }
    if ("013".equals(status)) {
      return; // 데이터 없음 - 정상 처리
    }
    if ("010".equals(status)
        || "011".equals(status)
        || "012".equals(status)
        || "901".equals(status)) {
      throw new OpenDartAuthenticationException(status, message);
    }
    throw new OpenDartApiException(status, message);
  }

  /** 지분증권 증권신고서만 필터링하고, 같은 corp_code의 정정/발행조건확정 공시를 최신 1건으로 디듀프. */
  private List<OpenDartDisclosureItem> filterAndDeduplicate(List<OpenDartDisclosureItem> items) {
    // 1단계: "증권신고서(지분증권)" 포함 항목만 필터링 (정정/확정 공시도 정규화 후 포함)
    List<OpenDartDisclosureItem> filtered =
        items.stream().filter(OpenDartDisclosureItem::isEquitySecuritiesReport).toList();

    // 2단계: corp_code별로 그룹화하여 최신 rcept_dt 1건만 채택
    Map<String, OpenDartDisclosureItem> latestByCorpCode = new LinkedHashMap<>();
    for (OpenDartDisclosureItem item : filtered) {
      String key = item.getCorpCode();
      OpenDartDisclosureItem existing = latestByCorpCode.get(key);
      if (existing == null || compareRceptDt(item.getRceptDt(), existing.getRceptDt()) > 0) {
        latestByCorpCode.put(key, item);
      }
    }

    // 3단계: rcept_dt 내림차순 정렬 (최신순)
    return latestByCorpCode.values().stream()
        // 접수일이 비어 있는 항목은 맨 뒤로(null 비교로 동기화 전체가 멈추지 않게)
        .sorted(
            Comparator.comparing(
                OpenDartDisclosureItem::getRceptDt,
                Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                .reversed())
        .toList();
  }

  /**
   * 접수일자(YYYYMMDD) 문자열 비교.
   *
   * @return a > b 이면 양수, 같으면 0, a < b 이면 음수
   */
  private int compareRceptDt(String a, String b) {
    if (a == null) {
      return b == null ? 0 : -1;
    }
    if (b == null) {
      return 1;
    }
    return a.compareTo(b);
  }

  /** DS001 응답 매핑용 내부 레코드. */
  private record OpenDartListResponse(
      String status,
      String message,
      int page_no,
      int page_count,
      int total_count,
      int total_page,
      List<OpenDartDisclosureItem> list) {}
}
