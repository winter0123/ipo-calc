package com.vibeaiteam.brokerintegration.service.matching;

import java.time.LocalDate;

/** 브로커가 알려준 일자×종목 매도 집계. */
public record BrokerSaleSummary(
    LocalDate tradeDate, String stockCode, long quantity, long amount, long fee, long tax) {}
