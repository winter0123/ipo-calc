package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport;
import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenDART DS006 증권신고서-지분증권 상세 조회 클라이언트.
 *
 * <p>회사고유번호({@code corp_code})로 청약일·공모가·주간사 등 상세 정보를 조회한다. 응답은 {@code group[]} 배열 구조이며, 각 group은
 * {@code title}과 {@code list[]}를 갖는다. 필요한 필드는 "일반사항", "증권의종류", "인수인정보" 그룹에서 추출한다.
 */
public class OpenDartEquitySecuritiesClient {

  private static final String ENDPOINT_PATH = "/api/estkRs.json";
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final RestClient restClient;
  private final OpenDartApiProperties properties;
  private final RequestThrottle throttle;

  /** 생성자 (테스트용 - RestClient 직접 주입). */
  public OpenDartEquitySecuritiesClient(
      RestClient restClient, OpenDartApiProperties properties, RequestThrottle throttle) {
    this.restClient = restClient;
    this.properties = properties;
    this.throttle = throttle;
  }

  /** 생성자 (운영용 - RestClient.Builder로 생성, 타임아웃 설정 포함). */
  public OpenDartEquitySecuritiesClient(
      RestClient.Builder restClientBuilder,
      OpenDartApiProperties properties,
      RequestThrottle throttle) {
    this(
        restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(HttpClientSupport.requestFactory())
            .build(),
        properties,
        throttle);
  }

  /**
   * 특정 회사의 증권신고서-지분증권 상세 정보를 조회한다.
   *
   * @param corpCode 회사고유번호 (8자리)
   * @return 파싱된 {@link OpenDartEquitySecurityDetail} 리스트 (여러 건일 수 있음, 보통 1건)
   * @throws IllegalStateException API 키가 설정되지 않은 경우
   * @throws OpenDartAuthenticationException 인증키 오류(status 011)인 경우
   * @throws OpenDartApiException 기타 API 오류인 경우
   */
  public List<OpenDartEquitySecurityDetail> getEquitySecurityDetail(String corpCode) {
    validateApiKey();
    throttle.acquire();
    LocalDate today = LocalDate.now();

    OpenDartEstkRsResponse response;
    try {
      response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(ENDPOINT_PATH)
                          .queryParam("crtfc_key", properties.getApiKey())
                          .queryParam("corp_code", corpCode)
                          // DS006은 조회 기간(bgn_de·end_de)이 필수다. 없으면 "필수값 누락" 오류
                          .queryParam("bgn_de", today.minusDays(365).format(DATE))
                          .queryParam("end_de", today.format(DATE))
                          .build())
              .retrieve()
              .body(OpenDartEstkRsResponse.class);
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
      // 조회된 데이터 없음 - 빈 리스트 반환 (예외 아님)
      return List.of();
    }

    if (response.group() == null) {
      return List.of();
    }

    return parseGroups(response.group());
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

  /**
   * DS006 응답의 group[] 배열에서 필요한 필드를 추출하여 DTO 리스트로 변환한다.
   *
   * <p>관심 그룹: "일반사항"(sbd, pymd, sband, asand, asstd), "증권의종류"(stksen, stkcnt, slprc, slta),
   * "인수인정보"(actnmn). 정정 공시가 있으면 {@code rcept_no}가 여러 개이므로 같은 {@code rcept_no}끼리만 묶어 접수번호별로 DTO를 하나씩
   * 만든다(접수번호 오름차순). 최신 건 선택은 호출자가 {@code rceptNo}가 가장 큰 건을 고른다.
   */
  private List<OpenDartEquitySecurityDetail> parseGroups(List<OpenDartGroup> groups) {
    if (groups == null || groups.isEmpty()) {
      return List.of();
    }

    // rcept_no -> (그룹 title -> 첫 항목). TreeMap으로 접수번호 오름차순.
    Map<String, Map<String, OpenDartGroupItem>> byReceipt = new TreeMap<>();
    for (OpenDartGroup g : groups) {
      if (g.list() == null) {
        continue;
      }
      for (OpenDartGroupItem item : g.list()) {
        byReceipt
            .computeIfAbsent(item.rcept_no() == null ? "" : item.rcept_no(), k -> new HashMap<>())
            .putIfAbsent(g.title(), item);
      }
    }

    List<OpenDartEquitySecurityDetail> details = new ArrayList<>();
    byReceipt.forEach(
        (rceptNo, groupMap) -> {
          OpenDartGroupItem general = groupMap.get("일반사항");
          OpenDartGroupItem securityType = groupMap.get("증권의종류");
          OpenDartGroupItem underwriter = groupMap.get("인수인정보");

          // corp_code, corp_name은 모든 그룹에 공통으로 포함되어 있음
          OpenDartGroupItem any =
              groupMap.values().stream()
                  .filter(i -> i.corp_code() != null)
                  .findFirst()
                  .orElse(null);

          OpenDartEquitySecurityDetail detail =
              OpenDartEquitySecurityDetail.builder()
                  .rceptNo(rceptNo.isEmpty() ? null : rceptNo)
                  .corpCode(any == null ? null : any.corp_code())
                  .corpName(any == null ? null : any.corp_name())
                  .sbd(getValue(general, "sbd"))
                  .pymd(getValue(general, "pymd"))
                  .sband(getValue(general, "sband"))
                  .asand(getValue(general, "asand"))
                  .asstd(getValue(general, "asstd"))
                  .stksen(getValue(securityType, "stksen"))
                  .stkcnt(getValue(securityType, "stkcnt"))
                  .slprc(getValue(securityType, "slprc"))
                  .slta(getValue(securityType, "slta"))
                  .actnmn(getValue(underwriter, "actnmn"))
                  .build();
          detail.parseDerivedFields();
          details.add(detail);
        });
    return details;
  }

  private String getValue(OpenDartGroupItem item, String fieldName) {
    if (item == null) {
      return null;
    }
    return switch (fieldName) {
      case "sbd" -> item.sbd();
      case "pymd" -> item.pymd();
      case "sband" -> item.sband();
      case "asand" -> item.asand();
      case "asstd" -> item.asstd();
      case "stksen" -> item.stksen();
      case "stkcnt" -> item.stkcnt();
      case "slprc" -> item.slprc();
      case "slta" -> item.slta();
      case "actnmn" -> item.actnmn();
      default -> null;
    };
  }

  /** DS006 응답 매핑용 내부 레코드들. */
  private record OpenDartEstkRsResponse(String status, String message, List<OpenDartGroup> group) {}

  private record OpenDartGroup(String title, List<OpenDartGroupItem> list) {}

  private record OpenDartGroupItem(
      String rcept_no,
      String corp_cls,
      String corp_code,
      String corp_name,
      String sbd,
      String pymd,
      String sband,
      String asand,
      String asstd,
      String exstk,
      String exprc,
      String expd,
      String rpt_rcpn,
      String stksen,
      String stkcnt,
      String fv,
      String slprc,
      String slta,
      String slmthn,
      String actsen,
      String actnmn,
      String udtcnt,
      String udtamt,
      String udtprc,
      String udtmth,
      String se,
      String amt,
      String hdr,
      String rl_cmp,
      String bfsl_hdstk,
      String slstk,
      String atsl_hdstk,
      String exprc2,
      String expd2,
      String grtrs,
      String exavivr,
      String grtcnt) {}
}
