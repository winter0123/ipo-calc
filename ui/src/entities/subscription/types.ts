import { z } from 'zod'

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
 * GET /investment-history 응답 DTO — 백엔드 InvestmentHistoryDto 기준.
 * 매도의 amount/fee/tax/otherCost와 IPO 이름·고정가는 DB에서 nullable이라 null을 허용한다.
 */
export const allocationSummarySchema = z.object({
  id: z.number(),
  allocatedQuantity: z.number(),
  allocationPrice: z.number().nullable(),
  allocationStatus: z.string(),
})

export const saleSummarySchema = z.object({
  id: z.number(),
  quantity: z.number(),
  amount: z.number().nullable(),
  fee: z.number().nullable(),
  tax: z.number().nullable(),
  otherCost: z.number().nullable(),
  status: z.string(),
  soldAt: z.string(),
})

export const investmentHistoryItemSchema = z.object({
  id: z.number(),
  subscribedAt: z.string(),
  ipoId: z.number(),
  ipoName: z.string().nullable(),
  ipoFixedPrice: z.number().nullable(),
  subscriptionQuantity: z.number(),
  subscriptionStatus: z.string(),
  allocation: allocationSummarySchema.nullable(),
  sales: z.array(saleSummarySchema),
  remainingQuantity: z.number().nullable(),
  investorName: z.string().nullable(),
  securitiesFirmName: z.string().nullable(),
  securitiesAccountId: z.number().nullish(),
  securitiesAccountName: z.string().nullish(),
})

export const investmentHistoryResponseSchema = z.object({
  content: z.array(investmentHistoryItemSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(),
  size: z.number(),
})

export type AllocationSummary = z.infer<typeof allocationSummarySchema>
export type SaleSummary = z.infer<typeof saleSummarySchema>
export type InvestmentHistoryItem = z.infer<typeof investmentHistoryItemSchema>
export type InvestmentHistoryResponse = z.infer<typeof investmentHistoryResponseSchema>

export interface InvestmentHistoryFilters {
  investorId?: number
  securitiesAccountId?: number
  ipoId?: number
  page?: number
  size?: number
}

export interface SubscriptionUpdateRequest {
  quantity?: number
  depositAmount?: number
  subscribedAt?: string
  memo?: string
}
