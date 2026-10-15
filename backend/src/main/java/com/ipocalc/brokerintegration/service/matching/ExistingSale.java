package com.ipocalc.brokerintegration.service.matching;

import java.time.LocalDate;

/**
 * 우리 쪽에 이미 등록된 Sale.
 *
 * <p>중복 판정에는 수량·금액만 쓴다. fee(공모주수수료)와 tax(세금 및 기타 수수료)는 증권사 값과 의미가 달라 비교하지 않으며 참고용으로만 담는다.
 */
public record ExistingSale(
    Long saleId, LocalDate saleDate, long quantity, long amount, long fee, long tax) {}
