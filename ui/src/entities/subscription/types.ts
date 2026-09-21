export interface SubscriptionRegisterRequest {
  investorId: number
  securitiesAccountId: number
  ipoId: number
  quantity: number
  depositAmount: number
  subscribedAt: string
  memo?: string
}

export interface Subscription {
  id: number
  investorId: number
  securitiesAccountId: number
  ipoId: number
  quantity: number
  depositAmount: number
  subscribedAt: string
  memo?: string
  status: string
  createdAt: string
  updatedAt: string
}

/**
 * GET /investment-history 응답 DTO — Epic 11(investment-query-api) 미구현.
 * docs/breakdown/investment-query-api.md SF-1 산출물 파일 경로(InvestmentHistoryDto 등)에
 * 정의된 필드 그대로 타입만 선반영한다. 백엔드 구현 시 필드가 달라지면 함께 갱신한다.
 * 주의: 이 DTO에는 증권계좌 정보(securitiesAccountId 등)가 없다 — web-subscription-workflow
 * spec 2절의 "청약 카드에 증권계좌 표시" 요구사항과의 불일치를 발견했다(work-log 기록).
 */
export interface AllocationSummary {
  id: number
  allocatedQuantity: number
  allocationPrice: number | null
  allocationStatus: string
}

export interface SaleSummary {
  id: number
  quantity: number
  amount: number
  fee: number
  tax: number
  otherCost: number
  status: string
  soldAt: string
}

export interface InvestmentHistoryItem {
  id: number
  subscribedAt: string
  ipoId: number
  ipoName: string
  ipoFixedPrice: number
  subscriptionQuantity: number
  subscriptionStatus: string
  allocation: AllocationSummary | null
  sales: SaleSummary[]
  remainingQuantity: number | null
}

export interface InvestmentHistoryFilters {
  investorId?: number
  securitiesAccountId?: number
  ipoId?: number
  page?: number
  size?: number
}

export interface InvestmentHistoryResponse {
  content: InvestmentHistoryItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
