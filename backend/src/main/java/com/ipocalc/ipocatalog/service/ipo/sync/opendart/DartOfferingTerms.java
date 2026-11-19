package com.ipocalc.ipocatalog.service.ipo.sync.opendart;

import java.time.LocalDate;

/** 원문 파싱으로 확인된 공모 조건(밴드·확정가·일정·수량). */
public record DartOfferingTerms(
    String sourceReceiptNo,
    int parserVersion,
    Long lowerBandPrice,
    Long upperBandPrice,
    Long fixedPrice,
    LocalDate subscriptionStartDate,
    LocalDate subscriptionEndDate,
    LocalDate allotmentDate,
    LocalDate refundDate,
    Long offeringShareCount) {

  public static final int PARSER_VERSION = 2;

  /** 밴드가 유효한지 확인: 두 값이 모두 있고 0 < lower < upper일 때만 true. */
  public boolean hasValidBand() {
    return lowerBandPrice != null
        && upperBandPrice != null
        && lowerBandPrice > 0
        && upperBandPrice > 0
        && lowerBandPrice < upperBandPrice;
  }

  /** 확정가가 유효한지 확인: fixedPrice가 양수일 때만 true. */
  public boolean hasConfirmedFixedPrice() {
    return fixedPrice != null && fixedPrice > 0;
  }
}
