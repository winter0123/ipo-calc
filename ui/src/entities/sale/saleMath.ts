import type { SaleRegisterRequest } from './types.ts'

/** 공모주수수료 기본값(원). 사용자가 고칠 수 있다. */
export const DEFAULT_IPO_FEE = 2000

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

/**
 * 취득 단가: 서버 수익 계산(ProfitCalculator)과 같은 규칙 — 배정단가가 있으면(0 포함) 그 값, 없으면 확정 공모가.
 * 실현손익 미리보기가 저장 후 서버 값과 같아야 한다.
 */
export function acquisitionUnitPrice(
  allocationPrice: number | null | undefined,
  fixedPrice: number | null | undefined,
): number | null {
  if (allocationPrice != null) return allocationPrice
  return fixedPrice ?? null
}

/** 매도단가 기본값용 공모가: 확정 공모가가 있으면 그 값, 없으면 배정단가, 둘 다 없으면 null. */
export function resolveIpoPrice(
  fixedPrice: number | null | undefined,
  allocationPrice: number | null | undefined,
): number | null {
  if (fixedPrice != null && fixedPrice > 0) return fixedPrice
  if (allocationPrice != null && allocationPrice > 0) return allocationPrice
  return null
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
