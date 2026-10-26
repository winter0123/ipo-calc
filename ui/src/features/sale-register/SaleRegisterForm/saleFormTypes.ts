import type { CaptureFill } from '@/entities/sale/index.ts'

/** 캡처로 채울 내용. subscriptionId가 없으면 사용자가 청약을 고르는 즉시 채운다. */
export interface CaptureApply {
  subscriptionId?: number
  fill: CaptureFill
  notes: string[]
}

export type CapturedField = 'quantity' | 'price' | 'saleDate' | 'otherFees' | 'realizedProfit'

/** 폼 입력값. 숫자 칸은 비어 있으면 NaN이다(valueAsNumber). */
export interface SaleFormValues {
  allocationId: number
  quantity: number
  price: number
  saleDate: string
  ipoFee: number
  otherFees: number
  realizedProfit: number
}
