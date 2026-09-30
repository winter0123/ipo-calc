package com.vibeaiteam.brokerintegration.service.matching;

import java.time.LocalDate;

/** 우리 쪽에 이미 등록된 Sale. */
public record ExistingSale(
    Long saleId, LocalDate saleDate, long quantity, long amount, long fee, long tax) {}
