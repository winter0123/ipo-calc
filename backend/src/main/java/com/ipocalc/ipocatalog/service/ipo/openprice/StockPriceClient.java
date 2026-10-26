package com.ipocalc.ipocatalog.service.ipo.openprice;

import com.ipocalc.ipocatalog.service.holiday.DataGoKrApiProperties;
import com.ipocalc.ipocatalog.service.support.HttpClientSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공데이터포털 주식시세 정보 API 클라이언트.
 *
 * <p>금융위원회 주식시세 정보 서비스({@code GetStockSecuritiesInfoService_V2})의 {@code getStockPriceInfo_V2}
 * 엔드포인트를 호출해 특정 종목의 상장일 시초가(mkp)를 조회한다.
 *
 * <ul>
 *   <li>엔드포인트: {@code GET /1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2}
 *   <li>파라미터: {@code serviceKey}, {@code resultType=json}, {@code numOfRows=10}, {@code pageNo=1},
 *       {@code basDt=YYYYMMDD}(조회일), {@code likeSrtnCd=종목코드}
 *   <li>응답: {@code response.header.resultCode}가 {@code "00"}이면 정상. {@code
 *       response.body.items.item}은 1건이면 객체, 여러 건이면 배열, 0건이면 빈 문자열({@code ""})일 수 있음
 *   <li>item 필드: {@code basDt}, {@code srtnCd}, {@code itmsNm}, {@code clpr}, {@code mkp}, {@code
 *       hipr}, {@code lopr} 등. 값은 모두 JSON 문자열
 *   <li>시초가 = {@code mkp} (시가)
 * </ul>
 *
 * <p>데이터 반영: D일 시세는 D+1 영업일 오후에 조회된다. 휴장일이거나 아직 반영 전이면 {@code items}가 비어 있다.
 */
@RequiredArgsConstructor
@Slf4j
public class StockPriceClient {

  private static final String ENDPOINT_PATH =
      "/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2";
  private static final String NORMAL_RESULT_CODE = "00";
  private static final long THROTTLE_INTERVAL_MS = 200L;

  private final DataGoKrApiProperties properties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final HttpClientSupport.RequestThrottle throttle =
      new HttpClientSupport.RequestThrottle(THROTTLE_INTERVAL_MS);

  public DataGoKrApiProperties getProperties() {
    return properties;
  }

  /** 생성자 (테스트용 - RestClient 직접 주입). */
  public StockPriceClient(RestClient restClient, DataGoKrApiProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
    this.objectMapper = createObjectMapper();
  }

