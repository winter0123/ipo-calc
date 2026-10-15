package com.ipocalc.ipocatalog.controller.holiday.dto;

import java.time.LocalDate;

/** 시장 휴장일 응답 DTO. */
public record MarketHolidayResponse(LocalDate date, String name) {}
