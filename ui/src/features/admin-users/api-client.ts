import { z } from 'zod'
import { apiGetParsed } from '@/shared/api/parsed.ts'
import {
  adminUserDetailSchema,
  adminUserListSchema,
  allocationSummarySchema,
  investorSummarySchema,
  pagedSchema,
  saleSummarySchema,
  subscriptionSummarySchema,
  type AdminUserDetail,
  type AdminUserList,
  type AdminUserSearchParams,
  type AllocationSummary,
  type InvestorSummary,
  type SaleSummary,
  type SubscriptionSummary,
} from './types.ts'

export function fetchAdminUsers(
  params: AdminUserSearchParams,
  signal?: AbortSignal,
): Promise<{ content: AdminUserList[]; totalPages: number; totalElements: number }> {
  const query = new URLSearchParams()
  query.set('page', String(params.page))
  query.set('size', String(params.size))
  if (params.search) query.set('search', params.search)
  if (params.role) query.set('role', params.role)
  if (params.approved !== null && params.approved !== undefined) {
    query.set('approved', String(params.approved))
  }
  if (params.locked !== null && params.locked !== undefined) {
    query.set('locked', String(params.locked))
  }
  if (params.sort) query.set('sort', params.sort)
  return apiGetParsed(`/admin/users?${query}`, pagedSchema(adminUserListSchema), signal)
}

export function fetchAdminUserDetail(
  accountId: number,
  signal?: AbortSignal,
): Promise<AdminUserDetail> {
  return apiGetParsed(`/admin/users/${accountId}`, adminUserDetailSchema, signal)
}

export function fetchAdminUserInvestors(
  accountId: number,
  signal?: AbortSignal,
): Promise<InvestorSummary[]> {
  return apiGetParsed(`/admin/users/${accountId}/investors`, z.array(investorSummarySchema), signal)
}

export function fetchAdminUserSubscriptions(
  accountId: number,
  signal?: AbortSignal,
): Promise<{ content: SubscriptionSummary[]; totalPages: number }> {
  return apiGetParsed(
    `/admin/users/${accountId}/subscriptions`,
    pagedSchema(subscriptionSummarySchema),
    signal,
  )
}

export function fetchAdminUserAllocations(
  accountId: number,
  signal?: AbortSignal,
): Promise<{ content: AllocationSummary[]; totalPages: number }> {
  return apiGetParsed(
    `/admin/users/${accountId}/allocations`,
    pagedSchema(allocationSummarySchema),
    signal,
  )
}

export function fetchAdminUserSales(
  accountId: number,
  signal?: AbortSignal,
): Promise<{ content: SaleSummary[]; totalPages: number }> {
  return apiGetParsed(`/admin/users/${accountId}/sales`, pagedSchema(saleSummarySchema), signal)
}
