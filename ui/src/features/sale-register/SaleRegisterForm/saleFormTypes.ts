import type { Control } from 'react-hook-form'
import type { InvestmentHistoryItem } from '@/entities/subscription/index.ts'
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

/**
 * 캡처로 채우기 UI·로직이 폼에서 실제로 쓰는 최소 계약. /sales/register의 useSaleForm이 그대로 제공하며,
 * 매도 관리 섹션(AddSaleForm)도 같은 모양만 제공하면 CaptureImport를 재사용할 수 있다.
 * features 간 직접 의존 없이 이 인터페이스만 통과시킨다.
 */
export interface SaleFormCaptureAdapter {
  control: Control<SaleFormValues>
  subscriptions: InvestmentHistoryItem[]
  subscriptionsLoading: boolean
  effectiveIpoId?: number
  ipoName?: string | null
  ipoCompanyName?: string | null
  ipoStockCode?: string | null
  securitiesAccountId?: number
  connectionId?: number
  isPending: boolean
  capturePending: boolean
  captureNotes: string[]
  captured: Partial<Record<CapturedField, number | string>>
  noteCapture(notes: string[]): void
  applyCapture(apply: CaptureApply): void
}
