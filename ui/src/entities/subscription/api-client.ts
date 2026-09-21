import { apiFetch } from '../../shared/api/client.ts'
import type {
  InvestmentHistoryFilters,
  InvestmentHistoryResponse,
  Subscription,
  SubscriptionRegisterRequest,
} from './types.ts'

export async function createSubscription(data: SubscriptionRegisterRequest): Promise<Subscription> {
  return apiFetch<Subscription>('/subscriptions', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/**
 * GET /investment-history — Epic 11(investment-query-api) 백엔드 미구현.
 * 필터(investorId/securitiesAccountId/ipoId)는 지정된 것만 쿼리스트링에 포함한다.
 */
export async function getInvestmentHistory(
  filters: InvestmentHistoryFilters = {},
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
  return apiFetch<InvestmentHistoryResponse>(`/investment-history${query ? `?${query}` : ''}`, {
    method: 'GET',
  })
}
