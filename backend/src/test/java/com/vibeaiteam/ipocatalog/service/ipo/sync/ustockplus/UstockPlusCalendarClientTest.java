package com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** {@link UstockPlusCalendarClient} 단위 테스트. 네트워크·Spring 컨텍스트를 쓰지 않는다. */
class UstockPlusCalendarClientTest {

  private static final LocalDate START = LocalDate.of(2026, 10, 1);
  private static final LocalDate END = LocalDate.of(2026, 10, 31);

  private MockRestServiceServer server;
  private UstockPlusCalendarClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new UstockPlusCalendarClient(
            builder, new UstockPlusApiProperties("https://api.ustockplus.com"));
  }

  private static String item(String code, String type, String date) {
    return """
        {"stockCode":"%s","stockName":"n","isAvail":true,"logoUrl":"","calendarStockType":"%s",
         "startDate":"%s","endDate":"%s","extraInfo":{"finalOfferPrice":1}}"""
        .formatted(code, type, date, date);
  }

  private static String body(String... items) {
    return "{\"ipoCalendarResponses\":[" + String.join(",", items) + "]}";
  }

  @Test
  void mapsThreeTypesAndIgnoresOthers() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(
            withSuccess(
                body(
                    item("250030", "TO_BE_LISTED", "2026-10-02"),
                    item("250030", "ALLOTMENT_DATE", "2026-10-06"),
                    item("250030", "REFUND_DATE", "2026-10-10"),
                    item("250030", "SUBMIT_REPORT", "2026-08-27"),
                    item("170190", "UNKNOWN_TYPE", "2026-10-03")),
                MediaType.APPLICATION_JSON));

    List<UstockPlusScheduleDates> result = client.fetchScheduleDates(START, END);

    assertThat(result)
        .containsExactly(
            new UstockPlusScheduleDates(
                "250030",
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 10)));
  }

  @Test
  void picksLatestStartDateForDuplicateTypeAndDropsBlankStockCode() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(
            withSuccess(
                body(
                    item("250030", "TO_BE_LISTED", "2026-10-02"),
                    item("250030", "TO_BE_LISTED", "2026-10-09"),
                    item("250030", "TO_BE_LISTED", "2026-10-05"),
                    item("", "TO_BE_LISTED", "2026-10-02")),
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchScheduleDates(START, END))
        .containsExactly(
            new UstockPlusScheduleDates("250030", LocalDate.of(2026, 10, 9), null, null));
  }

  @Test
  void sendsRequiredHeadersAndDateQuery() {
    server
        .expect(method(HttpMethod.GET))
        .andExpect(requestTo(Matchers.startsWith("https://api.ustockplus.com/v2/ipo/calendars")))
        .andExpect(queryParam("startDate", "2026-10-01"))
        .andExpect(queryParam("endDate", "2026-10-31"))
        .andExpect(header("Origin", "https://ustock.naver.com"))
        .andExpect(header("Referer", "https://ustock.naver.com/service/ipo"))
        .andExpect(header("x-app-device", "WEB"))
        .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

    assertThat(client.fetchScheduleDates(START, END)).isEmpty();
    server.verify();
  }

  @Test
  void returnsEmptyOnLoginRequired400() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"I0003\",\"message\":\"LOGIN_REQUIRED\"}"));

    assertThat(client.fetchScheduleDates(START, END)).isEmpty();
  }

  @Test
  void returnsEmptyOnServerError() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(withServerError());

    assertThat(client.fetchScheduleDates(START, END)).isEmpty();
  }

  @Test
  void returnsEmptyOnBrokenJson() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

    assertThat(client.fetchScheduleDates(START, END)).isEmpty();
  }

  @Test
  void returnsEmptyOnConnectionFailure() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });

    assertThat(client.fetchScheduleDates(START, END)).isEmpty();
  }

  @Test
  void keepsMinimumIntervalBetweenConsecutiveCalls() {
    server
        .expect(ExpectedCount.twice(), requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

    client.fetchScheduleDates(START, END);
    long before = System.nanoTime();
    client.fetchScheduleDates(START, END);
    long elapsedMs = (System.nanoTime() - before) / 1_000_000;

    assertThat(elapsedMs).isGreaterThanOrEqualTo(60);
  }

  @Test
  void rejectsRangeOver89DaysAndReversedRange() {
    assertThatThrownBy(() -> client.fetchScheduleDates(START, START.plusDays(90)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.fetchScheduleDates(END, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsExactly89DayRange() {
    server
        .expect(requestTo(Matchers.containsString("/v2/ipo/calendars")))
        .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

    assertThat(client.fetchScheduleDates(START, START.plusDays(89))).isEmpty();
  }
}
