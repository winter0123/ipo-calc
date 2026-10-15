package com.ipocalc.ipocatalog.service.holiday;

import com.ipocalc.ipocatalog.service.support.HttpClientSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한국천문연구원 특일 정보 API 클라이언트.
 *
 * <p>공공데이터포털 {@code B090041} 특일 정보 서비스({@code getRestDeInfo})를 호출해 법정 공휴일, 대체공휴일, 임시공휴일, 선거일 등을
 * 조회한다.
 *
 * <ul>
 *   <li>엔드포인트: {@code GET /B090041/openapi/service/SpcdeInfoService/getRestDeInfo}
 *   <li>파라미터: {@code solYear}(연도), {@code _type=json}, {@code numOfRows=100}, {@code pageNo=1},
 *       {@code ServiceKey}
 *   <li>응답: {@code response.header.resultCode}가 {@code "00"}이면 정상, {@code
 *       response.body.items.item}은 1건이면 객체, 여러 건이면 배열, 0건이면 빈 문자열({@code ""})일 수 있음
 *   <li>항목 필드: {@code locdate}(yyyymmdd 정수 또는 문자열), {@code dateName}, {@code isHoliday}({@code
 *       "Y"}만 사용), {@code dateKind}, {@code seq}
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public class KasiSpecialDayClient {

  private static final String ENDPOINT_PATH =
      "/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
  private static final String NORMAL_RESULT_CODE = "00";

  private final DataGoKrApiProperties properties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public DataGoKrApiProperties getProperties() {
    return properties;
  }

  /** 생성자 (테스트용 - RestClient 직접 주입). */
  public KasiSpecialDayClient(RestClient restClient, DataGoKrApiProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
    this.objectMapper = createObjectMapper();
  }

  /** 생성자 (운영용 - RestClient.Builder로 생성, 타임아웃 설정 포함). */
  public KasiSpecialDayClient(
      RestClient.Builder restClientBuilder, DataGoKrApiProperties properties) {
    this(
        restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(HttpClientSupport.requestFactory())
            .build(),
        properties);
  }

  private static ObjectMapper createObjectMapper() {
    // items.item이 단일 객체일 때도 배열로 처리되도록 설정
    return JsonMapper.builder().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY).build();
  }

  /**
   * 특정 연도의 특일 정보를 조회한다.
   *
   * @param solYear 조회할 연도 (예: 2026)
   * @return 휴일 항목 리스트 ({@code isHoliday="Y"}인 것만 포함, {@code LocalDate}와 이름)
   * @throws IllegalStateException 서비스 키가 설정되지 않은 경우
   * @throws KasiApiException API 호출 실패 또는 비정상 응답 코드인 경우
   */
  public List<HolidayItem> getHolidays(int solYear) {
    validateServiceKey();

    String responseBody;
    try {
      responseBody =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(ENDPOINT_PATH)
                          .queryParam("solYear", solYear)
                          .queryParam("_type", "json")
                          .queryParam("numOfRows", 100)
                          .queryParam("pageNo", 1)
                          .queryParam("ServiceKey", properties.getServiceKey())
                          .build())
              .retrieve()
              .body(String.class);
    } catch (Exception e) {
      // 예외 메시지에는 ServiceKey가 담긴 요청 URL이 들어갈 수 있어 클래스명만 남긴다.
      String reason = e.getClass().getSimpleName();
      log.error("KASI 특일 정보 API 호출 실패: solYear={}, reason={}", solYear, reason);
      throw new KasiApiException("KASI API 호출 실패: " + reason);
    }

    if (responseBody == null || responseBody.isBlank()) {
      throw new KasiApiException("KASI API 응답이 비어 있습니다");
    }

    return parseResponse(responseBody, solYear);
  }

  private void validateServiceKey() {
    if (properties.isServiceKeyMissing()) {
      throw new IllegalStateException(
          "공공데이터포털 서비스 키가 설정되지 않았습니다. DATA_GO_KR_SERVICE_KEY 환경변수를 확인하세요.");
    }
  }

  /**
   * JSON 응답을 파싱해 휴일 항목 리스트로 변환한다.
   *
   * <p>응답 구조:
   *
   * <pre>
   * {
   *   "response": {
   *     "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
   *     "body": {
   *       "items": {"item": [...]},  // 또는 "items": "", 또는 "items": {"item": {...}}
   *       "numOfRows": 100,
   *       "pageNo": 1,
   *       "totalCount": 1
   *     }
   *   }
   * }
   * </pre>
   */
  private List<HolidayItem> parseResponse(String responseBody, int solYear) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode header = root.path("response").path("header");
      String resultCode = header.path("resultCode").asString();

      if (!NORMAL_RESULT_CODE.equals(resultCode)) {
        String resultMsg = header.path("resultMsg").asString("알 수 없는 오류");
        log.error(
            "KASI API 비정상 응답: solYear={}, resultCode={}, resultMsg={}",
            solYear,
            resultCode,
            resultMsg);
        throw new KasiApiException(
            "KASI API 오류: resultCode=" + resultCode + ", resultMsg=" + resultMsg);
      }

      JsonNode body = root.path("response").path("body");
      JsonNode itemsNode = body.path("items");

      // items가 빈 문자열인 경우 (0건)
      if (itemsNode.isString() && itemsNode.asString().isBlank()) {
        log.debug("KASI API 응답: solYear={}, 휴일 항목 0건", solYear);
        return List.of();
      }

      // items.item이 배열 또는 단일 객체인 경우
      JsonNode itemArray = itemsNode.path("item");
      if (itemArray.isMissingNode() || itemArray.isNull()) {
        log.debug("KASI API 응답: solYear={}, item 노드 없음", solYear);
        return List.of();
      }

      List<HolidayItem> holidays = new ArrayList<>();
      if (itemArray.isArray()) {
        for (JsonNode item : itemArray) {
          HolidayItem holiday = parseItem(item);
          if (holiday != null) {
            holidays.add(holiday);
          }
        }
      } else if (itemArray.isObject()) {
        HolidayItem holiday = parseItem(itemArray);
        if (holiday != null) {
          holidays.add(holiday);
        }
      }

      log.debug(
          "KASI API 응답 파싱 완료: solYear={}, 휴일 항목 {}건 (isHoliday=Y만)", solYear, holidays.size());
      return holidays;

    } catch (KasiApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("KASI API 응답 파싱 실패: solYear={}, error={}", solYear, e.getMessage());
      throw new KasiApiException("KASI API 응답 파싱 실패: " + e.getMessage(), e);
    }
  }

  /** 단일 item 노드를 HolidayItem으로 파싱. isHoliday="Y"인 것만 반환, 나머지는 null. */
  private HolidayItem parseItem(JsonNode item) {
    String isHoliday = item.path("isHoliday").asString();
    if (!"Y".equals(isHoliday)) {
      return null;
    }

    // locdate는 정수(20260101) 또는 문자열("20260101")일 수 있음
    String locdateStr = item.path("locdate").asString();
    if (locdateStr.isBlank()) {
      return null;
    }

    LocalDate date;
    try {
      // 숫자만 있는 경우 yyyyMMdd 파싱
      date = LocalDate.parse(locdateStr, DateTimeFormatter.BASIC_ISO_DATE);
    } catch (Exception e) {
      log.warn("KASI API locdate 파싱 실패: locdate={}", locdateStr);
      return null;
    }

    String name = item.path("dateName").asString();
    if (name.isBlank()) {
      name = "특일"; // 기본값
    }

    return new HolidayItem(date, name);
  }

  /** 특일 정보 항목 (날짜, 이름). */
  public record HolidayItem(LocalDate date, String name) {}

  /** KASI API 예외. */
  public static class KasiApiException extends RuntimeException {
    /** 메시지(와 원인 예외)로 예외를 생성한다. */
    public KasiApiException(String message) {
      super(message);
    }

    /** 메시지(와 원인 예외)로 예외를 생성한다. */
    public KasiApiException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
