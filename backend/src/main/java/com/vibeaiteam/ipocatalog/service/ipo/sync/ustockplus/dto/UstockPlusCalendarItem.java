package com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * ustockplus 캘린더 응답 항목. 모르는 필드는 무시한다.
 *
 * @param stockCode 종목코드
 * @param stockName 종목명
 * @param calendarStockType 일정 종류
 * @param startDate 시작일
 * @param endDate 종료일
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UstockPlusCalendarItem(
    String stockCode,
    String stockName,
    String calendarStockType,
    LocalDate startDate,
    LocalDate endDate) {}
