package com.vibeaiteam.brokerintegration.adapter.kis.dto;

import java.util.List;

/**
 * 기간별 매매손익 현황 조회 결과.
 *
 * <p>{@link KisPeriodTradeProfitResponse}(브로커 스펙 그대로)를 감싸 포트가 브로커 DTO를 직접 반환하지 않도록 한다. SF-4b에서 브로커
 * 중립 모델로 매핑하며 그때 이 타입을 대체·확장한다.
 *
 * @param details 일자×종목 집계 상세 목록 (비어 있을 수 있음)
 * @param summary 요약 정보 (응답에 없으면 null)
 */
public record KisPeriodTradeProfit(
    List<KisPeriodTradeProfitDetail> details, KisPeriodTradeProfitSummary summary) {}
