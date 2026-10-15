package com.ipocalc.brokerintegration.controller.sync.dto;

import java.util.List;

/**
 * 동기화 미리보기 결과. 조회만 하며 아무것도 저장하지 않는다.
 *
 * @param matched 매칭에 성공한 건(사용자가 값을 고쳐 확정할 수 있다)
 * @param unmatched 매칭에 실패한 건(사유 포함, 수동 등록 안내 대상)
 * @param allocationsWithoutStockCode 종목코드가 없어 자동 매칭이 불가능한 배정 ID 목록
 */
public record SyncPreviewResponse(
    List<SyncPreviewItem> matched,
    List<SyncPreviewItem> unmatched,
    List<Long> allocationsWithoutStockCode) {}
