package com.vibeaiteam.brokerintegration.service.matching;

import java.util.List;

/** 해당 증권계좌의 배정 1건(IPO 종목코드 포함). */
public record MatchCandidate(
    Long allocationId,
    String stockCode,
    long allocatedQuantity,
    List<ExistingSale> existingSales) {}
