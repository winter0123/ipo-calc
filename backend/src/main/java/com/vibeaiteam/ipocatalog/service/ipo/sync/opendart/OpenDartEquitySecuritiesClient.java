package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenDART DS006 증권신고서-지분증권 상세 조회 클라이언트.
 *
 * <p>회사고유번호({@code corp_code})로 청약일·공모가·주간사 등 상세 정보를 조회한다. 응답은 {@code group[]} 배열 구조이며, 각 group은
 * {@code title}과 {@code list[]}를 갖는다. 필요한 필드는 "일반사항", "증권의종류", "인수인정보" 그룹에서 추출한다.
 */
public class OpenDartEquitySecuritiesClient {

  private static final String ENDPOINT_PATH = "/api/estkRs.json";
  private static final int REQUEST_INTERVAL_MS = 200; // 호출 간 최소 간격
  private static final int CONNECT_TIMEOUT_MS = 5_000; // 연결 타임아웃 5초
  private static final int READ_TIMEOUT_MS = 15_000; // 읽기 타임아웃 15초

  private final RestClient restClient;
  private final OpenDartApiProperties properties;
  private long lastRequestTimeMs = 0;

  /** 생성자 (테스트용 - RestClient 직접 주입). */
  public OpenDartEquitySecuritiesClient(RestClient restClient, OpenDartApiProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  /** 생성자 (운영용 - RestClient.Builder로 생성, 타임아웃 설정 포함). */
  public OpenDartEquitySecuritiesClient(
      RestClient.Builder restClientBuilder, OpenDartApiProperties properties) {
    this(
        restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(createRequestFactory())
            .build(),
        properties);
  }

  private static SimpleClientHttpRequestFactory createRequestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout(READ_TIMEOUT_MS);
    return factory;
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
    enforceRateLimit();

    OpenDartEstkRsResponse response =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path(ENDPOINT_PATH)
                        .queryParam("crtfc_key", properties.getApiKey())
                        .queryParam("corp_code", corpCode)
                        .build())
            .retrieve()
            .body(OpenDartEstkRsResponse.class);

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

  private void enforceRateLimit() {
    long now = System.currentTimeMillis();
    long elapsed = now - lastRequestTimeMs;
    if (elapsed < REQUEST_INTERVAL_MS) {
      try {
        Thread.sleep(REQUEST_INTERVAL_MS - elapsed);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("요청 간격 대기 중 인터럽트 발생", e);
      }
    }
    lastRequestTimeMs = System.currentTimeMillis();
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
   * DS006 응답의 group[] 배열에서 필요한 필드를 추출하여 DTO 리스트로 변환.
   *
   * <p>관심 그룹: "일반사항"(sbd, pymd, sband, asand, asstd), "증권의종류"(stksen, stkcnt, slprc, slta),
   * "인수인정보"(actnmn). 각 그룹의 list[] 첫 번째 항목에서 값을 가져온다.
   */
  private List<OpenDartEquitySecurityDetail> parseGroups(List<OpenDartGroup> groups) {
    if (groups == null || groups.isEmpty()) {
      return List.of();
    }

    // 그룹별 첫 번째 항목만 사용 (보통 1개씩만 있음)
    Map<String, OpenDartGroupItem> groupMap =
        groups.stream()
            .filter(g -> g.list() != null && !g.list().isEmpty())
            .collect(Collectors.toMap(OpenDartGroup::title, g -> g.list().get(0), (a, b) -> a));

    OpenDartGroupItem general = groupMap.get("일반사항");
    OpenDartGroupItem securityType = groupMap.get("증권의종류");
    OpenDartGroupItem underwriter = groupMap.get("인수인정보");

    // corp_code, corp_name은 모든 그룹에 공통으로 포함되어 있음 → 첫 번째 그룹에서 추출
    String corpCode = null;
    String corpName = null;
    for (OpenDartGroupItem item : groupMap.values()) {
      if (item.corp_code() != null) {
        corpCode = item.corp_code();
        corpName = item.corp_name();
        break;
      }
    }

    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode(corpCode)
            .corpName(corpName)
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
    return List.of(detail);
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
