import { useQuery } from '@tanstack/react-query'
import { getInvestmentHistory } from './api-client.ts'
import { investmentHistoryKeys } from './queryKeys.ts'
import type { InvestmentHistoryFilters, InvestmentHistoryResponse } from './types.ts'

export function useInvestmentHistory(
  filters: InvestmentHistoryFilters = {},
  { enabled }: { enabled?: boolean } = {},
) {
  return useQuery<InvestmentHistoryResponse>({
    queryKey: investmentHistoryKeys.list(filters),
    queryFn: ({ signal }) => getInvestmentHistory(filters, signal),
    enabled,
  })
}
