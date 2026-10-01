import type { InvestmentHistoryFilters } from './types.ts'

export const investmentHistoryKeys = {
  all: ['investment-history'] as const,
  list: (filters: InvestmentHistoryFilters = {}) =>
    [...investmentHistoryKeys.all, 'list', filters] as const,
  detail: (subscriptionId: number | null | undefined) =>
    [...investmentHistoryKeys.all, 'detail', subscriptionId] as const,
}
