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
 * GET /investment-history 응답 DTO — Epic 11(investment-query-api) 구현 완료.
 * 백엔드 InvestmentHistoryDto(PR #180)에 맞춰 유지한다. 투자자/증권사 필드가 늘었으므로
 * 아래 주석이 말하던 "증권계좌 정보가 없다"는 불일치는 해결됐다 — 대신 증권계좌 ID가 아닌
 * 증권사명(securitiesFirmName)으로 표기한다.
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
  /** 투자자명 — 투자자 정보가 없으면 null (백엔드 PR #180) */
  investorName: string | null
  /** 증권사명 — 증권계좌가 없으면 null (백엔드 PR #180) */
  securitiesFirmName: string | null
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
