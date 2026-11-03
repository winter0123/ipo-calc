import type { SaleRegisterRequest } from './types.ts'

/** 공모주수수료 기본값(원). 사용자가 고칠 수 있다. */
export const DEFAULT_IPO_FEE = 2000

/** 공모가 대비 매도 차익(수수료 반영 전): (매도단가 − 공모가) × 수량. */
export function grossGain(price: number, quantity: number, ipoPrice: number): number {
  return (price - ipoPrice) * quantity
}

/**
 * 매도 수량에 대한 취득금액을 계산한다.
 * - allocatedAmount가 있고 allocatedQuantity > 0이면: roundHalfUp(allocatedAmount * saleQuantity / allocatedQuantity)
 * - allocatedAmount가 있고 allocatedQuantity <= 0이면: 0 (fixedPrice fallback 안 함)
 * - allocatedAmount가 없으면: fixedPrice * saleQuantity
 * - 둘 다 없으면: 0
 */
export function acquisitionAmountForSale(
  saleQuantity: number,
  fixedPrice: number | null | undefined,
  allocatedAmount: number | null | undefined,
  allocatedQuantity: number | null | undefined,
): number {
  if (allocatedAmount != null) {
    if (allocatedQuantity != null && allocatedQuantity > 0) {
      return Math.round((allocatedAmount * saleQuantity) / allocatedQuantity)
    }
    // allocatedAmount가 있는데 allocatedQuantity가 0 이하면 0 반환 (fixedPrice fallback 안 함)
    return 0
  }
  if (fixedPrice != null) {
    return fixedPrice * saleQuantity
  }
  return 0
}

/**
 * 실현손익 = 매도금액 − 취득금액 − 세금 및 기타 수수료.
 * 증권사 앱의 실현손익과 같은 개념이며 공모주수수료는 빼지 않는다.
 */
export function realizedProfitFromFees(
  price: number,
  quantity: number,
  fixedPrice: number | null | undefined,
  otherFees: number,
  allocatedAmount?: number | null,
  allocatedQuantity?: number | null,
): number {
  const saleAmount = price * quantity
  const acquisitionAmount = acquisitionAmountForSale(
    quantity,
    fixedPrice,
    allocatedAmount,
    allocatedQuantity,
  )
  return saleAmount - acquisitionAmount - otherFees
}

/** 증권사 앱의 실현손익이 주어졌을 때 거꾸로 구한 세금 및 기타 수수료. */
export function otherFeesFromRealizedProfit(
  price: number,
  quantity: number,
  fixedPrice: number | null | undefined,
  realizedProfit: number,
  allocatedAmount?: number | null,
  allocatedQuantity?: number | null,
): number {
  const saleAmount = price * quantity
  const acquisitionAmount = acquisitionAmountForSale(
    quantity,
    fixedPrice,
    allocatedAmount,
    allocatedQuantity,
  )
  return saleAmount - acquisitionAmount - realizedProfit
}

/** 최종수익금 = 실현손익 − 공모주수수료. */
export function finalProfit(realizedProfit: number, ipoFee: number): number {
  return realizedProfit - ipoFee
}

/** 매도단가 기본값용 공모가: 확정 공모가가 0보다 크면 그 값, 없으면 null. */
export function resolveIpoPrice(fixedPrice: number | null | undefined): number | null {
  if (fixedPrice != null && fixedPrice > 0) return fixedPrice
  return null
}

/**
 * 매도단가 기본값용 배정 단가(주당) 계산.
 * - allocatedAmount와 allocatedQuantity가 있으면 평균 단가를 계산
 * - 없으면 fixedPrice 사용
 * - 없으면 null
 * 참고: 이는 매도단가 기본값(표시용) 용도이며, 취득금액 계산은 acquisitionAmountForSale을 사용한다.
 */
export function allocationUnitPrice(
  fixedPrice: number | null | undefined,
  allocatedAmount?: number | null,
  allocatedQuantity?: number | null,
): number | null {
  if (allocatedAmount != null && allocatedQuantity != null && allocatedQuantity > 0) {
    return Math.round(allocatedAmount / allocatedQuantity)
  }
  return fixedPrice ?? null
}

/**
 * @deprecated acquisitionAmountForSale을 사용하세요. 이 함수는 하위 호환성을 위해 유지됩니다.
 * 주당 취득 단가(원 단위 반올림)를 계산합니다.
 */
export function acquisitionUnitPrice(
  fixedPrice: number | null | undefined,
  allocatedAmount?: number | null,
  allocatedQuantity?: number | null,
): number | null {
  if (allocatedAmount != null && allocatedQuantity != null && allocatedQuantity > 0) {
    return Math.round(allocatedAmount / allocatedQuantity)
  }
  return fixedPrice ?? null
}

/** 상장일('YYYY-MM-DD' 또는 ISO)을 date 입력 값('YYYY-MM-DD')으로 바꾼다. */
export function listingDate(value: string | null | undefined): string | undefined {
  if (!value) return undefined
  return value.slice(0, 10)
}

/** 매도 폼 입력값(숫자 칸은 비어 있으면 NaN). */
export interface SaleFormInput {
  quantity: number
  price: number
  saleDate: string
  ipoFee: number
  otherFees: number
  /** 직접 매도 등록 시 필요한 필드 */
  allocatedQuantity?: number
  securitiesAccountId?: number
  ipoId?: number
  investorId?: number
}

const isNum = (value: unknown): value is number => typeof value === 'number' && !Number.isNaN(value)

/** 폼 입력을 저장 요청으로: price=매도단가, amount=단가×수량, fee=공모주수수료, tax=세금 및 기타, 날짜에 T09:00. */
export function toSalePayload(data: SaleFormInput): Omit<SaleRegisterRequest, 'allocationId'> {
  const payload: Omit<SaleRegisterRequest, 'allocationId'> = {
    quantity: data.quantity,
    price: data.price,
    amount: data.price * data.quantity,
    saleDate: `${data.saleDate}T09:00`,
    fee: isNum(data.ipoFee) ? data.ipoFee : 0,
    tax: isNum(data.otherFees) ? data.otherFees : 0,
  }

  // 직접 매도 등록 시 추가 필드
  if (data.allocatedQuantity != null) {
    payload.allocatedQuantity = data.allocatedQuantity
  }
  if (data.securitiesAccountId != null) {
    payload.securitiesAccountId = data.securitiesAccountId
  }
  if (data.ipoId != null) {
    payload.ipoId = data.ipoId
  }
  if (data.investorId != null) {
    payload.investorId = data.investorId
  }

  return payload
}
