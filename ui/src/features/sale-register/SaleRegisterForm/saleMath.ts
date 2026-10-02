/** 공모주수수료 기본값(원). 사용자가 고칠 수 있다. */
export const DEFAULT_IPO_FEE = 2000

/** 상장일 매도의 기본 시각(장 시작). */
const DEFAULT_SALE_TIME = '09:00'

/** 공모가 대비 매도 차익(수수료 반영 전): (매도단가 − 공모가) × 수량. */
export function grossGain(price: number, quantity: number, ipoPrice: number): number {
  return (price - ipoPrice) * quantity
}

/**
 * 실현손익 = 매도단가 × 수량 − 공모가 × 수량 − 세금 및 기타 수수료.
 * 증권사 앱의 실현손익과 같은 개념이며 공모주수수료는 빼지 않는다.
 */
export function realizedProfitFromFees(
  price: number,
  quantity: number,
  ipoPrice: number,
  otherFees: number,
): number {
  return grossGain(price, quantity, ipoPrice) - otherFees
}

/** 증권사 앱의 실현손익이 주어졌을 때 거꾸로 구한 세금 및 기타 수수료. */
export function otherFeesFromRealizedProfit(
  price: number,
  quantity: number,
  ipoPrice: number,
  realizedProfit: number,
): number {
  return grossGain(price, quantity, ipoPrice) - realizedProfit
}

/** 최종수익금 = 실현손익 − 공모주수수료. */
export function finalProfit(realizedProfit: number, ipoFee: number): number {
  return realizedProfit - ipoFee
}

/** 공모가: 확정 공모가가 있으면 그 값, 없으면 배정단가, 둘 다 없으면 null. */
export function resolveIpoPrice(
  fixedPrice: number | null | undefined,
  allocationPrice: number | null | undefined,
): number | null {
  if (fixedPrice != null && fixedPrice > 0) return fixedPrice
  if (allocationPrice != null && allocationPrice > 0) return allocationPrice
  return null
}

/** 상장일('YYYY-MM-DD' 또는 ISO)을 datetime-local 값('YYYY-MM-DDT09:00')으로 바꾼다. */
export function listingDateTime(listingDate: string | null | undefined): string | undefined {
  if (!listingDate) return undefined
  return `${listingDate.slice(0, 10)}T${DEFAULT_SALE_TIME}`
}
