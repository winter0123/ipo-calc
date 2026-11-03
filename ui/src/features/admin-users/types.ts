import { z } from 'zod'

export const adminUserListSchema = z.object({
  id: z.number(),
  email: z.string(),
  name: z.string().nullable(),
  nickname: z.string(),
  role: z.string(),
  approved: z.boolean(),
  locked: z.boolean(),
  lockedUntil: z.string().nullable(),
  lastLoginAt: z.string().nullable(),
  createdAt: z.string(),
  investorCount: z.number(),
  subscriptionCount: z.number(),
})
export type AdminUserList = z.infer<typeof adminUserListSchema>

export const adminUserDetailSchema = z.object({
  id: z.number(),
  email: z.string(),
  name: z.string().nullable(),
  nickname: z.string(),
  role: z.string(),
  approved: z.boolean(),
  locked: z.boolean(),
  lockedUntil: z.string().nullable(),
  lastLoginAt: z.string().nullable(),
  createdAt: z.string(),
  investors: z.array(
    z.object({
      id: z.number(),
      displayName: z.string(),
      relationship: z.string(),
      status: z.string(),
    }),
  ),
  stats: z.object({
    subscriptionCount: z.number(),
    allocationCount: z.number(),
    saleCount: z.number(),
    totalProfit: z.number(),
  }),
})
export type AdminUserDetail = z.infer<typeof adminUserDetailSchema>

export const investorSummarySchema = z.object({
  id: z.number(),
  displayName: z.string(),
  relationship: z.string(),
  status: z.string(),
})
export type InvestorSummary = z.infer<typeof investorSummarySchema>

export const subscriptionSummarySchema = z.object({
  id: z.number(),
  ipoName: z.string(),
  companyName: z.string(),
  quantity: z.number().nullable(),
  depositAmount: z.number().nullable(),
  subscribedAt: z.string().nullable(),
  status: z.string(),
  allocatedQuantity: z.number().nullable(),
  remainingQuantity: z.number().nullable(),
})
export type SubscriptionSummary = z.infer<typeof subscriptionSummarySchema>

export const allocationSummarySchema = z.object({
  id: z.number(),
  ipoName: z.string(),
  allocatedQuantity: z.number().nullable(),
  allocatedAt: z.string().nullable(),
  allocationMethod: z.string().nullable(),
  allocatedAmount: z.number().nullable(),
  status: z.string(),
})
export type AllocationSummary = z.infer<typeof allocationSummarySchema>

export const saleSummarySchema = z.object({
  id: z.number(),
  ipoName: z.string(),
  quantity: z.number().nullable(),
  price: z.number().nullable(),
  amount: z.number().nullable(),
  fee: z.number().nullable(),
  tax: z.number().nullable(),
  otherCost: z.number().nullable(),
  soldAt: z.string().nullable(),
  status: z.string(),
})
export type SaleSummary = z.infer<typeof saleSummarySchema>

export function pagedSchema<T>(itemSchema: z.ZodType<T>) {
  return z.object({
    content: z.array(itemSchema),
    totalPages: z.number(),
    totalElements: z.number(),
    number: z.number(),
    size: z.number(),
  })
}

export interface AdminUserSearchParams {
  page: number
  size: number
  search?: string
  role?: string | null
  approved?: boolean | null
  locked?: boolean | null
  sort?: string | null
}

export const defaultSearchParams: AdminUserSearchParams = {
  page: 0,
  size: 20,
  search: '',
  role: null,
  approved: null,
  locked: null,
  sort: 'createdAt,desc',
}
