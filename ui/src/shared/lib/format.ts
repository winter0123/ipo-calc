/** 금액을 `1,234원`으로. null 처리는 호출부에서 한다. */
export const formatWon = (v: number): string => `${v.toLocaleString('ko-KR')}원`

/** 주식수를 `1,234주`로. null 처리는 호출부에서 한다. */
export const formatShares = (v: number): string => `${v.toLocaleString('ko-KR')}주`
