package com.ipocalc.ipocatalog.controller.holiday;

import com.ipocalc.ipocatalog.controller.holiday.dto.MarketHolidayResponse;
import com.ipocalc.ipocatalog.service.holiday.MarketHolidayQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시장 휴장일 공개 조회 API.
 *
 * <p>로그인 없이 누구나 연도별 휴장일(법정 공휴일, 대체공휴일, KRX 휴장일)을 조회할 수 있다. 공모주 달력 표시에 사용한다.
 */
@RestController
@RequestMapping("/market-holidays")
@RequiredArgsConstructor
public class MarketHolidayController {

  private final MarketHolidayQueryService marketHolidayQueryService;

  /**
   * 연도별 휴장일 목록을 조회한다.
   *
   * <p>year 파라미터가 없으면 현재 연도(서울 시간대 기준)의 휴장일을 반환한다. 해당 연도 데이터가 없으면 빈 배열을 반환한다(200).
   *
   * @param year 조회할 연도 (선택, 2000~2100)
   * @return [{ "date": "2026-10-09", "name": "한글날" }, ...] (날짜 오름차순)
   */
  @GetMapping
  public ResponseEntity<List<MarketHolidayResponse>> getHolidays(
      @RequestParam(required = false) Integer year) {
    List<MarketHolidayResponse> holidays;
    if (year != null) {
      holidays = marketHolidayQueryService.getHolidaysByYear(year);
    } else {
      holidays = marketHolidayQueryService.getHolidaysForCurrentYear();
    }
    return ResponseEntity.ok(holidays);
  }
}
