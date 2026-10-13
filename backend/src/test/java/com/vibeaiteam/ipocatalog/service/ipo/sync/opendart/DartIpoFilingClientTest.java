package com.vibeaiteam.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.EquityOffering;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient.FilingRef;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** {@link DartIpoFilingClient} 테스트. */
class DartIpoFilingClientTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

  private MockRestServiceServer mockServer;
  private RestClient restClient;
  private DartIpoFilingClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();
    restClient =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    client =
        new DartIpoFilingClient(
            restClient, new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr"));
  }

  private static String item(String rceptNo, String name, String date) {
    return "{\"rcept_no\":\"%s\",\"report_nm\":\"%s\",\"rcept_dt\":\"%s\"}"
        .formatted(rceptNo, name, date);
  }

  private void expectJson(String urlPart, String json) {
    mockServer
        .expect(requestTo(containsString(urlPart)))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
  }

  private static byte[] zip(String... namesAndContents) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (int i = 0; i < namesAndContents.length; i += 2) {
        zip.putNextEntry(new ZipEntry(namesAndContents[i]));
        zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  @Test
  void 투자설명서가_있으면_증권신고서보다_우선하고_가장_최신을_고른다() {
    expectJson(
        "/api/list.json",
        "{\"status\":\"000\",\"total_page\":1,\"list\":["
            + item("20261001000001", "증권신고서(지분증권)", "20261001")
            + ","
            + item("20261005000002", "투자설명서", "20261005")
            + ","
            + item("20261007000003", "[기재정정]투자설명서", "20261007")
            + "]}");

    assertThat(client.findLatestOfferingDocument("00123456", TODAY))
        .contains(new FilingRef("20261007000003", "[기재정정]투자설명서", LocalDate.of(2026, 10, 7)));
  }

  @Test
  void 접수일이_같으면_접수번호가_큰_쪽을_고른다() {
    expectJson(
        "/api/list.json",
        "{\"status\":\"000\",\"total_page\":1,\"list\":["
            + item("20261007000001", "투자설명서", "20261007")
            + ","
            + item("20261007000009", "[기재정정]투자설명서", "20261007")
            + "]}");

    assertThat(client.findLatestOfferingDocument("00123456", TODAY))
        .map(FilingRef::rceptNo)
        .contains("20261007000009");
  }

  @Test
  void 투자설명서가_없으면_증권신고서를_고른다() {
    expectJson(
        "/api/list.json",
        "{\"status\":\"000\",\"total_page\":1,\"list\":["
            + item("20261001000001", "증권신고서(지분증권)", "20261001")
            + ","
            + item("20261002000002", "[기재정정]증권신고서(지분증권)", "20261002")
            + ","
            + item("20261003000003", "사업보고서", "20261003")
            + "]}");

    assertThat(client.findLatestOfferingDocument("00123456", TODAY))
        .map(FilingRef::rceptNo)
        .contains("20261002000002");
  }

  @Test
  void 관련_공시가_없거나_데이터없음이면_empty() {
    expectJson(
        "/api/list.json",
        "{\"status\":\"000\",\"total_page\":1,\"list\":[" + item("1", "사업보고서", "20261003") + "]}");
    expectJson("/api/list.json", "{\"status\":\"013\",\"message\":\"조회된 데이터가 없습니다.\"}");

    assertThat(client.findLatestOfferingDocument("00123456", TODAY)).isEmpty();
    assertThat(client.findLatestOfferingDocument("00123456", TODAY)).isEmpty();
  }

  @Test
  void 문서_ZIP에서_가장_큰_항목을_문자열로_꺼낸다() throws IOException {
    mockServer
        .expect(requestTo(containsString("rcept_no=20261007000003")))
        .andRespond(
            withSuccess(
                zip("a.xml", "짧음", "b.xml", "가장 긴 본문입니다"), MediaType.APPLICATION_OCTET_STREAM));

    assertThat(client.downloadDocument("20261007000003")).contains("가장 긴 본문입니다");
  }

  @Test
  void 문서가_JSON_오류_응답이면_empty() {
    expectJson("/api/document.xml", "{\"status\":\"014\",\"message\":\"파일이 존재하지 않습니다.\"}");

    assertThat(client.downloadDocument("20261007000003")).isEmpty();
  }

  @Test
  void 압축_해제_합계가_한도를_넘으면_empty() throws IOException {
    DartIpoFilingClient small =
        new DartIpoFilingClient(
            restClient,
            new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr"),
            1024);
    mockServer
        .expect(requestTo(containsString("/api/document.xml")))
        .andRespond(
            withSuccess(zip("bomb.xml", "0".repeat(2048)), MediaType.APPLICATION_OCTET_STREAM));

    assertThat(small.downloadDocument("20261007000003")).isEmpty();
  }

  @Test
  void 증권신고서_주요정보에서_액면가와_인수인을_매핑한다() {
    expectJson(
        "/api/estkRs.json",
        """
        {"status":"000","group":[
          {"title":"증권의종류","list":[{"stksen":"보통주","stkcnt":"4,000,000","fv":"100"}]},
          {"title":"인수인정보","list":[
            {"actsen":"대표","actnmn":"케이비증권","udtcnt":"4,000,000"},
            {"actsen":"인수","actnmn":"신영증권","udtcnt":"-"}]}
        ]}
        """);

    EquityOffering offering = client.fetchEquityOffering("00123456", TODAY).orElseThrow();

    assertThat(offering.parValue()).isEqualTo(100L);
    assertThat(offering.underwriters())
        .extracting("name", "role", "shares")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("케이비증권", "대표", 4_000_000L),
            org.assertj.core.groups.Tuple.tuple("신영증권", "인수", null));
  }

  @Test
  void 액면가가_대시면_null이고_013이면_empty() {
    expectJson(
        "/api/estkRs.json",
        "{\"status\":\"000\",\"group\":[{\"title\":\"증권의종류\",\"list\":[{\"fv\":\"-\"}]}]}");
    expectJson("/api/estkRs.json", "{\"status\":\"013\"}");

    assertThat(client.fetchEquityOffering("00123456", TODAY))
        .map(EquityOffering::parValue)
        .isEmpty();
    assertThat(client.fetchEquityOffering("00123456", TODAY)).isEmpty();
  }

  @Test
  void 키_없음_형식_오류는_호출_없이_empty() {
    DartIpoFilingClient noKey =
        new DartIpoFilingClient(
            restClient, new OpenDartApiProperties("", "https://opendart.fss.or.kr"));

    assertThat(noKey.findLatestOfferingDocument("00123456", TODAY)).isEmpty();
    assertThat(noKey.fetchEquityOffering("00123456", TODAY)).isEmpty();
    assertThat(noKey.downloadDocument("20261007000003")).isEmpty();
    assertThat(client.findLatestOfferingDocument("123", TODAY)).isEmpty();
    assertThat(client.fetchEquityOffering(null, TODAY)).isEmpty();
    assertThat(client.downloadDocument("2026100700")).isEmpty();
    mockServer.verify(); // 기대한 호출이 없으므로 어떤 요청도 나가지 않았음을 확인
  }
}
