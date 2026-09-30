package com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus;

import java.time.LocalDate;

/**
 * 종목별 상장예정일·배정일·환불일. 날짜는 모두 null일 수 있다.
 *
 * @param stockCode 종목코드
 * @param stockName 종목명
 * @param listingDate 상장예정일
 * @param allotmentDate 배정일
 * @param refundDate 환불일
 */
public record UstockPlusScheduleDates(
    String stockCode,
    String stockName,
    LocalDate listingDate,
    LocalDate allotmentDate,
    LocalDate refundDate) {}
