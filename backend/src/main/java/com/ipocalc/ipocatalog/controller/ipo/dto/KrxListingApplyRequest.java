package com.ipocalc.ipocatalog.controller.ipo.dto;

import java.time.LocalDate;

/**
 * KIND 공고 적용 요청. 미리보기에서 확인한 후보를 강제하기 위해 기대값을 모두 받는다.
 *
 * <p>기대값이 없거나 재조회 결과와 다르면 서비스가 충돌로 거절하고 쓰기를 하지 않는다. 하위 호환 생성자나 별칭은 두지 않는다.
 */
public record KrxListingApplyRequest(
    String noticeUrl,
    String expectedDartReceiptNo,
    LocalDate expectedDartListingDate,
    String expectedStockCode,
    Long expectedIssuedShares,
    Long expectedIssuePrice,
    LocalDate expectedKrxListingDate,
    String expectedNoticeHash) {}
