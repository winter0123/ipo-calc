package com.vibeaiteam.brokerintegration.controller.sync.dto;

import com.vibeaiteam.brokerintegration.service.matching.UnmatchedReason;
import java.time.LocalDate;

/**
 * 증권사 매도 집계 1건과 매칭 결과.
 *
 * @param tradeDate 매도 일자
 * @param stockCode 종목코드
 * @param quantity 매도 수량
 * @param amount 매도 금액
 * @param price 매도 단가(금액 ÷ 수량, 반올림)
 * @param fee 수수료
 * @param tax 세금
 * @param allocationId 매칭된(또는 특정된) 배정 ID, 없으면 null
 * @param reason 매칭 실패 사유, 성공이면 null
 * @param allocationHasActiveSale 대상 배정에 활성 매도가 이미 있는지(공모주수수료 이중 계상 방지용)
 */
public record SyncPreviewItem(
    LocalDate tradeDate,
    String stockCode,
    long quantity,
    long amount,
    long price,
    long fee,
    long tax,
    Long allocationId,
    UnmatchedReason reason,
    boolean allocationHasActiveSale) {}
