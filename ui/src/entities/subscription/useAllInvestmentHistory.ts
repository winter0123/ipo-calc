import { useQuery } from '@tanstack/react-query'
import { getAllInvestmentHistory } from './api-client.ts'
import { investmentHistoryKeys } from './queryKeys.ts'
import type { InvestmentHistoryFilters, InvestmentHistoryItem } from './types.ts'

export function useAllInvestmentHistory(
  filters: Omit<InvestmentHistoryFilters, 'page' | 'size'> = {},
  { enabled }: { enabled?: boolean } = {},
) {
  return useQuery<InvestmentHistoryItem[]>({
    queryKey: [...investmentHistoryKeys.list(filters), 'all'],
    queryFn: ({ signal }) => getAllInvestmentHistory(filters, signal),
    enabled,
  })
}
