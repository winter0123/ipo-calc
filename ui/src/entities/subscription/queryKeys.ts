import type { InvestmentHistoryFilters } from './types.ts'

export const investmentHistoryKeys = {
  all: ['investment-history'] as const,
  list: (filters: InvestmentHistoryFilters = {}) =>
    [...investmentHistoryKeys.all, 'list', filters] as const,
  detail: (subscriptionId: number | null | undefined) =>
    [...investmentHistoryKeys.all, 'detail', subscriptionId] as const,
  /** 수정 폼이 미리 채우려고 읽는 원본 기록. 투자 내역 전체 무효화에 같이 갱신된다 */
  source: (kind: 'subscription' | 'allocation' | 'sale', id: number) =>
    [...investmentHistoryKeys.all, 'source', kind, id] as const,
}
