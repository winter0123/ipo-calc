import type { CaptureFill } from '@/entities/sale/index.ts'

/** 캡처로 채울 내용. subscriptionId가 있으면 그 청약 연결로 바꾸고, 없으면 현재 연결에 적용한다. */
export interface CaptureApply {
  subscriptionId?: number
  fill: CaptureFill
  notes: string[]
}

export type CapturedField = 'quantity' | 'price' | 'saleDate' | 'otherFees' | 'realizedProfit'

/** 폼 입력값. 숫자 칸은 비어 있으면 NaN이다(valueAsNumber). */
export interface SaleFormValues {
  quantity: number
  price: number
  saleDate: string
  ipoFee: number
  otherFees: number
  realizedProfit: number
  /** 직접 매도 등록(기존 배정이 없을 때) 시 필요한 필드 */
  allocatedQuantity?: number
  securitiesAccountId?: number
  ipoId?: number
  investorId?: number
}
