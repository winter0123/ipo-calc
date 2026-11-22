package com.ipocalc.ipocatalog.service.ipo.krx;

import java.time.LocalDate;
import java.util.List;

/** KIND 공고 미리보기 결과. 읽기 전용이며 mutation을 하지 않는다. */
public record KrxListingReconciliationPreview(
    Long ipoId,
    String stockCode,
    LocalDate currentListingDate,
    String currentEvidenceType,
    String currentEvidenceReceipt,
    KrxListingNotice notice,
    String dartReceiptNo,
    LocalDate dartPaymentDate,
    Long dartIssuedShares,
    Long dartIssuePrice,
    LocalDate dartListingDate,
    LocalDate dartOriginalListingDate,
    boolean stockCodeMatches,
    boolean stockTypeCommon,
    boolean reasonAllowed,
    boolean singleMatch,
    boolean issuedSharesMatch,
    boolean issuePriceMatch,
    boolean datesValid,
    boolean expired,
    boolean applyAllowed,
    boolean idempotent,
    List<String> reasons,
    String reviewStatus,
    String expectedDartReceiptNo,
    LocalDate expectedDartListingDate,
    String expectedStockCode,
    Long expectedIssuedShares,
    Long expectedIssuePrice,
    LocalDate expectedKrxListingDate,
    String expectedNoticeHash) {}
