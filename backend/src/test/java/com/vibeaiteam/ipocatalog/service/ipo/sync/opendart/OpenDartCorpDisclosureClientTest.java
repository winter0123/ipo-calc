package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
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

/** {@link OpenDartCorpDisclosureClient} 테스트. */
class OpenDartCorpDisclosureClientTest {

  private MockRestServiceServer mockServer;
  private RestTemplate restTemplate;
  private RestClient restClient;
  private OpenDartApiProperties properties;
  private OpenDartCorpDisclosureClient client;
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
    client = new OpenDartCorpDisclosureClient(restClient, properties);
    objectMapper = new ObjectMapper();
  }

  private String readFixture(String fileName) throws IOException {
    Path path = Path.of("src/test/resources/opendart", fileName);
    return Files.readString(path);
  }

  private String withTotalPage(String fixture, int totalPage) throws IOException {
    JsonNode node = objectMapper.readTree(fixture);
    ((ObjectNode) node).put("total_page", totalPage);
    return objectMapper.writeValueAsString(node);
  }

  private String withPageNo(String fixture, int pageNo) throws IOException {
    JsonNode node = objectMapper.readTree(fixture);
    ((ObjectNode) node).put("page_no", pageNo);
    return objectMapper.writeValueAsString(node);
  }

  private String withEmptyList(String fixture) throws IOException {
    JsonNode node = objectMapper.readTree(fixture);
    ((ObjectNode) node).putArray("list");
    return objectMapper.writeValueAsString(node);
  }

  private void expectListRequest(String responseBody) {
    mockServer
        .expect(requestTo(containsString("/api/list.json")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  private void expectListRequestMultiplePages(String page1Body, String page2Body) {
    mockServer
        .expect(requestTo(containsString("/api/list.json")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(page1Body, MediaType.APPLICATION_JSON));
    mockServer
        .expect(requestTo(containsString("/api/list.json")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(page2Body, MediaType.APPLICATION_JSON));
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_success_parsesAndDeduplicates() throws IOException {
    // given - total_page=1로 수정하여 페이지네이션 없이 단일 페이지 테스트
    String fixture = withTotalPage(readFixture("ds001-c001.json"), 1);
    expectListRequest(fixture);

    // when
    List<OpenDartDisclosureItem> result = client.searchRecentEquitySecuritiesCompanies(7);

    // then
    mockServer.verify();
    assertThat(result).hasSize(3); // 옵토닉스, 큐리언트, 멜콘(정정+발행조건확정→최신 1건)

    // 옵토닉스 (stock_code 없음, 정상)
    OpenDartDisclosureItem optonix = findByCorpCode(result, "01658035");
    assertThat(optonix).isNotNull();
    assertThat(optonix.getCorpName()).isEqualTo("옵토닉스");
    assertThat(optonix.getStockCode()).isEqualTo("");
    assertThat(optonix.isEquitySecuritiesReport()).isTrue();
    assertThat(optonix.isCorrectionOrFinalized()).isFalse();

    // 큐리언트 (기재정정)
    OpenDartDisclosureItem curient = findByCorpCode(result, "01042650");
    assertThat(curient).isNotNull();
    assertThat(curient.getCorpName()).isEqualTo("큐리언트");
    assertThat(curient.getStockCode()).isEqualTo("115180");
    assertThat(curient.isCorrectionOrFinalized()).isTrue();
    assertThat(curient.getNormalizedReportName()).isEqualTo("증권신고서(지분증권)");

    // 멜콘 (기재정정 + 발행조건확정 → rcept_dt 최신인 발행조건확정 채택)
    OpenDartDisclosureItem melcon = findByCorpCode(result, "01028933");
    assertThat(melcon).isNotNull();
    assertThat(melcon.getCorpName()).isEqualTo("멜콘");
    assertThat(melcon.getRceptDt()).isEqualTo("20260929"); // 발행조건확정이 더 최신
    assertThat(melcon.isCorrectionOrFinalized()).isTrue();
    assertThat(melcon.getNormalizedReportName()).isEqualTo("증권신고서(지분증권)");
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status013_returnsEmptyList() throws IOException {
    // given
    String fixture = readFixture("ds006-empty.json"); // status 013 응답 재사용
    expectListRequest(fixture);

    // when
    List<OpenDartDisclosureItem> result = client.searchRecentEquitySecuritiesCompanies(7);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status011_throwsAuthenticationException()
      throws IOException {
    // given - 실제 오류 응답에는 list/total_count 등 필드가 없음
    String errorResponse = "{\"status\":\"011\",\"message\":\"인증키가 유효하지 않습니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("인증키가 유효하지 않습니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status013_noListField_returnsEmptyList()
      throws IOException {
    // given - list 필드 없는 013 응답 (실제 OpenDART 응답 형태)
    String errorResponse = "{\"status\":\"013\",\"message\":\"조회된 데이타가 없습니다.\"}";
    expectListRequest(errorResponse);

    // when
    List<OpenDartDisclosureItem> result = client.searchRecentEquitySecuritiesCompanies(7);

    // then
    mockServer.verify();
    assertThat(result).isEmpty();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status010_throwsAuthenticationException()
      throws IOException {
    // given - 010: 미등록 키
    String errorResponse = "{\"status\":\"010\",\"message\":\"미등록 키입니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("미등록 키입니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status012_throwsAuthenticationException()
      throws IOException {
    // given - 012: 사용할 수 없는 키
    String errorResponse = "{\"status\":\"012\",\"message\":\"사용할 수 없는 키입니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("사용할 수 없는 키입니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status901_throwsAuthenticationException()
      throws IOException {
    // given - 901: 계정 개인정보 보유기간 만료
    String errorResponse = "{\"status\":\"901\",\"message\":\"계정 개인정보 보유기간이 만료되었습니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartAuthenticationException.class)
        .hasMessageContaining("계정 개인정보 보유기간이 만료되었습니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status020_throwsApiException() throws IOException {
    // given - 020: 요청 제한 초과
    String errorResponse = "{\"status\":\"020\",\"message\":\"요청 제한을 초과했습니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageContaining("요청 제한을 초과했습니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status800_throwsApiException() throws IOException {
    // given - 800: 시스템 점검
    String errorResponse = "{\"status\":\"800\",\"message\":\"시스템 점검 중입니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageContaining("시스템 점검 중입니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_status021_throwsApiException() throws IOException {
    // given - 021: 기타 오류
    String errorResponse = "{\"status\":\"021\",\"message\":\"기타 오류입니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    assertThatThrownBy(() -> client.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(OpenDartApiException.class)
        .hasMessageContaining("기타 오류입니다");
    mockServer.verify();
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_missingApiKey_throwsIllegalStateException() {
    // given
    OpenDartApiProperties emptyProps = new OpenDartApiProperties("", "https://opendart.fss.or.kr");
    OpenDartCorpDisclosureClient clientWithEmptyKey =
        new OpenDartCorpDisclosureClient(RestClient.builder(), emptyProps);

    // when & then
    assertThatThrownBy(() -> clientWithEmptyKey.searchRecentEquitySecuritiesCompanies(7))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OpenDART API 키가 설정되지 않았습니다");
  }

  @Test
  void searchRecentEquitySecuritiesCompanies_exceptionMessageDoesNotContainApiKey()
      throws IOException {
    // given - 실제 오류 응답에는 list/total_count 등 필드가 없음
    String errorResponse = "{\"status\":\"011\",\"message\":\"인증키가 유효하지 않습니다.\"}";
    expectListRequest(errorResponse);

    // when & then
    try {
      client.searchRecentEquitySecuritiesCompanies(7);
    } catch (OpenDartAuthenticationException e) {
      // then
      String message = e.getMessage();
      assertThat(message).doesNotContain("test-api-key");
      assertThat(message).doesNotContain("crtfc_key");
    }
    mockServer.verify();
  }

  @Test
  void searchByDateRange_pagination_twoPages() throws IOException {
    // given - 첫 페이지 (total_page=2)
    String page1Fixture = withTotalPage(readFixture("ds001-c001.json"), 2);
    // 두 번째 페이지 - 빈 리스트, page_no=2
    String page2Fixture =
        withEmptyList(withPageNo(withTotalPage(readFixture("ds001-c001.json"), 2), 2));
    expectListRequestMultiplePages(page1Fixture, page2Fixture);

    // when
    List<OpenDartDisclosureItem> result =
        client.searchByDateRange(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 29));

    // then
    mockServer.verify();
    assertThat(result).hasSize(3);
  }

  private OpenDartDisclosureItem findByCorpCode(
      List<OpenDartDisclosureItem> list, String corpCode) {
    return list.stream().filter(i -> corpCode.equals(i.getCorpCode())).findFirst().orElse(null);
  }
}
