package com.ipocalc.ipocatalog.service.ipo.krx;

import java.time.LocalDate;

/** 공식 KIND 추가상장 공고에서 읽은 KRX 확정 후보. */
public record KrxListingNotice(
    String noticeUrl,
    String companyName,
    String stockCode,
    String stockType,
    String reason,
    String roundLabel,
    Long issuedShares,
    Long issuePrice,
    LocalDate listingDate,
    LocalDate issueDate) {}
