package com.ipocalc.ipocatalog.service.ipo.openprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

/** {@link StockPriceClient} 테스트. */
class StockPriceClientTest {

  private static final String STOCK_CODE = "123450";
  private static final LocalDate LISTING_DATE = LocalDate.of(2026, 10, 15);

  private MockRestServiceServer mockServer;
  private RestTemplate restTemplate;
  private RestClient restClient;
  private StockPriceClient client;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    restClient =
        RestClient.builder()
            .baseUrl("https://apis.data.go.kr")
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    var properties = new com.ipocalc.ipocatalog.service.holiday.DataGoKrApiProperties(
        "test-service-key", "https://apis.data.go.kr");
    client = new StockPriceClient(restClient, properties);
    objectMapper = new ObjectMapper();
  }

  private static final String ENDPOINT_MARKER =
      "/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2";

  private void expectStockPriceRequest(String responseBody) {
    mockServer
        .expect(requestTo(containsString(ENDPOINT_MARKER)))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  private static String itemsJson(String itemsValue) {
    return "{\"response\":{\"header\":{\"resultCode\":\"00\"},"
        + "\"body\":{\"items\":"
        + itemsValue
        + "}}}";
  }

  @Test
  void fetchOpenPrice_normalSingleItem_returnsMkp() throws IOException {
    // given
    expectStockPriceRequest(
        itemsJson(
            "{\"item\":["
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"123450\",\"itmsNm\":\"예시\","
                + "\"mrktCtg\":\"KOSDAQ\",\"mkp\":\"27400\",\"clpr\":\"17470\"}"
                + "] }"));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).hasValue(27400L);
  }

  @Test
  void fetchOpenPrice_srtnCdMismatch_ignoresOtherItems() throws IOException {
    // given - 응답에 다른 종목코드의 item이 포함되어 있어도 정확히 일치하는 행만 사용
    expectStockPriceRequest(
        itemsJson(
            "{\"item\":["
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"999999\",\"itmsNm\":\"다른종목\","
                + "\"mkp\":\"1000\"},"
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"123450\",\"itmsNm\":\"예시\","
                + "\"mkp\":\"27400\"}"
                + "] }"));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).hasValue(27400L);
  }

  @Test
  void fetchOpenPrice_emptyItemsString_returnsEmpty() throws IOException {
    // given - items가 빈 문자열("")
    expectStockPriceRequest(itemsJson("\"\""));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void fetchOpenPrice_mkpZero_returnsEmpty() throws IOException {
    // given - mkp가 "0"
    expectStockPriceRequest(
        itemsJson(
            "{\"item\":["
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"123450\",\"mkp\":\"0\"}"
                + "] }"));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void fetchOpenPrice_mkpBlank_returnsEmpty() throws IOException {
    // given - mkp가 빈 문자열
    expectStockPriceRequest(
        itemsJson(
            "{\"item\":["
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"123450\",\"mkp\":\"\"}"
                + "] }"));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void fetchOpenPrice_resultCodeNot00_throwsException() throws IOException {
    // given - 인증 안 된 키 (403 + XML/JSON 봉투)
    expectStockPriceRequest(
        "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":"
            + "{\"errMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\","
            + "\"returnReasonCode\":\"30\"}}}");

    // when & then
    assertThatThrownBy(() -> client.fetchOpenPrice(STOCK_CODE, LISTING_DATE))
        .isInstanceOf(StockPriceClient.StockPriceApiException.class)
        .hasMessageContaining("resultCode");
    mockServer.verify();
  }

  @Test
  void fetchOpenPrice_missingServiceKey_throwsIllegalStateException() {
    // given
    var emptyProps = new com.ipocalc.ipocatalog.service.holiday.DataGoKrApiProperties(
        "", "https://apis.data.go.kr");
    StockPriceClient clientWithEmptyKey = new StockPriceClient(RestClient.builder(), emptyProps);

    // when & then
    assertThatThrownBy(() -> clientWithEmptyKey.fetchOpenPrice(STOCK_CODE, LISTING_DATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("공공데이터포털 서비스 키가 설정되지 않았습니다")
        .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
  }

  @Test
  void fetchOpenPrice_noMatchingItem_returnsEmpty() throws IOException {
    // given - 응답에 있는 item의 srtnCd가 요청 종목코드와 불일치
    expectStockPriceRequest(
        itemsJson(
            "{\"item\":["
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"999999\",\"mkp\":\"5000\"}"
                + "] }"));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void fetchOpenPrice_mkpParseError_returnsEmpty() throws IOException {
    // given - mkp가 숫자가 아닌 값
    expectStockPriceRequest(
        itemsJson(
            "{\"item\":["
                + "{\"basDt\":\"20261015\",\"srtnCd\":\"123450\",\"mkp\":\"abc\"}"
                + "] }"));

    // when
    Optional<Long> result = client.fetchOpenPrice(STOCK_CODE, LISTING_DATE);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void productionConstructor_buildsClientAndExposesProperties() {
    var properties = new com.ipocalc.ipocatalog.service.holiday.DataGoKrApiProperties(
        "test-key", "https://apis.data.go.kr");
    StockPriceClient prod = new StockPriceClient(RestClient.builder(), properties);

    assertThat(prod.getProperties()).isSameAs(properties);
  }
}
