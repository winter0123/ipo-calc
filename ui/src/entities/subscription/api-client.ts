import { apiFetch } from '@/shared/api/client.ts'
import type {
  InvestmentHistoryFilters,
  InvestmentHistoryResponse,
  InvestmentHistoryItem,
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
  return apiFetch<InvestmentHistoryResponse>(`/investment-history${query ? `?${query}` : ''}`, {
    method: 'GET',
    signal,
  })
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
  return apiFetch<InvestmentHistoryItem>(`/investment-history/${subscriptionId}`, {
    method: 'GET',
    signal,
  })
}
