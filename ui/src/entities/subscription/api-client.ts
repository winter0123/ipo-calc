import { apiFetch } from '@/shared/api/client.ts'
import { apiFetchParsed, apiGetParsed } from '@/shared/api/parsed.ts'
import {
  investmentHistoryItemSchema,
  investmentHistoryResponseSchema,
  subscriptionSchema,
} from './types.ts'
import type {
  InvestmentHistoryFilters,
  InvestmentHistoryResponse,
  InvestmentHistoryItem,
  Subscription,
  SubscriptionRegisterRequest,
  SubscriptionUpdateRequest,
} from './types.ts'

export async function createSubscription(data: SubscriptionRegisterRequest): Promise<Subscription> {
  return apiFetchParsed('/subscriptions', subscriptionSchema, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/**
 * GET /investment-history — Epic 11(investment-query-api) 구현 완료.
 * 필터(investorId/securitiesAccountId/ipoId)는 지정된 것만 쿼리스트링에 포함한다.
 */
export async function getInvestmentHistory(
  filters: InvestmentHistoryFilters = {},
  signal?: AbortSignal,
): Promise<InvestmentHistoryResponse> {
  const params = new URLSearchParams()
  if (filters.investorId !== undefined) params.set('investorId', String(filters.investorId))
  if (filters.securitiesAccountId !== undefined) {
    params.set('securitiesAccountId', String(filters.securitiesAccountId))
  }
  if (filters.ipoId !== undefined) params.set('ipoId', String(filters.ipoId))
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))

  const query = params.toString()
  return apiGetParsed(
    `/investment-history${query ? `?${query}` : ''}`,
    investmentHistoryResponseSchema,
    signal,
  )
}

/** 서버 size 상한(100)으로 마지막 페이지까지 이어 읽어 전체 목록을 돌려준다. */
export async function getAllInvestmentHistory(
  filters: Omit<InvestmentHistoryFilters, 'page' | 'size'> = {},
  signal?: AbortSignal,
): Promise<InvestmentHistoryItem[]> {
  const items: InvestmentHistoryItem[] = []
  for (let page = 0; ; page++) {
    const res = await getInvestmentHistory({ ...filters, page, size: 100 }, signal)
    items.push(...res.content)
    if (page + 1 >= res.totalPages) return items
  }
}

/**
 * GET /investment-history/{subscriptionId} — Epic 11(investment-query-api) 구현 완료.
 * 배정 등록 화면 진입 시 청약 상세 조회용으로 사용된다(참고 표시용 청약수량 추출).
 * 응답에서 subscriptionQuantity(청약수량) 필드를 추출한다.
 */
export async function getSubscriptionDetail(
  subscriptionId: number,
  signal?: AbortSignal,
): Promise<InvestmentHistoryItem> {
  return apiGetParsed(`/investment-history/${subscriptionId}`, investmentHistoryItemSchema, signal)
}

export async function getSubscription(id: number, signal?: AbortSignal): Promise<Subscription> {
  return apiGetParsed(`/subscriptions/${id}`, subscriptionSchema, signal)
}

export async function updateSubscription(
  id: number,
  data: SubscriptionUpdateRequest,
): Promise<Subscription> {
  return apiFetchParsed(`/subscriptions/${id}`, subscriptionSchema, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function deleteSubscription(id: number): Promise<void> {
  return apiFetch<void>(`/subscriptions/${id}`, { method: 'DELETE' })
}
