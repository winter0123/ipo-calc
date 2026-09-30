package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** {@link OpenDartEquitySecuritiesClient} 테스트. */
class OpenDartEquitySecuritiesClientTest {

  private MockRestServiceServer mockServer;
  private RestTemplate restTemplate;
  private RestClient restClient;
  private OpenDartApiProperties properties;
  private OpenDartEquitySecuritiesClient client;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    restClient =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    properties = new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr");
    client = new OpenDartEquitySecuritiesClient(restClient, properties);
    objectMapper = new ObjectMapper();
  }

  private void expectEstkRsRequest(String responseBody) {
    mockServer
        .expect(requestTo(containsString("/api/estkRs.json")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  @Test
  void getEquitySecurityDetail_ioError_exceptionDoesNotExposeApiKey() {
    mockServer
        .expect(requestTo(containsString("/api/estkRs.json")))
        .andRespond(withException(new IOException("connection reset")));

    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageNotContaining("test-api-key")
        .hasNoCause();
  }

  @Test
  void getEquitySecurityDetail_success_parsesAllFields() throws IOException {
    // given
    String fixture = readFixture("ds006-optonix.json");
    expectEstkRsRequest(fixture);

    // when
    List<OpenDartEquitySecurityDetail> result = client.getEquitySecurityDetail("01658035");

    // then
    mockServer.verify();
    assertThat(result).hasSize(1);

    OpenDartEquitySecurityDetail detail = result.get(0);
    assertThat(detail.getCorpCode()).isEqualTo("01658035");
    assertThat(detail.getCorpName()).isEqualTo("옵토닉스");

    // 원문 필드 검증
    assertThat(detail.getSbd()).isEqualTo("2026년 11월 05일 ~ 2026년 11월 06일");
    assertThat(detail.getPymd()).isEqualTo("2026년 11월 10일");
    assertThat(detail.getSband()).isEqualTo("2026년 11월 05일");
    assertThat(detail.getAsand()).isEqualTo("2026년 11월 10일");
    assertThat(detail.getAsstd()).isEqualTo("-");
    assertThat(detail.getStksen()).isEqualTo("보통주");
    assertThat(detail.getStkcnt()).isEqualTo("2,200,000");
    assertThat(detail.getSlprc()).isEqualTo("10,000");
    assertThat(detail.getSlta()).isEqualTo("22,000,000,000");
    assertThat(detail.getActnmn()).isEqualTo("한국투자증권");

    // 파생 필드 검증
    assertThat(detail.getSubscriptionStartDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 5));
    assertThat(detail.getOfferingShareCount()).isEqualTo(2_200_000L);
    assertThat(detail.getOfferPrice()).isEqualTo(10_000L);
    assertThat(detail.getTotalOfferingAmount()).isEqualTo(22_000_000_000L);
  }

  @Test
  void getEquitySecurityDetail_status013_returnsEmptyList() throws IOException {
    // given
    String fixture = readFixture("ds006-empty.json");
    expectEstkRsRequest(fixture);

    // when
    List<OpenDartEquitySecurityDetail> result = client.getEquitySecurityDetail("01658035");

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void getEquitySecurityDetail_status011_throwsAuthenticationException() throws IOException {
    // given - 실제 오류 응답에는 group 필드 없음
    String errorResponse = "{\"status\":\"011\",\"message\":\"인증키가 유효하지 않습니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("인증키가 유효하지 않습니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_status010_throwsAuthenticationException() throws IOException {
    // given - 010: 미등록 키
    String errorResponse = "{\"status\":\"010\",\"message\":\"미등록 키입니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("미등록 키입니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_status012_throwsAuthenticationException() throws IOException {
    // given - 012: 사용할 수 없는 키
    String errorResponse = "{\"status\":\"012\",\"message\":\"사용할 수 없는 키입니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("사용할 수 없는 키입니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_status901_throwsAuthenticationException() throws IOException {
    // given - 901: 계정 개인정보 보유기간 만료
    String errorResponse = "{\"status\":\"901\",\"message\":\"계정 개인정보 보유기간이 만료되었습니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("계정 개인정보 보유기간이 만료되었습니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_status020_throwsApiException() throws IOException {
    // given - 020: 요청 제한 초과
    String errorResponse = "{\"status\":\"020\",\"message\":\"요청 제한을 초과했습니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageContaining("요청 제한을 초과했습니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_status800_throwsApiException() throws IOException {
    // given - 800: 시스템 점검
    String errorResponse = "{\"status\":\"800\",\"message\":\"시스템 점검 중입니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageContaining("시스템 점검 중입니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_status021_throwsApiException() throws IOException {
    // given - 021: 기타 오류
    String errorResponse = "{\"status\":\"021\",\"message\":\"기타 오류입니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.getEquitySecurityDetail("01658035"))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageContaining("기타 오류입니다");
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_missingApiKey_throwsIllegalStateException() {
    // given
    OpenDartApiProperties emptyProps = new OpenDartApiProperties("", "https://opendart.fss.or.kr");
    OpenDartEquitySecuritiesClient clientWithEmptyKey =
        new OpenDartEquitySecuritiesClient(RestClient.builder(), emptyProps);

    // when & then
    assertThatThrownBy(() -> clientWithEmptyKey.getEquitySecurityDetail("01658035"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OpenDART API 키가 설정되지 않았습니다");
  }

  @Test
  void getEquitySecurityDetail_exceptionMessageDoesNotContainApiKey() throws IOException {
    // given
    String errorResponse = "{\"status\":\"011\",\"message\":\"인증키가 유효하지 않습니다.\"}";
    expectEstkRsRequest(errorResponse);

    // when & then
    try {
      client.getEquitySecurityDetail("01658035");
    } catch (OpenDartAuthenticationException e) {
      String message = e.getMessage();
      assertThat(message).doesNotContain("test-api-key");
      assertThat(message).doesNotContain("crtfc_key");
    }
    mockServer.verify();
  }

  @Test
  void getEquitySecurityDetail_dashValues_parsedAsNull() throws IOException {
    // given - 대시 값이 포함된 응답 시뮬레이션
    String fixture =
        """
        {"status":"000","message":"정상","group":[
          {"title":"일반사항","list":[{"corp_code":"01658035","corp_name":"테스트","sbd":"-",
            "pymd":"-","sband":"-","asand":"-","asstd":"-"}]},
          {"title":"증권의종류","list":[{"corp_code":"01658035","corp_name":"테스트","stksen":"-",
            "stkcnt":"-","slprc":"-","slta":"-"}]},
          {"title":"인수인정보","list":[{"corp_code":"01658035","corp_name":"테스트","actnmn":"-"}]}
        ]}
        """;
    expectEstkRsRequest(fixture);

    // when
    List<OpenDartEquitySecurityDetail> result = client.getEquitySecurityDetail("01658035");

    // then
    mockServer.verify();
    assertThat(result).hasSize(1);
    OpenDartEquitySecurityDetail detail = result.get(0);
    assertThat(detail.getSubscriptionStartDate()).isNull();
    assertThat(detail.getOfferingShareCount()).isNull();
    assertThat(detail.getOfferPrice()).isNull();
    assertThat(detail.getTotalOfferingAmount()).isNull();
  }

  private String readFixture(String fileName) throws IOException {
    Path path = Path.of("src/test/resources/opendart", fileName);
    return Files.readString(path);
  }
}
