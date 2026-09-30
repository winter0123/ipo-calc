package com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.dto.UstockPlusCalendarItem;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ustockplus 공모주 캘린더 조회 클라이언트.
 *
 * <p>비공식 API — ustockplus/네이버가 자사 프론트 전용으로 제공, 공식 계약 없음. 장애 시 OpenDART 데이터만으로 폴백.
 *
 * <p>Origin, Referer, x-app-device 세 헤더가 모두 있어야 한다. 하나라도 빠지면 400 LOGIN_REQUIRED가 오며, 특히
 * x-app-device를 빠뜨리기 쉽다.
 */
public class UstockPlusCalendarClient {

  private static final Logger log = LoggerFactory.getLogger(UstockPlusCalendarClient.class);

  private static final String ORIGIN = "https://ustock.naver.com";
  private static final String REFERER = "https://ustock.naver.com/service/ipo";
  private static final String PATH = "/v2/ipo/calendars";
  private static final long MAX_RANGE_DAYS = 89;
  private static final long REQUEST_INTERVAL_MS = 70;
  private static final Map<String, Integer> SLOTS =
      Map.of("TO_BE_LISTED", 0, "ALLOTMENT_DATE", 1, "REFUND_DATE", 2);

  private final RestClient restClient;
  private long lastRequestTimeMs = 0;

  /**
   * 기본 URL과 고정 헤더 세 개를 적용한 클라이언트를 만든다.
   *
   * @param builder 타임아웃 등이 설정된 RestClient 빌더
   * @param properties ustockplus 설정
   */
  public UstockPlusCalendarClient(RestClient.Builder builder, UstockPlusApiProperties properties) {
    this.restClient =
        builder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.ORIGIN, ORIGIN)
            .defaultHeader(HttpHeaders.REFERER, REFERER)
            .defaultHeader("x-app-device", "WEB")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  /**
   * 기간 내 상장예정일·배정일·환불일을 종목코드별로 묶어 돌려준다.
   *
   * <p>호출 실패는 WARN 로그만 남기고 빈 리스트를 돌려준다.
   *
   * @param startDate 시작일(포함)
   * @param endDate 종료일(포함)
   * @return 종목별 일정 목록. 실패 시 빈 리스트
   * @throws IllegalArgumentException startDate가 endDate보다 늦거나 기간이 89일을 넘을 때
   */
  public List<UstockPlusScheduleDates> fetchScheduleDates(LocalDate startDate, LocalDate endDate) {
    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("startDate는 endDate보다 늦을 수 없습니다.");
    }
    if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_RANGE_DAYS) {
      throw new IllegalArgumentException("조회 기간은 최대 " + MAX_RANGE_DAYS + "일입니다.");
    }
    try {
      enforceRateLimit();
      CalendarResponse response =
          restClient
              .get()
              .uri(
                  uri ->
                      uri.path(PATH)
                          .queryParam("startDate", startDate)
                          .queryParam("endDate", endDate)
                          .build())
              .retrieve()
              .body(CalendarResponse.class);
      if (response == null || response.ipoCalendarResponses() == null) {
        log.warn("ustockplus 응답이 비어 있습니다 (startDate={}, endDate={})", startDate, endDate);
        return Collections.emptyList();
      }
      return group(response.ipoCalendarResponses());
    } catch (RestClientResponseException e) {
      log.warn("ustockplus 호출 실패: status={}", e.getStatusCode().value());
      return Collections.emptyList();
    } catch (RuntimeException e) {
      log.warn("ustockplus 호출 실패: {}", e.getClass().getSimpleName());
      return Collections.emptyList();
    }
  }

  private static List<UstockPlusScheduleDates> group(List<UstockPlusCalendarItem> items) {
    Map<String, LocalDate[]> byStock = new LinkedHashMap<>();
    for (UstockPlusCalendarItem item : items) {
      if (item == null
          || item.stockCode() == null
          || item.stockCode().isBlank()
          || item.calendarStockType() == null
          || item.startDate() == null) {
        continue;
      }
      Integer slot = SLOTS.get(item.calendarStockType());
      if (slot == null) {
        continue;
      }
      LocalDate[] dates = byStock.computeIfAbsent(item.stockCode(), k -> new LocalDate[3]);
      if (dates[slot] == null || item.startDate().isAfter(dates[slot])) {
        dates[slot] = item.startDate();
      }
    }
    return byStock.entrySet().stream()
        .map(
            e ->
                new UstockPlusScheduleDates(
                    e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
        .toList();
  }

  private synchronized void enforceRateLimit() {
    long wait = REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestTimeMs);
    if (wait > 0) {
      try {
        Thread.sleep(wait);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("요청 간격 대기 중 인터럽트", e);
      }
    }
    lastRequestTimeMs = System.currentTimeMillis();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CalendarResponse(List<UstockPlusCalendarItem> ipoCalendarResponses) {}
}
