package com.vibeaiteam.ipocatalog.service.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** {@link KasiSpecialDayClient} 테스트. */
class KasiSpecialDayClientTest {

  private MockRestServiceServer mockServer;
  private RestTemplate restTemplate;
  private RestClient restClient;
  private DataGoKrApiProperties properties;
  private KasiSpecialDayClient client;
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
    properties = new DataGoKrApiProperties("test-service-key", "https://apis.data.go.kr");
    client = new KasiSpecialDayClient(restClient, properties);
    objectMapper = new ObjectMapper();
  }

  private void expectKasiRequest(int solYear, String responseBody) {
    mockServer
        .expect(
            requestTo(containsString("/B090041/openapi/service/SpcdeInfoService/getRestDeInfo")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  private String readFixture(String fileName) throws IOException {
    Path path = Path.of("src/test/resources/kasi", fileName);
    return Files.readString(path);
  }

  @Test
  void getHolidays_normalMultipleItems_parsesAllHolidays() throws IOException {
    // given
    String fixture = readFixture("kasi-2026-normal.json");
    expectKasiRequest(2026, fixture);

    // when
    List<KasiSpecialDayClient.HolidayItem> result = client.getHolidays(2026);

    // then
    mockServer.verify();
    assertThat(result).hasSize(16);

    // 첫 번째 항목 검증
    KasiSpecialDayClient.HolidayItem first = result.get(0);
    assertThat(first.date()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(first.name()).isEqualTo("신정");

    // 마지막 항목 검증
    KasiSpecialDayClient.HolidayItem last = result.get(15);
    assertThat(last.date()).isEqualTo(LocalDate.of(2026, 12, 25));
    assertThat(last.name()).isEqualTo("성탄절");

    // 요청 쿼리에 solYear=2026이 포함되는지 검증 (mockServer.verify()로 확인)
  }

  @Test
  void getHolidays_singleItem_parsesCorrectly() throws IOException {
    // given
    String fixture = readFixture("kasi-2026-single.json");
    expectKasiRequest(2026, fixture);

    // when
    List<KasiSpecialDayClient.HolidayItem> result = client.getHolidays(2026);

    // then
    mockServer.verify();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(result.get(0).name()).isEqualTo("신정");
  }

  @Test
  void getHolidays_emptyItems_returnsEmptyList() throws IOException {
    // given - items가 빈 문자열("")
    String fixture = readFixture("kasi-2026-empty.json");
    expectKasiRequest(2026, fixture);

    // when
    List<KasiSpecialDayClient.HolidayItem> result = client.getHolidays(2026);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void getHolidays_isHolidayN_excludedFromResult() throws IOException {
    // given - isHoliday="Y" 1건, "N" 1건
    String fixture = readFixture("kasi-2026-mixed-holiday.json");
    expectKasiRequest(2026, fixture);

    // when
    List<KasiSpecialDayClient.HolidayItem> result = client.getHolidays(2026);

    // then
    mockServer.verify();
    assertThat(result).hasSize(1); // isHoliday="Y"만 포함
    assertThat(result.get(0).name()).isEqualTo("신정");
  }

  @Test
  void getHolidays_resultCodeNot00_throwsKasiApiException() throws IOException {
    // given
    String fixture = readFixture("kasi-2026-error.json");
    expectKasiRequest(2026, fixture);

    // when & then
    assertThatThrownBy(() -> client.getHolidays(2026))
        .isInstanceOf(KasiSpecialDayClient.KasiApiException.class)
        .hasMessageContaining("resultCode=99")
        .hasMessageContaining("ERROR");
    mockServer.verify();
  }

  @Test
  void getHolidays_missingServiceKey_throwsIllegalStateException() {
    // given
    DataGoKrApiProperties emptyProps = new DataGoKrApiProperties("", "https://apis.data.go.kr");
    KasiSpecialDayClient clientWithEmptyKey =
        new KasiSpecialDayClient(RestClient.builder(), emptyProps);

    // when & then
    assertThatThrownBy(() -> clientWithEmptyKey.getHolidays(2026))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("공공데이터포털 서비스 키가 설정되지 않았습니다")
        .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
  }

  @Test
  void getHolidays_exceptionMessageDoesNotContainServiceKey() throws IOException {
    // given
    String fixture = readFixture("kasi-2026-error.json");
    expectKasiRequest(2026, fixture);

    // when & then
    try {
      client.getHolidays(2026);
    } catch (KasiSpecialDayClient.KasiApiException e) {
      String message = e.getMessage();
      assertThat(message).doesNotContain("test-service-key");
      assertThat(message).doesNotContain("ServiceKey");
    }
    mockServer.verify();
  }

  @Test
  void getHolidays_ioError_exceptionDoesNotLeakServiceKey() {
    mockServer
        .expect(
            requestTo(containsString("/B090041/openapi/service/SpcdeInfoService/getRestDeInfo")))
        .andRespond(
            org.springframework.test.web.client.response.MockRestResponseCreators.withException(
                new IOException("connection reset")));

    assertThatThrownBy(() -> client.getHolidays(2026))
        .isInstanceOf(KasiSpecialDayClient.KasiApiException.class)
        .satisfies(
            e -> {
              assertThat(e.getMessage()).doesNotContain("test-service-key");
              assertThat(e.getCause()).isNull();
            });
    mockServer.verify();
  }

  @Test
  void getHolidays_locdateAsString_parsesCorrectly() throws IOException {
    // given - locdate가 문자열로 오는 경우
    String fixture =
        """
        {
          "response": {
            "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
            "body": {
              "items": {"item": [{"dateKind": "01", "dateName": "테스트", "isHoliday": "Y",
                "locdate": "20260101", "seq": 1}]},
              "numOfRows": 100,
              "pageNo": 1,
              "totalCount": 1
            }
          }
        }
        """;
    expectKasiRequest(2026, fixture);

    // when
    List<KasiSpecialDayClient.HolidayItem> result = client.getHolidays(2026);

    // then
    mockServer.verify();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
  }

  private static String itemsJson(String itemsValue) {
    return "{\"response\":{\"header\":{\"resultCode\":\"00\"},"
        + "\"body\":{\"items\":"
        + itemsValue
        + "}}}";
  }

  @Test
  void getHolidays_blankResponseBody_throwsKasiApiException() {
    expectKasiRequest(2026, "  ");

    assertThatThrownBy(() -> client.getHolidays(2026))
        .isInstanceOf(KasiSpecialDayClient.KasiApiException.class)
        .hasMessageContaining("비어 있습니다");
  }

  @Test
  void getHolidays_malformedJson_throwsParseFailure() {
    expectKasiRequest(2026, "{not json");

    assertThatThrownBy(() -> client.getHolidays(2026))
        .isInstanceOf(KasiSpecialDayClient.KasiApiException.class)
        .hasMessageContaining("파싱 실패");
  }

  @Test
  void getHolidays_itemsWithoutItemNode_returnsEmpty() {
    expectKasiRequest(2026, itemsJson("{}"));

    assertThat(client.getHolidays(2026)).isEmpty();
  }

  @Test
  void getHolidays_itemNull_returnsEmpty() {
    expectKasiRequest(2026, itemsJson("{\"item\":null}"));

    assertThat(client.getHolidays(2026)).isEmpty();
  }

  @Test
  void getHolidays_itemIsNeitherArrayNorObject_returnsEmpty() {
    expectKasiRequest(2026, itemsJson("{\"item\":\"oops\"}"));

    assertThat(client.getHolidays(2026)).isEmpty();
  }

  @Test
  void getHolidays_singleObjectNotHoliday_returnsEmpty() {
    expectKasiRequest(
        2026,
        itemsJson("{\"item\":{\"isHoliday\":\"N\",\"locdate\":20260101,\"dateName\":\"x\"}}"));

    assertThat(client.getHolidays(2026)).isEmpty();
  }

  @Test
  void getHolidays_badRows_skippedAndBlankNameDefaulted() {
    // 날짜가 비었거나 형식이 틀린 행은 버리고, 이름이 빈 행은 "특일"로 채운다.
    expectKasiRequest(
        2026,
        itemsJson(
            "{\"item\":["
                + "{\"isHoliday\":\"Y\",\"dateName\":\"a\"},"
                + "{\"isHoliday\":\"Y\",\"locdate\":\"2026-13-99\",\"dateName\":\"b\"},"
                + "{\"isHoliday\":\"Y\",\"locdate\":20260505,\"dateName\":\"\"}]}"));

    assertThat(client.getHolidays(2026))
        .containsExactly(new KasiSpecialDayClient.HolidayItem(LocalDate.of(2026, 5, 5), "특일"));
  }

  @Test
  void getHolidays_resultCodeError_defaultsMessageWhenMissing() {
    expectKasiRequest(2026, "{\"response\":{\"header\":{\"resultCode\":\"30\"}}}");

    assertThatThrownBy(() -> client.getHolidays(2026))
        .isInstanceOf(KasiSpecialDayClient.KasiApiException.class)
        .hasMessageContaining("resultCode=30")
        .hasMessageContaining("알 수 없는 오류");
  }

  @Test
  void productionConstructor_buildsClientWithBaseUrlAndExposesProperties() {
    KasiSpecialDayClient prod = new KasiSpecialDayClient(RestClient.builder(), properties);

    assertThat(prod.getProperties()).isSameAs(properties);
  }
}