  /** 생성자 (운영용 - RestClient.Builder로 생성, 타임아웃 설정 포함). */
  public StockPriceClient(RestClient.Builder restClientBuilder, DataGoKrApiProperties properties) {
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
   * 특정 종목의 상장일 시초가를 조회한다.
   *
   * <p>{@code likeSrtnCd}는 부분 일치 검색이므로, 응답 item 중 {@code srtnCd}가 요청 종목코드와 정확히 같은 행만 사용한다. {@code
   * mkp}를 Long으로 파싱해 0보다 크면 반환하고, 0·빈 값·파싱 실패·행 없음이면 {@link Optional#empty()}를 반환한다.
   *
   * @param stockCode 종목코드 (6자리)
   * @param listingDate 상장일
   * @return 시초가(원). 조회할 수 없으면 {@link Optional#empty()}
   * @throws IllegalStateException 서비스 키가 설정되지 않은 경우
   * @throws StockPriceApiException API 호출 실패 또는 비정상 응답 코드인 경우
   */
  public Optional<Long> fetchOpenPrice(String stockCode, LocalDate listingDate) {
    validateServiceKey();

    throttle.acquire();

    String responseBody;
    try {
      responseBody =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(ENDPOINT_PATH)
                          .queryParam("serviceKey", properties.getServiceKey())
                          .queryParam("resultType", "json")
                          .queryParam("numOfRows", 10)
                          .queryParam("pageNo", 1)
                          .queryParam("basDt", listingDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                          .queryParam("likeSrtnCd", stockCode)
                          .build())
              .retrieve()
              .body(String.class);
    } catch (Exception e) {
      // 예외 메시지에는 ServiceKey가 담긴 요청 URL이 들어갈 수 있어 클래스명만 남긴다.
      String reason = e.getClass().getSimpleName();
      log.error("주식시세 API 호출 실패: stockCode={}, reason={}", stockCode, reason);
      throw new StockPriceApiException("주식시세 API 호출 실패: " + reason);
    }

    if (responseBody == null || responseBody.isBlank()) {
      throw new StockPriceApiException("주식시세 API 응답이 비어 있습니다");
    }

    return parseOpenPrice(responseBody, stockCode);
  }

  private void validateServiceKey() {
    if (properties.isServiceKeyMissing()) {
      throw new IllegalStateException(
          "공공데이터포털 서비스 키가 설정되지 않았습니다. DATA_GO_KR_SERVICE_KEY 환경변수를 확인하세요.");
    }
  }

  /**
   * JSON 응답을 파싱해 시초가를 추출한다.
   *
   * <p>응답 구조:
   *
   * <pre>
   * {
   *   "response": {
   *     "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
   *     "body": {
   *       "items": {"item": [...]},  // 또는 "items": "", 또는 "items": {"item": {...}}
   *       "numOfRows": 10,
   *       "pageNo": 1,
   *       "totalCount": 1
   *     }
   *   }
   * }
   * </pre>
   */
  private Optional<Long> parseOpenPrice(String responseBody, String stockCode) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode header = root.path("response").path("header");
      String resultCode = header.path("resultCode").asString();

      if (!NORMAL_RESULT_CODE.equals(resultCode)) {
        String resultMsg = header.path("resultMsg").asString("알 수 없는 오류");
        log.error(
            "주식시세 API 비정상 응답: stockCode={}, resultCode={}, resultMsg={}",
            stockCode,
            resultCode,
            resultMsg);
        throw new StockPriceApiException(
            "주식시세 API 오류: resultCode=" + resultCode + ", resultMsg=" + resultMsg);
      }

      JsonNode body = root.path("response").path("body");
      JsonNode itemsNode = body.path("items");

      // items가 빈 문자열인 경우 (0건)
      if (itemsNode.isString() && itemsNode.asString().isBlank()) {
        log.debug("주식시세 API 응답: stockCode={}, 시세 데이터 0건", stockCode);
        return Optional.empty();
      }

      // items.item이 배열 또는 단일 객체인 경우
      JsonNode itemArray = itemsNode.path("item");
      if (itemArray.isMissingNode() || itemArray.isNull()) {
        log.debug("주식시세 API 응답: stockCode={}, item 노드 없음", stockCode);
        return Optional.empty();
      }

      List<JsonNode> items = new ArrayList<>();
      if (itemArray.isArray()) {
        itemArray.forEach(items::add);
      } else if (itemArray.isObject()) {
        items.add(itemArray);
      }

      // srtnCd가 요청 종목코드와 정확히 일치하는 행만 사용
      for (JsonNode item : items) {
        String srtnCd = item.path("srtnCd").asString();
        if (!stockCode.equals(srtnCd)) {
          continue;
        }

        String mkp = item.path("mkp").asString();
        if (mkp == null || mkp.isBlank()) {
          log.debug("주식시세 API 응답: stockCode={}, mkp 값 없음", stockCode);
          return Optional.empty();
        }

        try {
          long openPrice = Long.parseLong(mkp);
          if (openPrice > 0) {
            return Optional.of(openPrice);
          }
          log.debug("주식시세 API 응답: stockCode={}, mkp=0으로 무시", stockCode);
          return Optional.empty();
        } catch (NumberFormatException e) {
          log.warn("주식시세 API 응답: stockCode={}, mkp 파싱 실패: {}", stockCode, mkp);
          return Optional.empty();
        }
      }

      log.debug("주식시세 API 응답: stockCode={}, 일치하는 종목 없음", stockCode);
      return Optional.empty();

    } catch (StockPriceApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("주식시세 API 응답 파싱 실패: stockCode={}, error={}", stockCode, e.getMessage());
      throw new StockPriceApiException("주식시세 API 응답 파싱 실패: " + e.getMessage(), e);
    }
  }

  /** 주식시세 API 예외. */
  public static class StockPriceApiException extends RuntimeException {
    /** 메시지(와 원인 예외)로 예외를 생성한다. */
    public StockPriceApiException(String message) {
      super(message);
    }

    /** 메시지(와 원인 예외)로 예외를 생성한다. */
    public StockPriceApiException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
