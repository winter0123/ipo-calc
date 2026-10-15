package com.ipocalc.brokerintegration.service.matching;

/** 매칭 실패 사유. */
public enum UnmatchedReason {
  /** 해당 종목에 대한 배정이 존재하지 않음. */
  NO_ALLOCATION_FOR_STOCK,

  /** 동일한 종목코드에 대해 2개 이상의 배정이 존재하여 모호함. */
  AMBIGUOUS_ALLOCATIONS,

  /** 이미 해당 배정에 대해 매도가 등록되어 있음. */
  ALREADY_REGISTERED,

  /** 분할 매도 합산이 브로커 집계와 일치하지 않음. */
  SPLIT_SALE_MISMATCH,

  /** 배정 수량을 초과하는 매도 시도. */
  QUANTITY_EXCEEDS_ALLOCATION
}
