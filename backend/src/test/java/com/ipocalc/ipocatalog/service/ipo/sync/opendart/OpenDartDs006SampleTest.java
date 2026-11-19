package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import com.ipocalc.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** 사용자가 제공한 실제 형식의 DS006 그룹 응답 회귀 테스트. */
class OpenDartDs006SampleTest {

  private List<OpenDartEquitySecurityDetail> parseFixture(String corp, String filename)
      throws Exception {
    RestTemplate template = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
    server
        .expect(requestTo(Matchers.containsString("corp_code=" + corp)))
        .andRespond(
            withSuccess(
                Files.readString(Path.of("src/test/resources/opendart", filename)),
                MediaType.APPLICATION_JSON));
    OpenDartEquitySecuritiesClient client =
        new OpenDartEquitySecuritiesClient(
            RestClient.builder()
                .baseUrl("https://opendart.fss.or.kr")
                .requestFactory(template.getRequestFactory())
                .build(),
            new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr"),
            new RequestThrottle(0));
    List<OpenDartEquitySecurityDetail> result =
        client.getEquitySecurityDetail(
            corp, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 19));
    server.verify();
    return result;
  }

  @Test
  void optonicsFullGroupResponseMergesSameReceiptWithoutMisreadingOtherGroups()
      throws Exception {
    var detail =
        parseFixture("01658035", "ds006-user-optonics-20260929000347.json").get(0);
    assertThat(detail.getRceptNo()).isEqualTo("20260929000347");
    assertThat(detail.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(detail.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(detail.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(detail.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(detail.getOfferingShareCount()).isEqualTo(2_200_000L);
    assertThat(detail.getOfferPrice()).isEqualTo(10_000L);
    assertThat(detail.getTotalOfferingAmount()).isEqualTo(22_000_000_000L);
    assertThat(detail.getActnmn()).isEqualTo("한국투자증권");
  }

  @Test
  void simwonMinimalResponseToleratesMissingOptionalGroups() throws Exception {
    var detail = parseFixture("02038432", "ds006-user-simwon-20261012000149.json").get(0);
    assertThat(detail.getRceptNo()).isEqualTo("20261012000149");
    assertThat(detail.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(detail.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(detail.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 9));
    assertThat(detail.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(detail.getOfferingShareCount()).isEqualTo(1_500_000L);
    assertThat(detail.getActnmn()).isNull();
  }
}
