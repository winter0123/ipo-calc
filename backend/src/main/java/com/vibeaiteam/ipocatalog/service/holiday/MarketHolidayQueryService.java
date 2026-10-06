package com.vibeaiteam.ipocatalog.service.holiday;

import com.vibeaiteam.ipocatalog.controller.holiday.dto.MarketHolidayResponse;
import com.vibeaiteam.ipocatalog.domain.model.holiday.MarketHoliday;
import com.vibeaiteam.ipocatalog.exception.MarketHolidayValidationException;
import com.vibeaiteam.ipocatalog.repository.holiday.MarketHolidayRepository;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시장 휴장일 조회 서비스.
 *
 * <p>DB에 저장된 휴장일을 연도별로 조회하여 응답 DTO로 변환한다.
 */
@Service
@RequiredArgsConstructor
public class MarketHolidayQueryService {

  private final MarketHolidayRepository marketHolidayRepository;
  private final Clock clock;

  /**
   * 지정된 연도의 휴장일 목록을 날짜 오름차순으로 조회한다.
   *
   * @param year 조회할 연도 (2000~2100)
   * @return 휴장일 응답 DTO 리스트 (비어 있을 수 있음)
   * @throws MarketHolidayValidationException year가 유효 범위를 벗어난 경우
   */
  @Transactional(readOnly = true)
  public List<MarketHolidayResponse> getHolidaysByYear(int year) {
    validateYear(year);
    List<MarketHoliday> holidays = marketHolidayRepository.findByYear(year);
    return holidays.stream()
        .map(h -> new MarketHolidayResponse(h.getHolidayDate(), h.getName()))
        .toList();
  }

  /**
   * 현재 연도(서울 시간대 기준)의 휴장일 목록을 조회한다.
   *
   * @return 휴장일 응답 DTO 리스트 (비어 있을 수 있음)
   */
  @Transactional(readOnly = true)
  public List<MarketHolidayResponse> getHolidaysForCurrentYear() {
    int currentYear = Year.now(clock).getValue();
    return getHolidaysByYear(currentYear);
  }

  private void validateYear(int year) {
    if (year < 2000 || year > 2100) {
      throw new MarketHolidayValidationException("연도는 2000~2100 사이여야 합니다.");
    }
  }
}
